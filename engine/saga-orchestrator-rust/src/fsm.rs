//! Validação estática de FSMs declarativas de saga, definidas no mesmo arquivo
//! `orchestrator.properties` (sem exigir um arquivo separado). Roda no bootstrap, antes
//! de qualquer conexão com o Kafka: se a FSM declarada tem um "trap loop" (ciclo sem
//! saída para nenhum end state) ou um "dead end" (estado sem transição de saída que não
//! é end state), o processo recusa a subir. Fail-fast em vez de descobrir isso em
//! produção com uma saga presa para sempre.
//!
//! Formato esperado nas properties, por tipo de saga:
//!   saga.<Tipo>.start=<ESTADO>
//!   saga.<Tipo>.end=<ESTADO>[,<ESTADO>...]
//!   saga.<Tipo>.states=<ESTADO>[,<ESTADO>...]
//!   saga.<Tipo>.state.<ESTADO>.on.<Evento>=<PROXIMO_ESTADO>
//!
//! Os tipos de saga são descobertos automaticamente (qualquer `saga.<Tipo>.start`
//! presente nas properties é validado) -- não precisa de mais nenhuma chave de config.

use std::collections::{HashMap, HashSet, VecDeque};

#[derive(Debug, Clone)]
pub struct Transition {
    pub event: String,
    pub to: String,
}

#[derive(Debug, Clone)]
pub struct FsmDefinition {
    pub saga_type: String,
    pub start: String,
    pub end_states: HashSet<String>,
    pub states: HashSet<String>,
    pub transitions: HashMap<String, Vec<Transition>>, // from_state -> [(event, to_state)]
}

#[derive(Debug)]
pub enum FsmError {
    UndeclaredStart(String),
    UndeclaredEndState(String),
    UndeclaredTransitionTarget { from: String, event: String, to: String },
    UnreachableFromStart(Vec<String>),
    DeadEnd(String),
    TrapLoop(Vec<String>),
}

impl std::fmt::Display for FsmError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            FsmError::UndeclaredStart(s) =>
                write!(f, "start state '{s}' não está na lista de states declarada"),
            FsmError::UndeclaredEndState(s) =>
                write!(f, "end state '{s}' não está na lista de states declarada"),
            FsmError::UndeclaredTransitionTarget { from, event, to } =>
                write!(f, "transição {from} --{event}--> {to}: estado de destino '{to}' não foi declarado"),
            FsmError::UnreachableFromStart(states) =>
                write!(f, "states declarados mas nunca alcançáveis a partir do start: {states:?}"),
            FsmError::DeadEnd(s) =>
                write!(f, "FSM ERROR: dead end (sem transição de saída e não é end state): {s}"),
            FsmError::TrapLoop(cycle) =>
                write!(f, "FSM ERROR: trap loop detectado (ciclo sem saída para nenhum end state): {cycle:?}"),
        }
    }
}

/// Descobre todos os tipos de saga declarados nas properties (qualquer `saga.<Tipo>.start`).
pub fn discover_saga_types(props: &HashMap<String, String>) -> Vec<String> {
    let mut types: Vec<String> = props.keys()
        .filter_map(|k| k.strip_prefix("saga.")?.strip_suffix(".start"))
        .map(|s| s.to_string())
        .collect();
    types.sort();
    types.dedup();
    types
}

pub fn parse_fsm(props: &HashMap<String, String>, saga_type: &str) -> anyhow::Result<FsmDefinition> {
    let prefix = format!("saga.{saga_type}.");

    let start = props.get(&format!("{prefix}start"))
        .cloned()
        .ok_or_else(|| anyhow::anyhow!("missing {prefix}start"))?;

    let end_states: HashSet<String> = props.get(&format!("{prefix}end"))
        .map(|v| v.split(',').map(|s| s.trim().to_string()).filter(|s| !s.is_empty()).collect())
        .ok_or_else(|| anyhow::anyhow!("missing {prefix}end"))?;

    let states: HashSet<String> = props.get(&format!("{prefix}states"))
        .map(|v| v.split(',').map(|s| s.trim().to_string()).filter(|s| !s.is_empty()).collect())
        .ok_or_else(|| anyhow::anyhow!("missing {prefix}states"))?;

    let mut transitions: HashMap<String, Vec<Transition>> = HashMap::new();
    let state_prefix = format!("{prefix}state.");
    for (key, value) in props {
        let Some(rest) = key.strip_prefix(&state_prefix) else { continue };
        // rest = "<ESTADO>.on.<Evento>"
        let Some((from_state, event)) = rest.split_once(".on.") else { continue };
        transitions.entry(from_state.to_string())
            .or_default()
            .push(Transition { event: event.to_string(), to: value.trim().to_string() });
    }

    Ok(FsmDefinition { saga_type: saga_type.to_string(), start, end_states, states, transitions })
}

