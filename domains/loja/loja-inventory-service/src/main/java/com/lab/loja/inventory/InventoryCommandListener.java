package com.lab.loja.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Fecha o ciclo que cashback-outbox-jpa (o módulo irmão neste repo, no domínio cashback)
 * deixa em aberto de propósito: lá o objetivo era provar a atomicidade da transação
 * JPA+outbox isoladamente, sem listener Kafka. Aqui, como este é o serviço que a saga
 * `loja` de fato usa, o listener consome os comandos do orquestrador e delega pro
 * InventoryReservationService -- a garantia de atomicidade/idempotência continua sendo a
 * mesma (constraint UNIQUE via @Transactional), só agora conectada de ponta a ponta.
 */
@Component
public class InventoryCommandListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandListener.class);
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final ObjectMapper mapper;
    private final InventoryReservationService service;

    public InventoryCommandListener(ObjectMapper mapper, InventoryReservationService service) {
        this.mapper = mapper;
        this.service = service;
    }

    @KafkaListener(topics = "${app.kafka.in-topic-reservar}", groupId = "${spring.kafka.consumer.group-id}")
    public void onReservarEstoque(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            CommandEnvelope cmd = mapper.readValue(record.value(), CommandEnvelope.class);
            // MDC.put SÓ depois do parse ter sucesso -- antes disso não temos
            // correlationId ainda. Ver logstash-logback-encoder.includeMdcKeyName em
            // logback-spring.xml: qualquer log.*(...) chamado enquanto esta chave está no
            // MDC carrega "correlationId" automaticamente no JSON de saída, sem precisar
            // passar o valor manualmente em cada chamada de log (mesmo efeito prático do
            // campo %saga_id que o tracing do orquestrador Rust já captura por struct logging).
            MDC.put(MDC_CORRELATION_ID, cmd.getCorrelationId());

            List<ItemRequest> items = toItemRequests(cmd.getData().getItems());

            InventoryReservationService.ReserveResult result =
                    service.reserveStock(cmd.getCorrelationId(), cmd.getData().getOrderId(), items);
            log.info("ReservarEstoque -> {}", result);

            // O commit no banco já aconteceu dentro de reserveStock() (@Transactional) --
            // o evento de saída já está PENDING no outbox, publicado de forma assíncrona
            // por InventoryOutboxPublisher. Confirmar o comando de entrada agora é seguro,
            // mesma lógica de CashbackCommandListener (Redis) no domínio cashback.
            ack.acknowledge();
        } catch (Exception e) {
            log.error("failed to process ReservarEstoque command, will be redelivered", e);
            // Não confirma -- na redelivery, reserveStock() acha o outbox já gravado
            // (ALREADY_PROCESSED) se a falha ocorreu DEPOIS do commit; reprocessar é seguro.
        } finally {
            // OBRIGATÓRIO: o consumer Kafka reutiliza a mesma thread pra cada mensagem.
            // Sem limpar, o correlationId de UMA mensagem vazaria pros logs de todas as
            // mensagens processadas depois dela nessa thread até o próximo MDC.put.
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    @KafkaListener(topics = "${app.kafka.in-topic-liberar}", groupId = "${spring.kafka.consumer.group-id}")
    public void onLiberarEstoque(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            CommandEnvelope cmd = mapper.readValue(record.value(), CommandEnvelope.class);
            MDC.put(MDC_CORRELATION_ID, cmd.getCorrelationId());

            List<ItemRequest> items = toItemRequests(cmd.getData().getItems());

            service.releaseStock(cmd.getCorrelationId(), cmd.getData().getOrderId(), items);
            log.info("LiberarEstoque processado");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("failed to process LiberarEstoque command, will be redelivered", e);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    private List<ItemRequest> toItemRequests(List<ItemQuantityDto> dtos) {
        return dtos.stream()
                .map(dto -> new ItemRequest(dto.getSku(), dto.getQuantity()))
                .collect(Collectors.toList());
    }
}
