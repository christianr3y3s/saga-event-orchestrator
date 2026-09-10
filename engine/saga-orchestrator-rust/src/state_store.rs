use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::{collections::HashMap, sync::Arc};
use tokio::sync::RwLock;

/// Chave do state store: "<saga_type>|<saga_id>".
/// Valor: JSON com o estado mais recente.
///
/// A ideia é persistir isso em um tópico **compactado** para manter o último valor por chave.
/// (tombstone = value null para apagar) 
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SagaState {
    pub saga_type: String,
    pub saga_id: String,
    pub status: String,          // Started|InProgress|Completed|Failed|Compensating|Compensated
    pub current: String,         // estado atual (ex.: OrderCreated)
    pub version: u64,            // monotônico por saga
    pub updated_at_ms: u64,

    /// Último evento processado (apenas metadados)
    pub last_event: LastEvent,

    /// Campo livre para carregar dados mínimos de coordenação (não é domínio!)
    pub meta: Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LastEvent {
    pub event_type: String,
    pub topic: String,
    pub partition: i32,
    pub offset: i64,
}

impl SagaState {
    pub fn key(&self) -> String {
        format!("{}|{}", self.saga_type, self.saga_id)
    }
}

/// Cache in-memory reconstruído do tópico compactado na inicialização.
#[derive(Clone, Default)]
pub struct StateCache {
    inner: Arc<RwLock<HashMap<String, SagaState>>>,
}

impl StateCache {
    pub fn new() -> Self { Self::default() }

    pub async fn upsert(&self, state: SagaState) {
        self.inner.write().await.insert(state.key(), state);
    }

    pub async fn get(&self, saga_type: &str, saga_id: &str) -> Option<SagaState> {
        self.inner.read().await.get(&format!("{}|{}", saga_type, saga_id)).cloned()
    }

    /// Remove uma entrada pela chave composta ("<saga_type>|<saga_id>").
    /// Usado ao reidratar um tombstone (valor nulo/vazio) do tópico compactado.
    pub async fn remove_by_key(&self, key: &str) {
        self.inner.write().await.remove(key);
    }

    /// Quantidade de sagas atualmente rastreadas (usado só para logging/diagnóstico).
    pub async fn len(&self) -> usize {
        self.inner.read().await.len()
    }
}
