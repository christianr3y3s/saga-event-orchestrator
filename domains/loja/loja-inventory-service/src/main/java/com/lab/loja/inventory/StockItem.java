package com.lab.loja.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    private String sku;

    private long availableQuantity;

    protected StockItem() { } // JPA

    public StockItem(String sku, long availableQuantity) {
        this.sku = sku;
        this.availableQuantity = availableQuantity;
    }

    public String getSku() { return sku; }
    public long getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(long availableQuantity) { this.availableQuantity = availableQuantity; }
}
