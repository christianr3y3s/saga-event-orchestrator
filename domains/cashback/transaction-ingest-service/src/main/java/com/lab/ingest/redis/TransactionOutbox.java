package com.lab.ingest.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Outbox simplificado em Redis: a "rede de segurança" pedida -- garante que uma
 * transação já aceita (idempotência já reivindicada) não se perca se o processo cair
 * entre "recebi do Kafka" e "publiquei com sucesso no tópico de saída".
 *
 * Não é um outbox transacional de verdade (não há 2PC entre consumir do Kafka e
 * escrever no Redis) -- é proteção best-effort contra o caso comum (crash do processo),
 * não contra perda de dados do próprio Redis.
 */
@Component
public class TransactionOutbox {

    private static final String PENDING_SET = "outbox:tx:pending";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public TransactionOutbox(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /** Grava a transação como "pendente de publicação" antes de tentar enviar ao Kafka. */
    public void markPending(String transactionId, String kafkaKey, Object payload) {
        try {
            String valueJson = mapper.writeValueAsString(payload);
            OutboxEntry entry = new OutboxEntry(kafkaKey, valueJson);
            redis.opsForValue().set(entryKey(transactionId), mapper.writeValueAsString(entry), Duration.ofDays(2));
            redis.opsForSet().add(PENDING_SET, transactionId);
        } catch (Exception e) {
            throw new RuntimeException("failed to write outbox entry for " + transactionId, e);
        }
    }

    /** Confirma que a publicação teve sucesso; remove do conjunto de pendentes. */
    public void markPublished(String transactionId) {
        redis.delete(entryKey(transactionId));
        redis.opsForSet().remove(PENDING_SET, transactionId);
    }

    public Set<String> pendingIds() {
        return redis.opsForSet().members(PENDING_SET);
    }

    /** Lê a entrada crua (chave + valor Kafka originais) para reconciliação. */
    public OutboxEntry read(String transactionId) {
        String raw = redis.opsForValue().get(entryKey(transactionId));
        if (raw == null) return null;
        try {
            return mapper.readValue(raw, OutboxEntry.class);
        } catch (Exception e) {
            throw new RuntimeException("corrupt outbox entry for " + transactionId, e);
        }
    }

    private String entryKey(String transactionId) {
        return "outbox:tx:" + transactionId;
    }

    /** Par (chave Kafka, valor JSON) preservado para que a reconciliação publique exatamente como seria feito originalmente. */
    public static class OutboxEntry {
        public String key;
        public String value;

        public OutboxEntry() {}
        public OutboxEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
