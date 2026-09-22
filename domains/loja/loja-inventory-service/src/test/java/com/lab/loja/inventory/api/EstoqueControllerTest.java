package com.lab.loja.inventory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.lab.loja.inventory.StockItem;
import com.lab.loja.inventory.StockItemRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Unit puro com Mockito -- nenhum Spring context, nenhum servlet real sobe (mesma convenção de
 * {@code InventoryReservationServiceMockitoTest}). Cobre só a lógica de mapeamento/roteamento
 * de {@link EstoqueController}; não valida serialização JSON HTTP de ponta a ponta.
 *
 * <p>Cobre também, por omissão deliberada, a garantia arquitetural: esta classe não tem
 * nenhum teste de POST/PUT porque {@link EstoqueController} não expõe nenhum -- reserva/
 * liberação de estoque continua exclusivamente via Kafka.
 */
@ExtendWith(MockitoExtension.class)
class EstoqueControllerTest {

    @Mock StockItemRepository repository;

    EstoqueController controller;

    @BeforeEach
    void setUp() {
        controller = new EstoqueController(repository);
    }

    @Test
    void listarRetornaTodosOsItensMapeadosParaDto() {
        when(repository.findAll()).thenReturn(
                List.of(new StockItem("SKU-A", 10), new StockItem("SKU-B", 0)));

        var resultado = controller.listar();

        assertEquals(2, resultado.size());
        assertEquals("SKU-A", resultado.get(0).sku());
        assertEquals(10L, resultado.get(0).availableQuantity());
        assertEquals("SKU-B", resultado.get(1).sku());
        assertEquals(0L, resultado.get(1).availableQuantity());
    }

    @Test
    void listarRetornaListaVaziaQuandoNaoHaItens() {
        when(repository.findAll()).thenReturn(List.of());

        assertTrue(controller.listar().isEmpty());
    }

    @Test
    void buscarPorSkuRetorna200ComOItemQuandoExiste() {
        when(repository.findById("SKU-A")).thenReturn(Optional.of(new StockItem("SKU-A", 7)));

        var resposta = controller.buscarPorSku("SKU-A");

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals("SKU-A", resposta.getBody().sku());
        assertEquals(7L, resposta.getBody().availableQuantity());
    }

    @Test
    void buscarPorSkuRetorna404QuandoSkuNaoExiste() {
        when(repository.findById("SKU-NUNCA-CADASTRADO")).thenReturn(Optional.empty());

        var resposta = controller.buscarPorSku("SKU-NUNCA-CADASTRADO");

        assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode());
        assertEquals(null, resposta.getBody());
    }
}
