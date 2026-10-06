package com.gym.payment.api;

import com.gym.payment.application.PaymentAppService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收费 REST 入口（S2 切片）。
 *
 * <p>只依赖模块对外契约 {@link PaymentFacade}，不直接访问仓储。
 * 回调语义对齐《外部接口设计说明》EXT3：验签（Mock 环境省略）→ 幂等 → 金额核对 → 告警。
 */
@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentFacade paymentFacade;

    public PaymentController(PaymentFacade paymentFacade) {
        this.paymentFacade = paymentFacade;
    }

    public record CreateOrderRequest(@NotNull Long memberId, @NotNull String bizType,
                                     @NotNull BigDecimal amount, Integer times) {}
    public record NotifyRequest(BigDecimal paidAmount, String outTradeNo) {}

    /** 创建订单（REQ-B5-001） */
    @PostMapping("/orders")
    public PaymentOrderView create(@RequestBody CreateOrderRequest req) {
        return paymentFacade.createOrder(req.memberId(), req.bizType(), req.amount(), req.times());
    }

    /** 订单列表（可按状态过滤：pending / paid / abnormal / refunded） */
    @GetMapping("/orders")
    public List<PaymentOrderView> list(@RequestParam(required = false) String status) {
        return paymentFacade.listOrders(status);
    }

    /** 订单详情 */
    @GetMapping("/orders/{orderNo}")
    public PaymentOrderView get(@PathVariable String orderNo) {
        return paymentFacade.getOrder(orderNo);
    }

    /** 发起支付并受理成功（Mock 微信支付 EXT2） */
    @PostMapping("/orders/{orderNo}/pay")
    public PaymentOrderView pay(@PathVariable String orderNo) {
        return paymentFacade.payOrder(orderNo, "MOCK-" + orderNo);
    }

    /**
     * 支付结果回调（EXT3）。
     * 金额不符 → 返回 409 + 订单异常（不变更会籍）；重复回调 → 幂等返回 200。
     */
    @PostMapping("/pay/notify/{orderNo}")
    public ResponseEntity<PaymentOrderView> notify(@PathVariable String orderNo,
                                                   @RequestBody NotifyRequest req) {
        var view = paymentFacade.handleNotify(orderNo, req.paidAmount(), req.outTradeNo());
        return "abnormal".equals(view.status())
                ? ResponseEntity.status(HttpStatus.CONFLICT).body(view)
                : ResponseEntity.ok(view);
    }

    /** 异常订单（告警/对账用） */
    @GetMapping("/orders/abnormal")
    public List<PaymentOrderView> abnormal() {
        return paymentFacade.listAbnormalOrders();
    }

    /** 生成月度对账单（REQ-B5-003）；不传期间则默认当月 */
    @PostMapping("/settlements")
    public SettlementView generate(@RequestParam(required = false) String period) {
        return paymentFacade.generateSettlement(
                (period == null || period.isBlank()) ? PaymentAppService.currentPeriod() : period);
    }

    /** 对账单列表 */
    @GetMapping("/settlements")
    public List<SettlementView> settlements() {
        return paymentFacade.listSettlements();
    }
}
