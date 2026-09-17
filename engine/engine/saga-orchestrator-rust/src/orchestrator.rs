use crate::{config::OrchestratorConfig, state_store::{LastEvent, SagaState, StateCache}};
use anyhow::Result;
use rdkafka::{
    config::ClientConfig,
    consumer::{CommitMode, Consumer, StreamConsumer},
    message::Message,
    producer::{FutureProducer, FutureRecord},
};
use serde_json::Value;
use std::time::{SystemTime, UNIX_EPOCH, Duration};
use tracing::{info, warn};

pub struct Orchestrator {
    cfg: OrchestratorConfig,
    consumer: StreamConsumer,
    producer: FutureProducer,
    pub state: StateCache,
}

impl Orchestrator {
    pub fn new(cfg: OrchestratorConfig) -> Result<Self> {
        // NOTA: auto-commit é sempre desabilitado, independente de kafka.enable.auto.commit
        // no properties. O offset só avança depois que handle_message() retorna Ok em run()
        // (veja o comentário lá) -- isso é o que garante semântica at-least-once: uma falha
        // real de publish (Kafka fora do ar, etc.) não comita, então a mensagem é reentregue.
        // O campo cfg.kafka.enable_auto_commit fica só como registro do que veio do arquivo,
        // mas não é mais usado para configurar o client.
        if cfg.kafka.enable_auto_commit {
            warn!("kafka.enable.auto.commit=true no properties é ignorado: o orchestrator sempre comita manualmente após processar a mensagem com sucesso");
        }

        let consumer: StreamConsumer = ClientConfig::new()
            .set("bootstrap.servers", &cfg.kafka.bootstrap_servers)
            .set("group.id", &cfg.kafka.group_id)
            .set("auto.offset.reset", &cfg.kafka.auto_offset_reset)
            .set("enable.auto.commit", "false")
            .create()?;

        let producer: FutureProducer = ClientConfig::new()
            .set("bootstrap.servers", &cfg.kafka.bootstrap_servers)
            // Evita duplicar comandos/estado quando o rdkafka faz retry interno de um send
            // (ex.: timeout de broker). Não resolve duplicação por reprocessamento de
            // mensagem após redelivery -- para isso, o consumidor do comando precisa ser
            // idempotente por conta própria (checar correlationId, por exemplo).
            .set("enable.idempotence", "true")
            .create()?;

        Ok(Self { cfg, consumer, producer, state: StateCache::new() })
    }

    /// Reconstrói o cache in-memory a partir do tópico compactado de estado ANTES de
    /// começar a consumir eventos de negócio. Sem isso, um restart do processo perde
    /// o `version` e o histórico de toda saga em andamento (bug original).
    ///
    /// Estratégia: usa um group.id efêmero (não interfere no grupo de consumo real),
    /// lê do início do tópico, e considera a reidratação concluída depois de um período
    /// de "silêncio" sem novas mensagens (`idle_timeout`). É uma heurística adequada para
    /// um tópico de estado pequeno/lab; para um volume alto de sagas, o próximo passo é
    /// trocar por comparação exata contra o high watermark de cada partição.
    pub async fn rehydrate_state(&self) -> Result<()> {
        let topic = self.cfg.kafka.state_topic.clone();
        let rehydrate_group = format!("{}-rehydrate-{}", self.cfg.kafka.group_id, now_ms());

        let consumer: StreamConsumer = ClientConfig::new()
            .set("bootstrap.servers", &self.cfg.kafka.bootstrap_servers)
            .set("group.id", &rehydrate_group)
            .set("auto.offset.reset", "earliest")
            .set("enable.auto.commit", "false")
            .create()?;

        consumer.subscribe(&[topic.as_str()])?;
        info!(%topic, "rehydrating saga state from compacted topic");

        let idle_timeout = Duration::from_secs(3);
        let mut count = 0u64;

        loop {
            match tokio::time::timeout(idle_timeout, consumer.recv()).await {
                Ok(Ok(msg)) => {
                    let key = msg.key_view::<str>().and_then(|r| r.ok()).map(|s| s.to_string());
                    match msg.payload_view::<str>() {
                        Some(Ok(payload)) if !payload.is_empty() => {
                            match serde_json::from_str::<SagaState>(payload) {
                                Ok(s) => { self.state.upsert(s).await; count += 1; }
                                Err(e) => warn!(error=%e, "failed to parse state record during rehydration"),
                            }
                        }
                        _ => {
                            // tombstone (valor nulo/vazio): saga foi removida do state store
                            if let Some(k) = key {
                                self.state.remove_by_key(&k).await;
                            }
                        }
                    }
                }
                Ok(Err(e)) => warn!(error=%e, "rehydration read error"),
                Err(_) => {
                    // sem novas mensagens por `idle_timeout`: consideramos a reidratação concluída
                    break;
                }
            }
        }

        info!(records=count, sagas_loaded=self.state.len().await, "state rehydration complete");
        Ok(())
    }

