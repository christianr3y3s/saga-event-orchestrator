package com.lab.cashback.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceRepository extends JpaRepository<Balance, String> {
}
