package com.lab.cashback;

import com.lab.cashback.redis.BalanceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Republica confirmações de cashback (CashbackApplied) que ficaram pendentes -- o saldo
 * já foi creditado no Redis (applyOnce já rodou), só o publish no Kafka que falhou ou
 * nunca chegou a acontecer (processo morreu antes).
 */
@Component
public class CashbackOutboxReconciler {

    private static final Logger log = LoggerFactory.getLogger(CashbackOutboxReconciler.class);

    private final BalanceStore balances;
    private final KafkaTemplate<String, String> kafka;
    private final String outTopic;

    public CashbackOutboxReconciler(BalanceStore balances,
                                     KafkaTemplate<String, String> kafka,
                                     @Value("${app.kafka.out-topic}") String outTopic) {
        this.balances = balances;
        this.kafka = kafka;
        this.outTopic = outTopic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.reconcile-fixed-delay-ms:5000}")
    public void reconcile() {
        Set<String> pending = balances.pendingIds();
        if (pending.isEmpty()) return;

        log.info("reconciling {} pending cashback confirmations", pending.size());
        for (String correlationId : pending) {
            String eventJson = balances.readPendingEventJson(correlationId);
            if (eventJson == null) {
                continue; // claim expirou ou foi limpo por outra instância
            }
            try {
                kafka.send(outTopic, correlationId, eventJson).get();
                balances.markPublished(correlationId);
                log.info("reconciled cashback confirmation for {}", correlationId);
            } catch (Exception e) {
                log.warn("reconciliation publish still failing for {}", correlationId, e);
            }
        }
    }
}
