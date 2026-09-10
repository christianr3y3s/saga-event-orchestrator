mod config;
mod fsm;
mod state_store;
mod orchestrator;

use crate::config::{load_config, load_properties};
use crate::orchestrator::Orchestrator;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter("info")
        .init();

    let config_path = std::env::var("ORCH_CONFIG").unwrap_or_else(|_| "orchestrator.properties.example".into());

    // Valida qualquer FSM declarativa (saga.<Tipo>.*) ANTES de conectar no Kafka.
    // Fail-fast: uma saga com trap loop ou dead end não sobe, em vez de ficar presa em
    // produção sem ninguém perceber até uma saga real cair nesse estado.
    let raw_props = load_properties(&config_path)?;
    fsm::validate_all(&raw_props)?;

    let cfg = load_config(&config_path)?;

    let orch = Orchestrator::new(cfg)?;
    // Reconstrói o cache de estado a partir do tópico compactado ANTES de começar a
    // consumir eventos de negócio -- sem isso, um restart perde o version/histórico
    // de toda saga em andamento.
    orch.rehydrate_state().await?;
    orch.subscribe()?;
    orch.run().await?;

    Ok(())
}

