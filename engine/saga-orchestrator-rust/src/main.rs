mod config;
mod fsm;
mod state_store;
mod orchestrator;

use crate::config::{load_config, load_properties};
use crate::orchestrator::Orchestrator;
use tracing::Instrument;
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // ANTES: `.with_env_filter("info")` recebia uma string LITERAL -- apesar do nome do
    // método, isso NÃO lê a variável de ambiente RUST_LOG (só faz isso quando construído
    // via EnvFilter::try_from_default_env()/from_env()). Resultado: o nível de log ficava
    // hardcoded em "info" pra sempre, sem jeito de baixar pra "debug" em produção sem
    // recompilar. Corrigido abaixo -- RUST_LOG=debug ./saga-orchestrator agora funciona,
    // com "info" como fallback se a env var não estiver setada.
    let env_filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info"));

    // Saída em JSON (não texto solto): os campos que os macros de tracing já capturam
    // (saga_id, event_type, topic, cmd, ...) ficam preservados como campos estruturados
    // em vez de virarem parte de uma linha de texto que um coletor externo precisaria
    // fazer regex pra recuperar. Isso é o que torna a exportação pra Loki/Datadog/etc.
    // direta -- eles já leem JSON de stdout nativamente.
    tracing_subscriber::fmt()
        .json()
        // Sem isso, o texto da mensagem do log() fica aninhado em `fields.message` no
        // JSON de saída -- com flatten_event(true), vira `message` na raiz, o que deixa
        // queries no Grafana/LogQL mais diretas (`| json | message =~ ".*falhou.*"` em
        // vez de `fields.message`).
        .flatten_event(true)
        .with_env_filter(env_filter)
        .init();

    let config_path = std::env::var("ORCH_CONFIG").unwrap_or_else(|_| "orchestrator.properties.example".into());

    // Valida qualquer FSM declarativa (saga.<Tipo>.*) ANTES de conectar no Kafka.
    // Fail-fast: uma saga com trap loop ou dead end não sobe, em vez de ficar presa em
    // produção sem ninguém perceber até uma saga real cair nesse estado.
    let raw_props = load_properties(&config_path)?;
    fsm::validate_all(&raw_props)?;

    let cfg = load_config(&config_path)?;

    // Span de topo cobrindo todo o ciclo de vida do processo: todo log emitido dentro
    // dele (inclusive por chamadas aninhadas em orchestrator.rs) carrega os campos
    // `service`/`domain` no JSON de saída. É isso que permite filtrar "só logs do
    // domínio loja" num backend de log externo quando várias instâncias deste mesmo
    // binário rodam simultaneamente (uma por domínio, via scripts/run-domain.sh).
    let root_span = tracing::info_span!("orchestrator", service = "saga-orchestrator", domain = %cfg.domain_name);

    async move {
        let orch = Orchestrator::new(cfg)?;
        // Reconstrói o cache de estado a partir do tópico compactado ANTES de começar a
        // consumir eventos de negócio -- sem isso, um restart perde o version/histórico
        // de toda saga em andamento.
        orch.rehydrate_state().await?;
        orch.subscribe()?;
        orch.run().await?;
        Ok::<(), anyhow::Error>(())
    }
    .instrument(root_span)
    .await
}

