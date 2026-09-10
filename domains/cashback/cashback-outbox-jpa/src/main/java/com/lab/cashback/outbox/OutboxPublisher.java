package com.lab.cashback.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Metade "publish" do outbox pattern: roda fora da transação de negócio (de propósito
 * -- não faz sentido segurar a transação que aplicou o cashback esperando o Kafka
 * responder). Lê os PENDING em lote, publica, marca PUBLISHED um por um.
 *
 * Cada evento é marcado PUBLISHED em sua PRÓPRIA transação curta -- se o processo cair
 * no meio do lote, os já marcados ficam marcados (não duplicam no próximo ciclo) e os
 * que faltam são pegos de novo na próxima execução.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final String outTopic;

    public OutboxPublisher(OutboxEventRepository outbox,
                            KafkaTemplate<String, String> kafka,
                            @Value("${app.kafka.out-topic}") String outTopic) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.outTopic = outTopic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-fixed-delay-ms:5000}")
    public void publishPending() {
        List<OutboxEvent> pending = outbox.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) return;

        log.info("publishing {} pending outbox events", pending.size());
        for (OutboxEvent event : pending) {
            try {
                kafka.send(outTopic, event.getCorrelationId(), event.getPayload()).get();
                markPublished(event);
            } catch (Exception e) {
                log.warn("failed to publish outbox event {}, will retry next cycle", event.getId(), e);
                // não marca PUBLISHED -- próximo ciclo tenta de novo. Publish pode
                // duplicar em caso de crash entre o send() e o markPublished() logo
                // abaixo; o consumidor do tópico precisa ser idempotente por
                // correlationId (mesma exigência que já vale pro resto do fluxo).
            }
        }
    }

    /**
     * De propósito NÃO tem @Transactional próprio: chamar um método @Transactional da
     * MESMA classe (this.markPublished(...) dentro de publishPending) passaria por cima
     * do proxy do Spring e a anotação seria ignorada silenciosamente (auto-invocação).
     * Em vez disso, aproveitamos que outbox.save(...) já é transacional por si só (todo
     * método de um Spring Data repository roda em sua própria transação curta).
     */
    private void markPublished(OutboxEvent event) {
        event.setStatus(OutboxStatus.PUBLISHED);
        outbox.save(event);
    }
}
