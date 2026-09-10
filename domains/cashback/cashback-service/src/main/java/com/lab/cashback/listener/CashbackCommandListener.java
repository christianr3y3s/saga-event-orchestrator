package com.lab.cashback.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.cashback.model.CashbackAppliedEvent;
import com.lab.cashback.model.CommandEnvelope;
import com.lab.cashback.redis.BalanceStore;
import com.lab.cashback.service.CashbackCalculator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class CashbackCommandListener {

    private static final Logger log = LoggerFactory.getLogger(CashbackCommandListener.class);

    private final ObjectMapper mapper;
    private final CashbackCalculator calculator;
    private final BalanceStore balances;
    private final KafkaTemplate<String, String> kafka;
    private final String outTopic;

    public CashbackCommandListener(ObjectMapper mapper,
                                    CashbackCalculator calculator,
                                    BalanceStore balances,
                                    KafkaTemplate<String, String> kafka,
                                    @Value("${app.kafka.out-topic}") String outTopic) {
        this.mapper = mapper;
        this.calculator = calculator;
        this.balances = balances;
        this.kafka = kafka;
        this.outTopic = outTopic;
    }

    @KafkaListener(topics = "${app.kafka.in-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onCalculateCashback(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            CommandEnvelope cmd = mapper.readValue(record.value(), CommandEnvelope.class);
            String correlationId = cmd.getCorrelationId();
            String userId = cmd.getData().getUserId();
            long cashbackCents = calculator.calculate(cmd.getData().getAmountCents());

            CashbackAppliedEvent event = new CashbackAppliedEvent();
            event.setCorrelationId(correlationId);
            event.setUserId(userId);
            event.setCashbackCents(cashbackCents);
            event.setAppliedAtMs(System.currentTimeMillis());

            // applyOnce já cuida de: (1) idempotência -- não credita duas vezes a mesma
            // transação; (2) durabilidade -- guarda o evento de confirmação junto com o
            // claim, então mesmo se o publish abaixo falhar, o CashbackOutboxReconciler
            // consegue republicar sem perder nem duplicar o crédito já aplicado.
            BalanceStore.ApplyResult result = balances.applyOnce(correlationId, userId, cashbackCents, event);

            if (result == BalanceStore.ApplyResult.ALREADY_APPLIED) {
                log.info("cashback for {} already applied, skipping (redelivery)", correlationId);
                ack.acknowledge();
                return;
            }

            // Saldo já garantido no Redis -- confirmamos a mensagem original agora.
            ack.acknowledge();

            publish(correlationId, event);

        } catch (Exception e) {
            log.error("failed to process cashback command, will be redelivered", e);
            // Não faz ack. Na redelivery, applyOnce() vai retornar ALREADY_APPLIED se o
            // saldo já tiver sido creditado antes da falha -- reprocessar é seguro.
        }
    }

    private void publish(String correlationId, CashbackAppliedEvent event) {
        try {
            String json = mapper.writeValueAsString(event);
            kafka.send(outTopic, correlationId, json).get();
            balances.markPublished(correlationId);
            log.info("cashback applied and published: correlationId={} userId={} cashbackCents={}",
                    correlationId, event.getUserId(), event.getCashbackCents());
        } catch (Exception e) {
            log.warn("publish failed for {}, leaving pending for reconciliation", correlationId, e);
        }
    }
}
