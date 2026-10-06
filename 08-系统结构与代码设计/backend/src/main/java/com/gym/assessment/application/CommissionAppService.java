package com.gym.assessment.application;

import com.gym.assessment.api.CommissionFacade;
import com.gym.assessment.api.CommissionViews;
import com.gym.booking.api.BookingFacade;
import com.gym.shared.rule.RuleEngine;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 私教提成核算（SYS-R10）。
 *
 * <p>口径与 Node 参考实现一致：按课时数计（calcType=by_times），
 * 提成 = 课时数 × 单节基准（40 元）× 提成比例（rule_config 中可配置）。
 */
@Service
public class CommissionAppService implements CommissionFacade {

    /** 单节课时费基准（元）；如需调整可后续外置到 rule_config */
    public static final int UNIT_PRICE = 40;

    private final BookingFacade bookingFacade;
    private final RuleEngine ruleEngine;

    public CommissionAppService(BookingFacade bookingFacade, RuleEngine ruleEngine) {
        this.bookingFacade = bookingFacade;
        this.ruleEngine = ruleEngine;
    }

    @Override
    public CommissionViews.CommissionReport monthly(int days) {
        int window = days <= 0 ? 30 : days;
        double rate = ruleEngine.commissionRate();
        List<CommissionViews.CommissionRow> rows = new ArrayList<>();
        double total = 0;

        for (BookingFacade.CoachTimes ct : bookingFacade.checkedInTimesByCoach(window)) {
            double commission = Math.round(ct.times() * UNIT_PRICE * rate * 100) / 100.0;
            total += commission;
            rows.add(new CommissionViews.CommissionRow(ct.coachId(), ct.times(), UNIT_PRICE, rate, commission));
        }
        rows.sort((a, b) -> Double.compare(b.commission(), a.commission()));
        return new CommissionViews.CommissionReport(YearMonth.now().toString(), window, rate,
                Math.round(total * 100) / 100.0, rows);
    }
}
