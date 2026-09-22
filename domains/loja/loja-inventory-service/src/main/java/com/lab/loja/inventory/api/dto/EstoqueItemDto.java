package com.lab.loja.inventory.api.dto;

import com.lab.loja.inventory.StockItem;

/**
 * Projeção somente-leitura de {@link StockItem} para a API HTTP. Não existe
 * {@code paraDominio()} de propósito -- este DTO nunca vira uma mutação (ver
 * {@link com.lab.loja.inventory.api.EstoqueController}).
 */
public record EstoqueItemDto(String sku, long availableQuantity) {

    public static EstoqueItemDto deDominio(StockItem item) {
        return new EstoqueItemDto(item.getSku(), item.getAvailableQuantity());
    }
}
