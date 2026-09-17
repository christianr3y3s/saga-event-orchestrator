package com.lab.cashback.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * "Efetiva" o cashback: incrementa o saldo do usuário de forma idempotente por
 * correlationId (id da transação de origem). Se o mesmo comando for reprocessado
 * (redelivery do Kafka), o incremento NÃO é aplicado de novo.
 *
 * A chave de claim já guarda o JSON do evento de confirmação a publicar -- isso funciona
 * como outbox: se o processo cair depois de aplicar o saldo mas antes de publicar a
 * confirmação, o CashbackOutboxReconciler encontra essa entrada pendente e publica.
 */
@Component
public class BalanceStore {

    private static final String PENDING_SET = "cashback:applied:pending";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public BalanceStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public enum ApplyResult { APPLIED_NOW, ALREADY_APPLIED }

    /**
     * Tenta aplicar o cashback pela primeira vez para este correlationId.
     * @param confirmationEvent objeto a ser serializado e guardado para publicação (e reconciliação)
     */
    public ApplyResult applyOnce(String correlationId, String userId, long cashbackCents, Object confirmationEvent) {
        String claimKey = claimKey(correlationId);
        try {
            String eventJson = mapper.writeValueAsString(confirmationEvent);
            Boolean claimed = redis.opsForValue().setIfAbsent(claimKey, eventJson, Duration.ofDays(7));
            if (!Boolean.TRUE.equals(claimed)) {
                return ApplyResult.ALREADY_APPLIED;
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to claim cashback application for " + correlationId, e);
        }

        redis.opsForValue().increment("cashback:balance:" + userId, cashbackCents);
        redis.opsForSet().add(PENDING_SET, correlationId);
        return ApplyResult.APPLIED_NOW;
    }

    public void markPublished(String correlationId) {
        // Mantemos o claimKey (não apagamos) -- ele continua servindo de guarda de
        // idempotência permanente até expirar o TTL de 7 dias. Só tiramos do pendente.
        redis.opsForSet().remove(PENDING_SET, correlationId);
    }

    public Set<String> pendingIds() {
        return redis.opsForSet().members(PENDING_SET);
    }

    public String readPendingEventJson(String correlationId) {
        return redis.opsForValue().get(claimKey(correlationId));
    }

    public long getBalance(String userId) {
        String v = redis.opsForValue().get("cashback:balance:" + userId);
        return v == null ? 0 : Long.parseLong(v);
    }

    private String claimKey(String correlationId) {
        return "cashback:applied:" + correlationId;
    }
}
