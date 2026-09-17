package com.lab.loja.inventory;

import java.util.List;

/**
 * Payload esperado tanto em ReservarEstoque quanto em LiberarEstoque -- CONTRATO
 * importante: quem emite PagamentoRecusado (payment-service, ainda não implementado
 * neste pass) precisa ecoar orderId+items no payload do evento, senão o orquestrador
 * não tem como repassar esses dados pro comando LiberarEstoque (o motor só encaminha o
 * "data" do evento de entrada pro comando de saída, não faz lookup em nenhum banco).
 */
public class ReservationCommandData {
    private String orderId;
    private List<ItemQuantityDto> items;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public List<ItemQuantityDto> getItems() { return items; }
    public void setItems(List<ItemQuantityDto> items) { this.items = items; }
}
