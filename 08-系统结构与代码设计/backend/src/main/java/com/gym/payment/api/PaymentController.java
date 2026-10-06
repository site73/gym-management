package com.gym.payment.api;

import com.gym.identity.api.AuthContext;
import com.gym.payment.application.PaymentAppService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收费 REST 入口（S2 切片）。
 *
 * <p>权限：除支付回调外，其余均为门店后台操作（会员端不出现收费入口）。
 * 支付回调由支付平台直接调用，走验签而非前端令牌（已在拦截器中放行）。
 */
@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentFacade paymentFacade;

    public PaymentController(PaymentFacade paymentFacade) {
        this.paymentFacade = paymentFacade;
    }

    public record CreateOrderRequest(Long memberId, String bizType, BigDecimal amount, Integer times) {}
    public record NotifyRequest(BigDecimal paidAmount, String outTradeNo) {}

    /** 创建订单（REQ-B5-001，门店后台） */
    @PostMapping("/orders")
    public PaymentOrderView create(@RequestBody CreateOrderRequest req) {
        AuthContext.requireStaff();
        return paymentFacade.createOrder(req.memberId(), req.bizType(), req.amount(), req.times());
    }

    /** 订单列表（门店后台） */
    @GetMapping("/orders")
    public List<PaymentOrderView> list(@RequestParam(required = false) String status) {
        AuthContext.requireStaff();
        return paymentFacade.listOrders(status);
    }

    /** 异常订单（门店后台） */
    @GetMapping("/orders/abnormal")
    public List<PaymentOrderView> abnormal() {
        AuthContext.requireStaff();
        return paymentFacade.listAbnormalOrders();
    }

    /** 订单详情（门店后台） */
    @GetMapping("/orders/{orderNo}")
    public PaymentOrderView get(@PathVariable String orderNo) {
        AuthContext.requireStaff();
        return paymentFacade.getOrder(orderNo);
    }

    /** 发起支付并受理（Mock 微信支付 EXT2，门店后台） */
    @PostMapping("/orders/{orderNo}/pay")
    public PaymentOrderView pay(@PathVariable String orderNo) {
        AuthContext.requireStaff();
        return paymentFacade.payOrder(orderNo, "MOCK-" + orderNo);
    }

    /**
     * 支付结果回调（EXT3，无需令牌）。
     * 金额不符 → 409 + 订单异常（不变更会籍）；重复回调 → 幂等 200。
     */
    @PostMapping("/pay/notify/{orderNo}")
    public ResponseEntity<PaymentOrderView> notify(@PathVariable String orderNo,
                                                   @RequestBody NotifyRequest req) {
        var view = paymentFacade.handleNotify(orderNo, req.paidAmount(), req.outTradeNo());
        return "abnormal".equals(view.status())
                ? ResponseEntity.status(HttpStatus.CONFLICT).body(view)
                : ResponseEntity.ok(view);
    }

    /** 生成月度对账单（REQ-B5-003，门店后台） */
    @PostMapping("/settlements")
    public SettlementView generate(@RequestParam(required = false) String period) {
        AuthContext.requireStaff();
        return paymentFacade.generateSettlement(
                (period == null || period.isBlank()) ? PaymentAppService.currentPeriod() : period);
    }

    /** 对账单列表（门店后台） */
    @GetMapping("/settlements")
    public List<SettlementView> settlements() {
        AuthContext.requireStaff();
        return paymentFacade.listSettlements();
    }
}