    pub fn subscribe(&self) -> Result<()> {
        let topics: Vec<&str> = self.cfg.event_in.iter().map(|e| e.topic.as_str()).collect();
        self.consumer.subscribe(&topics)?;
        Ok(())
    }

    pub async fn run(mut self) -> Result<()> {
        info!("Orchestrator started");
        loop {
            match self.consumer.recv().await {
                Ok(msg) => {
                    match self.handle_message(&msg).await {
                        Ok(()) => {
                            // Só avança o offset depois que a mensagem foi de fato tratada
                            // (roteada com sucesso, mandada pra DLQ, ou ignorada por política).
                            if let Err(e) = self.consumer.commit_message(&msg, CommitMode::Async) {
                                warn!(error=%e, "failed to commit offset");
                            }
                        }
                        Err(e) => {
                            // Falha real de infraestrutura (ex.: publish no Kafka falhou).
                            // NÃO comitamos -- a mensagem será reentregue no próximo poll
                            // (ou após restart), em vez de ser perdida silenciosamente.
                            warn!(error=%e, "message handling failed, offset not committed; message will be redelivered");
                        }
                    }
                }
                Err(e) => warn!("Kafka recv error: {}", e),
            }
        }
    }

    async fn handle_message(&mut self, msg: &rdkafka::message::BorrowedMessage<'_>) -> Result<()> {
        let topic = msg.topic().to_string();
        let payload = msg.payload_view::<str>().unwrap_or(Ok(""))?;

        // Parse JSON
        let json: Value = match serde_json::from_str(payload) {
            Ok(v) => v,
            Err(e) => {
                self.on_error("parse", &topic, payload, &e.to_string()).await?;
                return Ok(());
            }
        };

        // Determine event type. Antes isso virava um anyhow::Error genérico que pulava
        // a política de erro configurada (error.on.missing.event.type nunca era consultada).
        // Agora roteia pelo mesmo caminho de DLQ que os outros erros de dados.
        let event_type = match self.extract_event_type(&topic, &json) {
            Some(et) => et,
            None => {
                self.on_error("missing_event_type", &topic, payload, "could not determine event type").await?;
                return Ok(());
            }
        };

        // Determine correlation id -- mesmo raciocínio: antes abortava a mensagem inteira
        // sem nunca de fato aplicar error.on.missing.correlation.
        let saga_id = match self.extract_correlation(&json) {
            Some(id) => id,
            None => {
                self.on_error("missing_correlation", &topic, payload, "could not determine correlation id").await?;
                return Ok(());
            }
        };
        let saga_type = "DefaultSaga".to_string(); // propositalmente genérico

        info!(%event_type, %saga_id, %topic, partition=msg.partition(), offset=msg.offset(), "event received");

        // Emit commands defined by config (v1 routing).
        // Antes, o `?` dentro do loop abortava no primeiro comando que falhasse e os
        // seguintes nunca eram tentados (ex.: ReservePayment falha -> ConfirmInventory
        // nunca é publicado, silenciosamente). Agora tentamos todos e só depois decidimos
        // se a mensagem inteira falhou.
        let cmds_for_route = self.cfg.routes.get(&event_type).cloned();
        if let Some(cmds) = &cmds_for_route {
            let mut failed_cmds = Vec::new();
            for cmd in cmds {
                if let Err(e) = self.emit_command(cmd, &saga_id, &json).await {
                    warn!(%cmd, %saga_id, error=%e, "failed to publish command, continuing with remaining routes");
                    failed_cmds.push(cmd.clone());
                }
            }
            if !failed_cmds.is_empty() {
                // Falha de infraestrutura (não é erro de dados): não atualizamos/publicamos
                // o estado, e propagamos o erro para que run() NÃO comite o offset --
                // a mensagem será reprocessada e os comandos bem-sucedidos podem duplicar
                // (mitigado por enable.idempotence no producer; o consumidor do comando
                // também deve ser idempotente por correlationId).
                return Err(anyhow::anyhow!("failed to publish commands: {:?}", failed_cmds));
            }
        } else {
            match self.cfg.error_policy.on_unknown_event.as_str() {
                "IGNORE" => {}
                _ => warn!(%event_type, "unknown event type (no route)")
            }
        }

        // Deriva o status da saga a partir dos comandos efetivamente roteados para este
        // evento, em vez de fixar sempre "InProgress" (bug original: o campo status nunca
        // refletia Completed/Failed/Compensating).
        let status = derive_status(cmds_for_route.as_deref());

        // Update and publish state
        let now_ms = now_ms();
        let prev = self.state.get(&saga_type, &saga_id).await;
        let next_version = prev.map(|s| s.version + 1).unwrap_or(1);

        let state = SagaState {
            saga_type: saga_type.clone(),
            saga_id: saga_id.clone(),
            status,
            current: event_type.clone(),
            version: next_version,
            updated_at_ms: now_ms,
            last_event: LastEvent {
                event_type: event_type.clone(),
                topic: topic.clone(),
                partition: msg.partition(),
                offset: msg.offset(),
            },
            meta: serde_json::json!({"lastPayloadType": event_type}),
        };

        self.state.upsert(state.clone()).await;
        self.publish_state(&state).await?;

        Ok(())
    }

