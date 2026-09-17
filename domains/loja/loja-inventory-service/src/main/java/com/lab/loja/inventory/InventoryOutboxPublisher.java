package com.lab.loja.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Metade "publish" do outbox pattern, mesmo desenho de OutboxPublisher em
 * cashback-outbox-jpa. Publica cada InventoryOutboxEvent PENDING no tópico
 * correspondente ao seu eventType -- os três eventos ("EstoqueReservado",
 * "EstoqueIndisponivel", "EstoqueLiberado") têm tópicos DIFERENTES em
 * orchestrator.loja.properties (cada um é um event.in.<Evento>.topic próprio), então o
 * roteamento aqui precisa ser exato por tipo, não em pares.
 */
@Component
public class InventoryOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryOutboxPublisher.class);

    private final InventoryOutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Map<String, String> topicByEventType;

    public InventoryOutboxPublisher(InventoryOutboxEventRepository outbox,
                                     KafkaTemplate<String, String> kafka,
                                     @Value("${app.kafka.out-topic-reservado}") String reservadoTopic,
                                     @Value("${app.kafka.out-topic-indisponivel}") String indisponivelTopic,
                                     @Value("${app.kafka.out-topic-liberado}") String liberadoTopic) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.topicByEventType = Map.of(
                "EstoqueReservado", reservadoTopic,
                "EstoqueIndisponivel", indisponivelTopic,
                "EstoqueLiberado", liberadoTopic
        );
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-fixed-delay-ms:5000}")
    public void publishPending() {
        List<InventoryOutboxEvent> pending = outbox.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) return;

        log.info("publishing {} pending inventory outbox events", pending.size());
        for (InventoryOutboxEvent event : pending) {
            String topic = topicByEventType.get(event.getEventType());
            if (topic == null) {
                log.error("no topic configured for eventType {}, skipping event {} (won't retry -- fix the mapping)",
                        event.getEventType(), event.getId());
                continue;
            }
            try {
                kafka.send(topic, event.getCorrelationId(), event.getPayload()).get();
                markPublished(event);
            } catch (Exception e) {
                log.warn("failed to publish inventory outbox event {}, will retry next cycle", event.getId(), e);
            }
        }
    }

    private void markPublished(InventoryOutboxEvent event) {
        event.setStatus(OutboxStatus.PUBLISHED);
        outbox.save(event);
    }
}
