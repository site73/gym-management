package com.gym.payment.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrderEntity, Long> {

    Optional<PaymentOrderEntity> findByOrderNo(String orderNo);

    List<PaymentOrderEntity> findByMemberIdOrderByIdDesc(Long memberId);

    List<PaymentOrderEntity> findByStatus(String status);

    List<PaymentOrderEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
