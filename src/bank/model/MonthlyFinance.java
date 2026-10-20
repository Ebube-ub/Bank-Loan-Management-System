package bank.model;

import java.math.BigDecimal;
import java.time.YearMonth;

// One month of the finance table: money earned (interest, late fees, interest on late amounts and prepayment charges),
// money lost, and the profit.
public class MonthlyFinance {

    private final YearMonth month;
    private final BigDecimal interestCollected;
    private final BigDecimal lateFees;
    private final BigDecimal lateInterest;
    private final BigDecimal prepaymentFees;
    private final BigDecimal badDebt;

    public MonthlyFinance(YearMonth month, BigDecimal interestCollected, BigDecimal lateFees, BigDecimal lateInterest,
            BigDecimal prepaymentFees,
            BigDecimal badDebt) {
        this.month = month;
        this.interestCollected = interestCollected;
        this.lateFees = lateFees;
        this.lateInterest = lateInterest;
        this.prepaymentFees = prepaymentFees;
        this.badDebt = badDebt;
    }

    public YearMonth getMonth() {
        return month;
    }

    public BigDecimal getInterestCollected() {
        return interestCollected;
    }

    public BigDecimal getLateFees() {
        return lateFees;
    }

    // interest charged because installments were paid late
    public BigDecimal getLateInterest() {
        return lateInterest;
    }

    public BigDecimal getPrepaymentFees() {
        return prepaymentFees;
    }

    // money earned: interest, late fees and prepayment charges
    public BigDecimal getEarned() {
        return interestCollected.add(lateFees).add(lateInterest).add(prepaymentFees);
    }

    // money lost: the unpaid money lent on loans that defaulted
    public BigDecimal getBadDebt() {
        return badDebt;
    }

    public BigDecimal getProfit() {
        return getEarned().subtract(badDebt);
    }
}
