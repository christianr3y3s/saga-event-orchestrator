package com.lab.ingest;

import com.lab.ingest.redis.TransactionOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Republica qualquer transação que ficou "pendente" no outbox -- cobre o caso em que o
 * processo morreu (ou o Kafka ficou indisponível) entre gravar no Redis e publicar com
 * sucesso no tópico de saída. É essa rotina que faz a garantia de durabilidade valer
 * na prática, não só o registro no Redis.
 */
@Component
public class OutboxReconciler {

    private static final Logger log = LoggerFactory.getLogger(OutboxReconciler.class);

    private final TransactionOutbox outbox;
    private final KafkaTemplate<String, String> kafka;
    private final String outTopic;

    public OutboxReconciler(TransactionOutbox outbox,
                             KafkaTemplate<String, String> kafka,
                             @Value("${app.kafka.out-topic}") String outTopic) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.outTopic = outTopic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.reconcile-fixed-delay-ms:5000}")
    public void reconcile() {
        Set<String> pending = outbox.pendingIds();
        if (pending.isEmpty()) return;

        log.info("reconciling {} pending outbox entries", pending.size());
        for (String transactionId : pending) {
            TransactionOutbox.OutboxEntry entry = outbox.read(transactionId);
            if (entry == null) {
                // já foi limpo por outra execução concorrente (ou outra instância do serviço)
                continue;
            }
            try {
                kafka.send(outTopic, entry.key, entry.value).get();
                outbox.markPublished(transactionId);
                log.info("reconciled outbox entry {}", transactionId);
            } catch (Exception e) {
                log.warn("reconciliation publish still failing for {}", transactionId, e);
            }
        }
    }
}