/// Valida a FSM: estrutura declarada + detecção de dead-ends e trap loops. Retorna TODOS
/// os erros encontrados (não para no primeiro), para o operador ver o quadro completo.
pub fn validate(fsm: &FsmDefinition) -> Vec<FsmError> {
    let mut errors = Vec::new();

    if !fsm.states.contains(&fsm.start) {
        errors.push(FsmError::UndeclaredStart(fsm.start.clone()));
    }
    for end in &fsm.end_states {
        if !fsm.states.contains(end) {
            errors.push(FsmError::UndeclaredEndState(end.clone()));
        }
    }
    for (from, ts) in &fsm.transitions {
        for t in ts {
            if !fsm.states.contains(&t.to) {
                errors.push(FsmError::UndeclaredTransitionTarget {
                    from: from.clone(), event: t.event.clone(), to: t.to.clone(),
                });
            }
        }
    }

    // Alcançabilidade a partir do start (BFS direto). Estado declarado mas nunca
    // alcançado geralmente é config morta ou uma transição faltando.
    let mut reachable = HashSet::new();
    let mut queue = VecDeque::new();
    reachable.insert(fsm.start.clone());
    queue.push_back(fsm.start.clone());
    while let Some(s) = queue.pop_front() {
        if let Some(ts) = fsm.transitions.get(&s) {
            for t in ts {
                if reachable.insert(t.to.clone()) {
                    queue.push_back(t.to.clone());
                }
            }
        }
    }
    let unreachable: Vec<String> = fsm.states.iter()
        .filter(|s| !reachable.contains(*s))
        .cloned()
        .collect();
    if !unreachable.is_empty() {
        errors.push(FsmError::UnreachableFromStart(unreachable));
    }

    // Consegue-alcançar-um-end (BFS reverso a partir dos end states). Qualquer state que
    // não seja end e não consiga chegar a nenhum end é ou um dead end (sem saída) ou um
    // trap loop (tem saída, mas nenhuma leva a lugar nenhum -- é o "soft-lock" do README).
    let mut reverse: HashMap<String, Vec<String>> = HashMap::new();
    for (from, ts) in &fsm.transitions {
        for t in ts {
            reverse.entry(t.to.clone()).or_default().push(from.clone());
        }
    }
    let mut can_reach_end = HashSet::new();
    let mut q2 = VecDeque::new();
    for e in &fsm.end_states {
        if can_reach_end.insert(e.clone()) {
            q2.push_back(e.clone());
        }
    }
    while let Some(s) = q2.pop_front() {
        if let Some(preds) = reverse.get(&s) {
            for p in preds {
                if can_reach_end.insert(p.clone()) {
                    q2.push_back(p.clone());
                }
            }
        }
    }

    for state in &fsm.states {
        if fsm.end_states.contains(state) { continue; }
        if can_reach_end.contains(state) { continue; }

        let out_degree = fsm.transitions.get(state).map(|v| v.len()).unwrap_or(0);
        if out_degree == 0 {
            errors.push(FsmError::DeadEnd(state.clone()));
        } else {
            let cycle = find_cycle_from(fsm, state).unwrap_or_else(|| vec![state.clone()]);
            errors.push(FsmError::TrapLoop(cycle));
        }
    }

    errors
}

/// Segue a primeira transição disponível a partir de `start` até repetir um estado, só
/// pra montar um caminho legível pro operador entender qual é o loop. Não precisa achar
/// o menor ciclo -- só precisa ser diagnóstico o suficiente.
fn find_cycle_from(fsm: &FsmDefinition, start: &str) -> Option<Vec<String>> {
    let mut path = vec![start.to_string()];
    let mut visited = HashSet::new();
    visited.insert(start.to_string());
    let mut current = start.to_string();

    loop {
        let next = fsm.transitions.get(&current)?.first()?.to.clone();
        path.push(next.clone());
        if next == start || !visited.insert(next.clone()) {
            return Some(path);
        }
        current = next;
    }
}

