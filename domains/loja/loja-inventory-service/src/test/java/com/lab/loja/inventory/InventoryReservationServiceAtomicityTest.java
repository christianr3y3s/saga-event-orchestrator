package com.lab.loja.inventory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mesma técnica documentada em CashbackApplicationServiceAtomicityTest (domínio
 * cashback): @Transactional(propagation = NOT_SUPPORTED) na classe desliga o embrulho
 * transacional automático do @DataJpaTest, então cada chamada ao serviço abre e fecha a
 * PRÓPRIA transação (igual em produção), e cada leitura de verificação depois também
 * abre a sua -- sem falso-positivo por objeto gerenciado em memória.
 *
 * Marcada como "integration": sobe Spring context + H2 de verdade.
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(InventoryReservationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InventoryReservationServiceAtomicityTest {

    @Autowired InventoryReservationService service;
    @Autowired StockItemRepository stockItems;
    @Autowired InventoryOutboxEventRepository outbox;

    @Test
    void reservesStockAndRecordsOutboxTogether() {
        stockItems.save(new StockItem("SKU-A", 10));

        var result = service.reserveStock("tx-100", "order-100", List.of(new ItemRequest("SKU-A", 3)));

        assertEquals(InventoryReservationService.ReserveResult.RESERVED, result);
        assertEquals(7L, stockItems.findById("SKU-A").orElseThrow().getAvailableQuantity());
        assertTrue(outbox.findByCorrelationIdAndEventType("tx-100", "EstoqueReservado").isPresent());
    }

    @Test
    void sameCorrelationIdIsNeverReservedTwice() {
        stockItems.save(new StockItem("SKU-B", 10));

        service.reserveStock("tx-101", "order-101", List.of(new ItemRequest("SKU-B", 4)));
        var second = service.reserveStock("tx-101", "order-101", List.of(new ItemRequest("SKU-B", 4))); // redelivery

        assertEquals(InventoryReservationService.ReserveResult.ALREADY_PROCESSED, second);
        assertEquals(6L, stockItems.findById("SKU-B").orElseThrow().getAvailableQuantity(),
                "estoque não pode ter sido decrementado duas vezes pela mesma transação");
    }

    @Test
    void sameCorrelationIdCanBeReservedThenReleased() {
        // Prova que a constraint composta (correlationId, eventType) permite o PAR
        // legítimo reserva->liberação para o MESMO correlationId, ao contrário de uma
        // constraint simples em correlationId (que bloquearia a compensação).
        stockItems.save(new StockItem("SKU-C", 10));

        service.reserveStock("tx-102", "order-102", List.of(new ItemRequest("SKU-C", 5)));
        assertEquals(5L, stockItems.findById("SKU-C").orElseThrow().getAvailableQuantity());

        service.releaseStock("tx-102", "order-102", List.of(new ItemRequest("SKU-C", 5)));
        assertEquals(10L, stockItems.findById("SKU-C").orElseThrow().getAvailableQuantity());

        assertTrue(outbox.findByCorrelationIdAndEventType("tx-102", "EstoqueReservado").isPresent());
        assertTrue(outbox.findByCorrelationIdAndEventType("tx-102", "EstoqueLiberado").isPresent());
    }

    @Test
    void insufficientStockLeavesNoItemMutatedEvenWithMultipleSkus() {
        stockItems.save(new StockItem("SKU-D", 100));
        stockItems.save(new StockItem("SKU-E", 1)); // este vai faltar

        var result = service.reserveStock("tx-103", "order-103",
                List.of(new ItemRequest("SKU-D", 5), new ItemRequest("SKU-E", 10)));

        assertEquals(InventoryReservationService.ReserveResult.INSUFFICIENT_STOCK, result);
        // SKU-D tinha de sobra mas NÃO deve ter sido tocado -- é tudo ou nada
        assertEquals(100L, stockItems.findById("SKU-D").orElseThrow().getAvailableQuantity());
        assertEquals(1L, stockItems.findById("SKU-E").orElseThrow().getAvailableQuantity());
    }

    @Test
    void concurrentDuplicateLosesRaceViaCompositeUniqueConstraintNotViaPreCheck() {
        // Mesma prova do cashback: insere o outbox de tx-104 diretamente, ignorando a
        // checagem otimista do serviço -- prova que é a constraint UNIQUE (não o "já
        // existe?" em InventoryReservationService) quem impede a duplicata sob concorrência.
        outbox.save(new InventoryOutboxEvent("tx-104", "EstoqueReservado", "{}"));

        assertThrows(DataIntegrityViolationException.class, () ->
                outbox.saveAndFlush(new InventoryOutboxEvent("tx-104", "EstoqueReservado", "{}")),
            "a constraint UNIQUE(correlationId, eventType) deveria rejeitar a segunda inserção do MESMO par");
    }

    @Test
    void differentEventTypesForSameCorrelationIdAreBothAllowedByTheConstraint() {
        outbox.save(new InventoryOutboxEvent("tx-105", "EstoqueReservado", "{}"));

        // Mesmo correlationId, eventType DIFERENTE -- não deve ser rejeitado. É essa
        // permissividade seletiva que diferencia a constraint daqui da do cashback.
        assertDoesNotThrow(() ->
                outbox.saveAndFlush(new InventoryOutboxEvent("tx-105", "EstoqueLiberado", "{}")));
    }
}
