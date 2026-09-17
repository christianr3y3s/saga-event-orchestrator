package com.lab.loja.inventory;

/** DTO de (de)serialização JSON -- convertido para o record ItemRequest antes de chegar no serviço. */
public class ItemQuantityDto {
    private String sku;
    private long quantity;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
}