/// Ponto de entrada chamado no bootstrap: descobre e valida todas as sagas declaradas.
/// Retorna Err com todos os problemas concatenados se qualquer saga for inválida.
pub fn validate_all(props: &HashMap<String, String>) -> anyhow::Result<()> {
    let saga_types = discover_saga_types(props);
    if saga_types.is_empty() {
        // Nenhuma FSM declarativa presente -- properties usando só o roteamento flat
        // (route.<Evento>.emit=<Comando>) continuam funcionando normalmente, sem FSM.
        return Ok(());
    }

    let mut all_errors: Vec<String> = Vec::new();
    for saga_type in &saga_types {
        let fsm = parse_fsm(props, saga_type)?;
        let errors = validate(&fsm);
        for e in errors {
            all_errors.push(format!("[{saga_type}] {e}"));
        }
    }

    if !all_errors.is_empty() {
        anyhow::bail!("FSM inválida, recusando iniciar:\n  {}", all_errors.join("\n  "));
    }

    tracing::info!(sagas = ?saga_types, "FSM validation passed");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn props(pairs: &[(&str, &str)]) -> HashMap<String, String> {
        pairs.iter().map(|(k, v)| (k.to_string(), v.to_string())).collect()
    }

    #[test]
    fn valid_saga_passes() {
        let p = props(&[
            ("saga.OrderSaga.start", "START"),
            ("saga.OrderSaga.end", "COMPLETED"),
            ("saga.OrderSaga.states", "START,COMPLETED"),
            ("saga.OrderSaga.state.START.on.OrderCreated", "COMPLETED"),
        ]);
        let fsm = parse_fsm(&p, "OrderSaga").unwrap();
        assert!(validate(&fsm).is_empty());
    }

    #[test]
    fn trap_loop_is_detected() {
        let p = props(&[
            ("saga.OrderSaga.start", "WAIT_PAYMENT"),
            ("saga.OrderSaga.end", "FAILED"),
            ("saga.OrderSaga.states", "WAIT_PAYMENT,WAIT_INVENTORY,FAILED"),
            ("saga.OrderSaga.state.WAIT_PAYMENT.on.PaymentReserved", "WAIT_INVENTORY"),
            ("saga.OrderSaga.state.WAIT_INVENTORY.on.InventoryTimeout", "WAIT_PAYMENT"),
        ]);
        let fsm = parse_fsm(&p, "OrderSaga").unwrap();
        let errors = validate(&fsm);
        assert!(errors.iter().any(|e| matches!(e, FsmError::TrapLoop(_))),
            "expected a TrapLoop error, got: {errors:?}");
    }

    #[test]
    fn open_cycle_with_exit_passes() {
        let p = props(&[
            ("saga.OrderSaga.start", "RETRYING"),
            ("saga.OrderSaga.end", "FAILED"),
            ("saga.OrderSaga.states", "RETRYING,WAIT_PAYMENT,FAILED"),
            ("saga.OrderSaga.state.RETRYING.on.TryAgain", "WAIT_PAYMENT"),
            ("saga.OrderSaga.state.WAIT_PAYMENT.on.Fail", "RETRYING"),
            ("saga.OrderSaga.state.RETRYING.on.GiveUp", "FAILED"),
        ]);
        let fsm = parse_fsm(&p, "OrderSaga").unwrap();
        assert!(validate(&fsm).is_empty());
    }

    #[test]
    fn dead_end_is_detected() {
        let p = props(&[
            ("saga.OrderSaga.start", "START"),
            ("saga.OrderSaga.end", "COMPLETED"),
            ("saga.OrderSaga.states", "START,STUCK,COMPLETED"),
            ("saga.OrderSaga.state.START.on.SomethingHappened", "STUCK"),
            // STUCK não tem nenhuma transição de saída e não é end state.
        ]);
        let fsm = parse_fsm(&p, "OrderSaga").unwrap();
        let errors = validate(&fsm);
        assert!(errors.iter().any(|e| matches!(e, FsmError::DeadEnd(s) if s == "STUCK")),
            "expected a DeadEnd error for STUCK, got: {errors:?}");
    }
}
