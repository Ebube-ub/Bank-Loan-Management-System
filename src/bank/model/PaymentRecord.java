package bank.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

// One payment that was made on a loan, and who entered it
// (the customer, the admin recording it for them, or the console).
public class PaymentRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String loanId;
    private BigDecimal amountApplied;
    private LocalDate date;
    private String recordedBy;
    private boolean recordedByAdmin;

    // the prepayment charge taken out of this payment (missing in files saved before it existed)
    private BigDecimal prepaymentFee;

    public PaymentRecord(String loanId, BigDecimal amountApplied, BigDecimal prepaymentFee, LocalDate date,
            String recordedBy, boolean recordedByAdmin) {
        this.loanId = loanId;
        this.amountApplied = amountApplied;
        this.prepaymentFee = prepaymentFee;
        this.date = date;
        this.recordedBy = recordedBy;
        this.recordedByAdmin = recordedByAdmin;
    }

    public String getLoanId() {
        return loanId;
    }

    // the part of the payment that went towards the installments
    public BigDecimal getAmountApplied() {
        return amountApplied;
    }

    public BigDecimal getPrepaymentFee() {
        if (prepaymentFee == null) {
            return BigDecimal.ZERO;
        }
        return prepaymentFee;
    }

    // everything the customer paid: the installments plus the prepayment charge
    public BigDecimal getTotalPaid() {
        return amountApplied.add(getPrepaymentFee());
    }

    public LocalDate getDate() {
        return date;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public boolean isRecordedByAdmin() {
        return recordedByAdmin;
    }
}
