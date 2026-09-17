package com.lab.ingest.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Evita processar a mesma transactionId duas vezes (ex.: redelivery do Kafka depois de
 * um restart, ou reenvio acidental do POS). SETNX é atômico no Redis, então isso é
 * seguro mesmo com múltiplas instâncias do serviço rodando ao mesmo tempo.
 */
@Component
public class IdempotencyGuard {

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public IdempotencyGuard(StringRedisTemplate redis,
                             @Value("${app.redis.idempotency-ttl-seconds:86400}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * @return true na primeira vez que vê essa chave (deve seguir em frente);
     *         false se já foi vista antes (deve ser ignorada).
     */
    public boolean tryClaim(String transactionId) {
        Boolean firstTime = redis.opsForValue().setIfAbsent("idempotency:tx:" + transactionId, "1", ttl);
        return Boolean.TRUE.equals(firstTime);
    }
}
