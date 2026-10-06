package com.gym.payment.application;

import com.gym.membership.api.MembershipFacade;
import com.gym.payment.api.PaymentFacade;
import com.gym.payment.api.PaymentOrderView;
import com.gym.payment.api.SettlementView;
import com.gym.payment.internal.PaymentOrderEntity;
import com.gym.payment.internal.PaymentOrderRepository;
import com.gym.payment.internal.SettlementEntity;
import com.gym.payment.internal.SettlementRepository;
import com.gym.shared.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 收费用例编排（S2 切片）。
 *
 * <p>关键点：
 * <ul>
 *   <li>回调幂等：已支付订单重复回调不重复续期（REQ-B5-004）</li>
 *   <li>金额核对：不一致 → 订单异常 + 告警，**不变更会籍**（REQ-B5-004）</li>
 *   <li>支付成功 → 经 {@link MembershipFacade} 契约激活会籍/增加课包（跨模块只走契约）</li>
 * </ul>
 */
@Service
public class PaymentAppService implements PaymentFacade {

    private static final Logger log = LoggerFactory.getLogger(PaymentAppService.class);

    private final PaymentOrderRepository orderRepository;
    private final SettlementRepository settlementRepository;
    private final MembershipFacade membershipFacade;   // 跨模块：只依赖 api 契约
    private final AuditLogger auditLogger;

    public PaymentAppService(PaymentOrderRepository orderRepository,
                             SettlementRepository settlementRepository,
                             MembershipFacade membershipFacade,
                             AuditLogger auditLogger) {
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
        this.membershipFacade = membershipFacade;
        this.auditLogger = auditLogger;
    }

    /* ==================== 下单 ==================== */

    @Override
    @Transactional
    public PaymentOrderView createOrder(Long memberId, String bizType, BigDecimal amount, Integer times) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("订单金额必须大于 0");
        }
        String orderNo = "O" + System.currentTimeMillis();
        var order = new PaymentOrderEntity(orderNo, memberId, bizType, amount);
        order.setTimes(times);
        var saved = orderRepository.save(order);
        auditLogger.log("order.create", "payment_order", saved.getId(),
                "member=" + memberId + ",biz=" + bizType + ",amount=" + amount);
        return toView(saved);
    }

    @Override
    public PaymentOrderView getOrder(String orderNo) {
        return toView(require(orderNo));
    }

    @Override
    public List<PaymentOrderView> listOrders(String status) {
        List<PaymentOrderEntity> list = (status == null || status.isBlank())
                ? orderRepository.findAll()
                : orderRepository.findByStatus(status);
        return list.stream().map(PaymentAppService::toView).toList();
    }

    @Override
    public List<PaymentOrderView> listAbnormalOrders() {
        return listOrders("abnormal");
    }

    /* ==================== 支付与回调 ==================== */

    @Override
    @Transactional
    public PaymentOrderView payOrder(String orderNo, String outTradeNo) {
        var order = require(orderNo);
        return settle(order, order.getAmount(), outTradeNo, "payOrder");
    }

    @Override
    @Transactional
    public PaymentOrderView handleNotify(String orderNo, BigDecimal paidAmount, String outTradeNo) {
        var order = require(orderNo);

        // 幂等：已支付则直接返回，不重复续期
        if ("paid".equals(order.getStatus())) {
            log.info("[payment] 重复回调，忽略：orderNo={}", orderNo);
            auditLogger.log("order.notifyIdempotent", "payment_order", order.getId(), orderNo);
            return toView(order);
        }

        // 金额核对（REQ-B5-004）
        if (paidAmount == null || paidAmount.compareTo(order.getAmount()) != 0) {
            order.markAbnormal("支付金额与订单不一致", paidAmount);
            orderRepository.save(order);
            log.warn("[payment][ALERT] 金额不符：orderNo={}, 应付={}, 实付={}",
                    orderNo, order.getAmount(), paidAmount);
            auditLogger.log("order.abnormal", "payment_order", order.getId(),
                    "amountMismatch expected=" + order.getAmount() + " paid=" + paidAmount);
            return toView(order);
        }

        return settle(order, paidAmount, outTradeNo, "notify");
    }

    /** 支付成功受理：标记已支付 + 经契约激活会籍（幂等由调用方保证） */
    private PaymentOrderView settle(PaymentOrderEntity order, BigDecimal paidAmount,
                                    String outTradeNo, String source) {
        boolean first = order.markPaid(paidAmount, outTradeNo);
        orderRepository.save(order);
        if (!first) {
            return toView(order);
        }
        membershipFacade.activateMembership(order.getMemberId(), order.getBizType(),
                order.getTimes() == null ? 0 : order.getTimes());
        auditLogger.log("order.paid", "payment_order", order.getId(),
                source + " amount=" + paidAmount);
        return toView(order);
    }

    /* ==================== 对账 ==================== */

    @Override
    @Transactional
    public SettlementView generateSettlement(String period) {
        YearMonth ym = YearMonth.parse(period);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().atTime(23, 59, 59);

        var orders = orderRepository.findByCreatedAtBetween(from, to);
        BigDecimal total = orders.stream()
                .filter(o -> "paid".equals(o.getStatus()))
                .map(PaymentOrderEntity::getPaidAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int paidCount = (int) orders.stream().filter(o -> "paid".equals(o.getStatus())).count();
        int abnormalCount = (int) orders.stream().filter(o -> "abnormal".equals(o.getStatus())).count();

        var settlement = settlementRepository.findByPeriod(period)
                .map(s -> { s.update(total, paidCount, abnormalCount); return s; })
                .orElseGet(() -> new SettlementEntity(period, total, paidCount, abnormalCount));
        var saved = settlementRepository.save(settlement);
        auditLogger.log("settlement.generate", "settlement", saved.getId(),
                period + " total=" + total + " paid=" + paidCount + " abnormal=" + abnormalCount);
        return toView(saved);
    }

    @Override
    public List<SettlementView> listSettlements() {
        return settlementRepository.findAllByOrderByPeriodDesc().stream()
                .map(PaymentAppService::toView).toList();
    }

    /* ==================== 内部工具 ==================== */

    private PaymentOrderEntity require(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderNo));
    }

    private static PaymentOrderView toView(PaymentOrderEntity o) {
        return new PaymentOrderView(o.getId(), o.getOrderNo(), o.getMemberId(), o.getBizType(),
                o.getTimes(), o.getAmount(), o.getPaidAmount(), o.getStatus(),
                o.getAbnormalReason(), o.getCreatedAt(), o.getPaidAt());
    }

    private static SettlementView toView(SettlementEntity s) {
        return new SettlementView(s.getId(), s.getPeriod(), s.getTotalAmount(),
                s.getOrderCount(), s.getAbnormalCount(), s.getStatus());
    }

    /** 供控制器展示"今天"的默认对账期间 */
    public static String currentPeriod() {
        return YearMonth.from(LocalDate.now()).toString();
    }
}
