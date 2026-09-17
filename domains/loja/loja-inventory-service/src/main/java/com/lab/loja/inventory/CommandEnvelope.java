package com.lab.loja.inventory;

/**
 * Formato exato que o orquestrador Rust usa em emit_command():
 * { "type": cmd, "correlationId": saga_id, "ts": ms, "data": <evento original> }
 * (mesmo contrato documentado em CommandEnvelope do domínio cashback)
 */
public class CommandEnvelope {
    private String type;
    private String correlationId;
    private long ts;
    private ReservationCommandData data;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }

    public ReservationCommandData getData() { return data; }
    public void setData(ReservationCommandData data) { this.data = data; }
}
