package bank.model;

import java.math.BigDecimal;

// What happened to the money in one payment:
//  - applied:        went towards the installments
//  - prepaymentFee:  taken as the prepayment charge (see Bank.PREPAYMENT_FEE_RATE)
//  - overpayment:    not used, because the loan was already fully paid
// applied + prepaymentFee + overpayment is the amount that was offered.
public class PaymentResult {

    private final BigDecimal applied;
    private final BigDecimal prepaymentFee;
    private final BigDecimal overpayment;

    public PaymentResult(BigDecimal applied, BigDecimal prepaymentFee, BigDecimal overpayment) {
        this.applied = applied;
        this.prepaymentFee = prepaymentFee;
        this.overpayment = overpayment;
    }

    public BigDecimal getApplied() {
        return applied;
    }

    public BigDecimal getPrepaymentFee() {
        return prepaymentFee;
    }

    public BigDecimal getOverpayment() {
        return overpayment;
    }
}
