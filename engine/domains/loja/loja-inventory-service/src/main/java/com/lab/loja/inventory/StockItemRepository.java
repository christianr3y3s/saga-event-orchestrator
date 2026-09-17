package com.lab.loja.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockItemRepository extends JpaRepository<StockItem, String> {
}
