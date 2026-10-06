package com.gym.payment.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收费模块对外契约（唯一入口）。
 *
 * <p>覆盖 S2 切片：下单、支付、支付回调（幂等 + 金额核对）、对账。
 * 其他模块不得直接读写 payment_order / settlement 表。
 */
public interface PaymentFacade {

    /** 创建订单（REQ-B5-001） */
    PaymentOrderView createOrder(Long memberId, String bizType, BigDecimal amount, Integer times);

    /** 查询订单 */
    PaymentOrderView getOrder(String orderNo);

    /** 按状态查询订单（空则查全部） */
    List<PaymentOrderView> listOrders(String status);

    /** 模拟/对接支付成功（EXT2 后的受理） */
    PaymentOrderView payOrder(String orderNo, String outTradeNo);

    /**
     * 支付结果回调（EXT3）。
     *
     * <p>幂等：同一订单重复回调不重复续期；
     * 金额不符则标记异常并告警，且**不变更会籍**（REQ-B5-004）。
     */
    PaymentOrderView handleNotify(String orderNo, BigDecimal paidAmount, String outTradeNo);

    /** 生成月度对账单（REQ-B5-003 / A10） */
    SettlementView generateSettlement(String period);

    /** 对账单列表 */
    List<SettlementView> listSettlements();

    /** 异常订单列表（对账与告警用） */
    List<PaymentOrderView> listAbnormalOrders();
}
