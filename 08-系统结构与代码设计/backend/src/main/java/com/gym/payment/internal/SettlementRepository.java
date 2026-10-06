package com.gym.payment.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<SettlementEntity, Long> {
    Optional<SettlementEntity> findByPeriod(String period);
    List<SettlementEntity> findAllByOrderByPeriodDesc();
}