    fn extract_event_type(&self, topic: &str, json: &Value) -> Option<String> {
        match self.cfg.conventions.event_type_source.as_str() {
            "topic" => Some(topic.to_string()),
            _ => extract_dot_path(json, &self.cfg.conventions.event_type_path).and_then(|v| v.as_str().map(|s| s.to_string())),
        }
    }

    fn extract_correlation(&self, json: &Value) -> Option<String> {
        match self.cfg.conventions.correlation_source.as_str() {
            "payload" => extract_dot_path(json, &self.cfg.conventions.correlation_path).and_then(|v| v.as_str().map(|s| s.to_string())),
            _ => None,
        }
    }

    async fn emit_command(&self, cmd: &str, saga_id: &str, original: &Value) -> Result<()> {
        let topic = self.cfg.command_out.get(cmd)
            .ok_or_else(|| anyhow::anyhow!("command not configured: {cmd}"))?
            .to_string();

        // command envelope genérico
        let payload = serde_json::json!({
            "type": cmd,
            "correlationId": saga_id,
            "ts": now_ms(),
            "data": original
        }).to_string();

        let record = FutureRecord::to(&topic)
            .key(saga_id)
            .payload(&payload);

        let _ = self.producer.send(record, Duration::from_secs(5)).await
            .map_err(|(e, _)| anyhow::anyhow!("publish failed: {e}"))?;

        info!(%cmd, %topic, %saga_id, "command published");
        Ok(())
    }

    async fn publish_state(&self, state: &SagaState) -> Result<()> {
        let topic = &self.cfg.kafka.state_topic;
        let key = state.key();
        let value = serde_json::to_string(state)?;

        let record = FutureRecord::to(topic)
            .key(&key)
            .payload(&value);

        let _ = self.producer.send(record, Duration::from_secs(5)).await
            .map_err(|(e, _)| anyhow::anyhow!("state publish failed: {e}"))?;

        info!(%key, "state upserted");
        Ok(())
    }

    async fn on_error(&self, kind: &str, topic: &str, raw: &str, detail: &str) -> Result<()> {
        let policy = match kind {
            "parse" => self.cfg.error_policy.on_parse.as_str(),
            "missing_correlation" => self.cfg.error_policy.on_missing_correlation.as_str(),
            "missing_event_type" => self.cfg.error_policy.on_missing_event_type.as_str(),
            _ => "DLQ",
        };

        match policy {
            "DLQ" => {
                let payload = serde_json::json!({
                    "error": kind,
                    "detail": detail,
                    "topic": topic,
                    "raw": raw,
                    "ts": now_ms()
                }).to_string();
                let record = FutureRecord::to(&self.cfg.kafka.dlq_topic).payload(&payload);
                let _ = self.producer.send(record, Duration::from_secs(5)).await;
                warn!(%kind, %topic, "sent to DLQ");
            }
            _ => {
                warn!(%kind, %topic, %policy, "error policy applied (no action)");
            }
        }

        Ok(())
    }
}

/// Heurística simples para derivar o status da saga a partir dos comandos que a rota
/// deste evento emite. É config-driven (não hardcoda nomes de saga específicos): olha
/// para convenções de nome de comando. Se nenhuma rota bater, mantém "InProgress".
fn derive_status(cmds: Option<&[String]>) -> String {
    let Some(cmds) = cmds else { return "InProgress".into() };

    if cmds.iter().any(|c| c.eq_ignore_ascii_case("SagaCompleted")) {
        "Completed".into()
    } else if cmds.iter().any(|c| c.eq_ignore_ascii_case("SagaFailed")) {
        "Failed".into()
    } else if cmds.iter().any(|c| {
        let lc = c.to_lowercase();
        lc.contains("rollback") || lc.contains("compensate")
    }) {
        "Compensating".into()
    } else {
        "InProgress".into()
    }
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64
}

/// Suporte mínimo a "$.a.b.c" (não é JSONPath completo). Se quiser, dá pra trocar por lib depois.
fn extract_dot_path<'a>(json: &'a Value, path: &str) -> Option<&'a Value> {
    let p = path.trim();
    let p = p.strip_prefix("$.").unwrap_or(p);
    if p.is_empty() { return Some(json); }
    let mut cur = json;
    for part in p.split('.') {
        cur = cur.get(part)?;
    }
    Some(cur)
}
