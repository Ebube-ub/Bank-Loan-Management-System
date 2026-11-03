package bank.model;

import java.math.BigDecimal;
import java.util.List;

// The system's opinion on a loan request: the advice, the reasons behind it,
// and the largest amount this customer could be lent right now.
public class LoanAssessment {

    private final Advice advice;
    private final List<String> reasons;
    private final BigDecimal maxAmount;
    private final CreditScore creditScore;

    public LoanAssessment(Advice advice, List<String> reasons, BigDecimal maxAmount, CreditScore creditScore) {
        this.advice = advice;
        this.reasons = reasons;
        this.maxAmount = maxAmount;
        this.creditScore = creditScore;
    }

    public Advice getAdvice() {
        return advice;
    }

    // why the advice is what it is (for DECLINE: which rules were broken)
    public List<String> getReasons() {
        return reasons;
    }

    // the most the customer can borrow in one new loan right now (never below zero)
    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public CreditScore getCreditScore() {
        return creditScore;
    }

    public boolean isDeclined() {
        return advice == Advice.DECLINE;
    }
}
