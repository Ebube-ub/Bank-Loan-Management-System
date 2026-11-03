package bank.model;

import java.math.BigDecimal;
import java.util.List;

// What the admin finance page shows: the month-by-month table and the totals.
public class FinanceReport {

    private final List<MonthlyFinance> months;

    public FinanceReport(List<MonthlyFinance> months) {
        this.months = months;
    }

    public List<MonthlyFinance> getMonths() {
        return months;
    }

    public BigDecimal getInterest() {
        BigDecimal total = BigDecimal.ZERO;
        for (MonthlyFinance month : months) {
            total = total.add(month.getInterestCollected());
        }
        return total;
    }

    public BigDecimal getLateFees() {
        BigDecimal total = BigDecimal.ZERO;
        for (MonthlyFinance month : months) {
            total = total.add(month.getLateFees());
        }
        return total;
    }

    public BigDecimal getLateInterest() {
        BigDecimal total = BigDecimal.ZERO;
        for (MonthlyFinance month : months) {
            total = total.add(month.getLateInterest());
        }
        return total;
    }

    public BigDecimal getPrepaymentFees() {
        BigDecimal total = BigDecimal.ZERO;
        for (MonthlyFinance month : months) {
            total = total.add(month.getPrepaymentFees());
        }
        return total;
    }

    // money earned: interest, late fees, interest on late amounts and prepayment charges
    public BigDecimal getRevenue() {
        return getInterest().add(getLateFees()).add(getLateInterest()).add(getPrepaymentFees());
    }

    // money lost
    public BigDecimal getBadDebt() {
        BigDecimal total = BigDecimal.ZERO;
        for (MonthlyFinance month : months) {
            total = total.add(month.getBadDebt());
        }
        return total;
    }

    public BigDecimal getProfit() {
        return getRevenue().subtract(getBadDebt());
    }
}
