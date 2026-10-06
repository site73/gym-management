package com.gym.payment;

import com.gym.membership.api.MembershipFacade;
import com.gym.payment.application.PaymentAppService;
import com.gym.payment.internal.PaymentOrderEntity;
import com.gym.payment.internal.PaymentOrderRepository;
import com.gym.payment.internal.SettlementRepository;
import com.gym.shared.audit.AuditLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 收费规则单元测试（S2 切片 · REQ-B5-001/004）。
 * 对应《关键规则—需求—测试溯源表》中的支付异常与幂等要求。
 */
class PaymentRulesTest {

    private final PaymentOrderRepository orderRepo = mock(PaymentOrderRepository.class);
    private final SettlementRepository settlementRepo = mock(SettlementRepository.class);
    private final MembershipFacade membershipFacade = mock(MembershipFacade.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final PaymentAppService service =
            new PaymentAppService(orderRepo, settlementRepo, membershipFacade, audit);

    private PaymentOrderEntity order(String orderNo, String amount) {
        var o = new PaymentOrderEntity(orderNo, 1L, "membership", new BigDecimal(amount));
        when(orderRepo.findByOrderNo(orderNo)).thenReturn(Optional.of(o));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return o;
    }

    @Test
    @DisplayName("下单金额必须大于 0")
    void reject_non_positive_amount() {
        assertThatThrownBy(() -> service.createOrder(1L, "membership", BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("金额");
    }

    @Test
    @DisplayName("支付成功：订单置为已支付，并经契约激活会籍")
    void pay_success_activates_membership() {
        order("O1", "3000");

        var view = service.handleNotify("O1", new BigDecimal("3000"), "WX1");

        assertThat(view.status()).isEqualTo("paid");
        verify(membershipFacade).activateMembership(eq(1L), eq("membership"), anyInt());
    }

    @Test
    @DisplayName("金额不符：订单置为异常，且不变更会籍（REQ-B5-004）")
    void amount_mismatch_marks_abnormal_and_no_activation() {
        var o = order("O2", "3000");

        var view = service.handleNotify("O2", new BigDecimal("300"), "WX2");

        assertThat(view.status()).isEqualTo("abnormal");
        assertThat(view.abnormalReason()).contains("金额");
        assertThat(o.getStatus()).isEqualTo("abnormal");
        verify(membershipFacade, never()).activateMembership(anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("重复回调幂等：不重复激活会籍（REQ-B5-004）")
    void duplicate_notify_is_idempotent() {
        order("O3", "1000");

        service.handleNotify("O3", new BigDecimal("1000"), "WX3");
        var second = service.handleNotify("O3", new BigDecimal("1000"), "WX3");

        assertThat(second.status()).isEqualTo("paid");
        verify(membershipFacade, times(1)).activateMembership(anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("订单不存在时抛出明确异常")
    void notify_unknown_order() {
        when(orderRepo.findByOrderNo("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleNotify("NOPE", BigDecimal.TEN, "WX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("订单不存在");
    }
}
