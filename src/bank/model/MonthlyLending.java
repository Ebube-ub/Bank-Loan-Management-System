package bank.model;

import java.math.BigDecimal;
import java.time.YearMonth;

// One month of the "lent vs earned" chart on the Reports screen:
// the money lent out that month and the money earned that month.
public class MonthlyLending {

    private final YearMonth month;
    private final BigDecimal lent;
    private final BigDecimal earned;

    public MonthlyLending(YearMonth month, BigDecimal lent, BigDecimal earned) {
        this.month = month;
        this.lent = lent;
        this.earned = earned;
    }

    public YearMonth getMonth() {
        return month;
    }

    // the principal of the loans handed out in this month
    public BigDecimal getLent() {
        return lent;
    }

    // interest, late fees and prepayment charges paid in this month (same figure as the Finance screen)
    public BigDecimal getEarned() {
        return earned;
    }
}
