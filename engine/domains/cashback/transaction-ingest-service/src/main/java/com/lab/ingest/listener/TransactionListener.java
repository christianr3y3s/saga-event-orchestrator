package com.lab.ingest.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.ingest.model.TransactionEvent;
import com.lab.ingest.model.ValidatedTransactionEvent;
import com.lab.ingest.redis.IdempotencyGuard;
import com.lab.ingest.redis.TransactionOutbox;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class TransactionListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionListener.class);

    private final ObjectMapper mapper;
    private final IdempotencyGuard idempotency;
    private final TransactionOutbox outbox;
    private final KafkaTemplate<String, String> kafka;
    private final String outTopic;

    public TransactionListener(ObjectMapper mapper,
                                IdempotencyGuard idempotency,
                                TransactionOutbox outbox,
                                KafkaTemplate<String, String> kafka,
                                @Value("${app.kafka.out-topic}") String outTopic) {
        this.mapper = mapper;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.kafka = kafka;
        this.outTopic = outTopic;
    }

    @KafkaListener(topics = "${app.kafka.in-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onTransaction(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            TransactionEvent tx = mapper.readValue(record.value(), TransactionEvent.class);

            // Idempotência: se já vimos essa transactionId, é redelivery -- só confirma e sai.
            if (!idempotency.tryClaim(tx.getTransactionId())) {
                log.info("transaction {} already ingested, skipping (redelivery)", tx.getTransactionId());
                ack.acknowledge();
                return;
            }

            ValidatedTransactionEvent validated = toValidated(tx);

            // Durabilidade: grava no outbox do Redis ANTES de tentar publicar no Kafka.
            outbox.markPending(tx.getTransactionId(), validated.getCorrelationId(), validated);

            // A partir daqui a transação está garantida (outbox + reconciler cobrem o
            // publish). Confirmamos a mensagem original já -- é o que mantém o caixa
            // rápido: não ficamos bloqueados esperando o Kafka confirmar o send abaixo.
            ack.acknowledge();

            publish(tx.getTransactionId(), validated);

        } catch (Exception e) {
            log.error("failed to process transaction message, will be redelivered", e);
            // Não faz ack -- Kafka reentrega. Como o primeiro passo é tryClaim (idempotente),
            // reprocessar essa mensagem do zero é seguro.
        }
    }

    private void publish(String transactionId, ValidatedTransactionEvent validated) {
        try {
            String json = mapper.writeValueAsString(validated);
            kafka.send(outTopic, validated.getCorrelationId(), json).get();
            outbox.markPublished(transactionId);
        } catch (Exception e) {
            log.warn("publish failed for {}, leaving in outbox for reconciliation", transactionId, e);
            // Fica marcado como pendente; o OutboxReconciler tenta de novo periodicamente.
        }
    }

    private ValidatedTransactionEvent toValidated(TransactionEvent tx) {
        ValidatedTransactionEvent v = new ValidatedTransactionEvent();
        v.setCorrelationId(tx.getTransactionId());
        v.setUserId(tx.getUserId());
        v.setAmountCents(tx.getAmountCents());
        v.setCurrency(tx.getCurrency());
        v.setValidatedAtMs(System.currentTimeMillis());
        return v;
    }
}
