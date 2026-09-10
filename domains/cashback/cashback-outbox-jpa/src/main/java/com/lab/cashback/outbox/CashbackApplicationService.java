package com.lab.cashback.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diferença central em relação à versão com Redis: aqui o incremento de saldo e o
 * registro do evento de saída (outbox) acontecem na MESMA transação de banco (uma
 * única @Transactional cobrindo os dois `save`). Se qualquer parte falhar, o banco desfaz
 * as duas -- é uma garantia ACID de verdade, não um "provavelmente está tudo certo" como
 * era com duas chaves separadas no Redis.
 */
@Service
public class CashbackApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CashbackApplicationService.class);

    public enum ApplyResult { APPLIED_NOW, ALREADY_APPLIED }

    private final BalanceRepository balances;
    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;

    public CashbackApplicationService(BalanceRepository balances, OutboxEventRepository outbox, ObjectMapper mapper) {
        this.balances = balances;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public ApplyResult applyCashback(String correlationId, String userId, long cashbackCents) {
        // Checagem otimista: evita fazer o trabalho de incrementar saldo à toa no caso
        // comum (redelivery normal do Kafka). NÃO é a garantia de idempotência -- só uma
        // otimização. Quem garante de verdade é a constraint UNIQUE em correlationId: se
        // duas transações concorrentes passarem por aqui ao mesmo tempo, uma das duas vai
        // falhar no insert do outbox com DataIntegrityViolationException.
        //
        // IMPORTANTE: essa exceção NÃO é tratada aqui dentro. Pegar e continuar dentro do
        // mesmo método @Transactional funciona no H2 (que é tolerante), mas quebra no
        // Postgres -- lá, qualquer erro aborta a transação inteira até um ROLLBACK
        // explícito; qualquer comando depois disso (mesmo um SELECT) falha com
        // "current transaction is aborted". Deixamos propagar de propósito: o Spring
        // desfaz a transação inteira (saldo + outbox) de forma limpa e portável entre
        // bancos, e quem chama este método decide o que fazer com o "já foi aplicado"
        // (veja o catch em CashbackApplicationServiceAtomicityTest e o comentário no
        // listener Kafka de produção).
        if (outbox.findByCorrelationId(correlationId).isPresent()) {
            log.info("cashback for {} already applied (found by pre-check), skipping", correlationId);
            return ApplyResult.ALREADY_APPLIED;
        }

        Balance balance = balances.findById(userId)
                .orElseGet(() -> new Balance(userId, 0));
        balance.setAmountCents(balance.getAmountCents() + cashbackCents);
        balances.save(balance);

        OutboxEvent event = new OutboxEvent(correlationId, "CashbackApplied", toPayload(correlationId, userId, cashbackCents));
        outbox.save(event);

        return ApplyResult.APPLIED_NOW;
    }

    private String toPayload(String correlationId, String userId, long cashbackCents) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "CashbackApplied");
            map.put("correlationId", correlationId);
            map.put("userId", userId);
            map.put("cashbackCents", cashbackCents);
            map.put("appliedAtMs", System.currentTimeMillis());
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize outbox payload", e);
        }
    }
}
