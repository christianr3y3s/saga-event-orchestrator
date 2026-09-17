package com.lab.loja.inventory;

/** Um item dentro do pedido de reserva: SKU + quantidade solicitada. */
public record ItemRequest(String sku, long quantity) {
}
