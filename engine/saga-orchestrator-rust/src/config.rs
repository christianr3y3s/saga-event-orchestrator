use std::{collections::HashMap, fs, path::Path};

#[derive(Debug, Clone)]
pub struct OrchestratorConfig {
    pub kafka: KafkaConfig,
    pub conventions: Conventions,
    pub event_in: Vec<EventIn>,
    pub command_out: HashMap<String, String>,
    pub routes: HashMap<String, Vec<String>>,
    pub error_policy: ErrorPolicy,
}

#[derive(Debug, Clone)]
pub struct KafkaConfig {
    pub bootstrap_servers: String,
    pub group_id: String,
    pub auto_offset_reset: String,
    pub enable_auto_commit: bool,
    pub dlq_topic: String,
    pub retry_topic: String,
    pub state_topic: String,
}

#[derive(Debug, Clone)]
pub struct Conventions {
    pub event_type_source: String, // payload|topic
    pub event_type_path: String,   // $.type
    pub correlation_source: String, // payload|header
    pub correlation_path: String,   // $.correlationId (dot path subset)
    pub step_timeout_ms: u64,
}

#[derive(Debug, Clone)]
pub struct EventIn {
    pub name: String,
    pub topic: String,
}

#[derive(Debug, Clone)]
pub struct ErrorPolicy {
    pub on_parse: String,
    pub on_missing_correlation: String,
    pub on_missing_event_type: String,
    pub on_unknown_event: String,
    pub on_publish_fail: String,
}

pub fn load_properties(path: impl AsRef<Path>) -> anyhow::Result<HashMap<String, String>> {
    let raw = fs::read_to_string(path)?;
    let mut map = HashMap::new();

    for line in raw.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        // support "key=value" only
        if let Some((k, v)) = line.split_once('=') {
            map.insert(k.trim().to_string(), v.trim().to_string());
        }
    }
    Ok(map)
}

pub fn load_config(path: impl AsRef<Path>) -> anyhow::Result<OrchestratorConfig> {
    let p = load_properties(path)?;

    let kafka = KafkaConfig {
        bootstrap_servers: get(&p, "kafka.bootstrap.servers")?,
        group_id: get(&p, "kafka.group.id")?,
        auto_offset_reset: p.get("kafka.auto.offset.reset").cloned().unwrap_or_else(|| "earliest".into()),
        enable_auto_commit: p.get("kafka.enable.auto.commit").map(|v| v == "true").unwrap_or(true),
        dlq_topic: p.get("kafka.dlq.topic").cloned().unwrap_or_else(|| "_orchestrator.dlq".into()),
        retry_topic: p.get("kafka.retry.topic").cloned().unwrap_or_else(|| "_orchestrator.retry".into()),
        state_topic: p.get("kafka.state.topic").cloned().unwrap_or_else(|| "_orchestrator.state".into()),
    };

    let conventions = Conventions {
        event_type_source: p.get("event.type.source").cloned().unwrap_or_else(|| "payload".into()),
        event_type_path: p.get("event.type.path").cloned().unwrap_or_else(|| "$.type".into()),
        correlation_source: p.get("correlation.source").cloned().unwrap_or_else(|| "payload".into()),
        correlation_path: p.get("correlation.path").cloned().unwrap_or_else(|| "$.correlationId".into()),
        step_timeout_ms: p.get("saga.step.timeout.ms").and_then(|v| v.parse().ok()).unwrap_or(30_000),
    };

    // event.in.*
    let mut event_in = vec![];
    for (k, v) in &p {
        if let Some(rest) = k.strip_prefix("event.in.") {
            if let Some(name) = rest.strip_suffix(".topic") {
                event_in.push(EventIn { name: name.to_string(), topic: v.to_string() });
            }
        }
    }
    event_in.sort_by(|a,b| a.name.cmp(&b.name));

    // command.out.*
    let mut command_out = HashMap::new();
    for (k, v) in &p {
        if let Some(rest) = k.strip_prefix("command.out.") {
            if let Some(cmd) = rest.strip_suffix(".topic") {
                command_out.insert(cmd.to_string(), v.to_string());
            }
        }
    }

    // route.*
    let mut routes = HashMap::new();
    for (k, v) in &p {
        if let Some(rest) = k.strip_prefix("route.") {
            if let Some(event_type) = rest.strip_suffix(".emit") {
                let cmds = v.split(',').map(|s| s.trim().to_string()).filter(|s| !s.is_empty()).collect::<Vec<_>>();
                routes.insert(event_type.to_string(), cmds);
            }
        }
    }

    let error_policy = ErrorPolicy {
        on_parse: p.get("error.on.parse").cloned().unwrap_or_else(|| "DLQ".into()),
        on_missing_correlation: p.get("error.on.missing.correlation").cloned().unwrap_or_else(|| "DLQ".into()),
        on_missing_event_type: p.get("error.on.missing.event.type").cloned().unwrap_or_else(|| "DLQ".into()),
        on_unknown_event: p.get("error.on.unknown.event").cloned().unwrap_or_else(|| "IGNORE".into()),
        on_publish_fail: p.get("error.on.publish.fail").cloned().unwrap_or_else(|| "RETRY".into()),
    };

    Ok(OrchestratorConfig { kafka, conventions, event_in, command_out, routes, error_policy })
}

fn get(map: &HashMap<String, String>, key: &str) -> anyhow::Result<String> {
    map.get(key)
        .cloned()
        .ok_or_else(|| anyhow::anyhow!("Missing required property: {key}"))
}
