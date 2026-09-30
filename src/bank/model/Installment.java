package bank.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

// One monthly payment that is part of a loan's repayment schedule.
//
// Note: we use BigDecimal instead of double for money. With double, adding
// up lots of small decimal payments can leave a tiny leftover like
// 0.0000000001, which would stop an installment from ever reaching PAID.
// BigDecimal does exact decimal math so that doesn't happen.
public class Installment implements Serializable {

    private static final long serialVersionUID = 1L;

    private LocalDate dueDate;
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private LocalDate paidDate;
    private InstallmentStatus status;

    public Installment(LocalDate dueDate, BigDecimal amountDue) {
        this.dueDate = dueDate;
        this.amountDue = amountDue;
        this.amountPaid = BigDecimal.ZERO;
        this.paidDate = null;
        this.status = InstallmentStatus.PENDING;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getAmountDue() {
        return amountDue;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public LocalDate getPaidDate() {
        return paidDate;
    }

    public InstallmentStatus getStatus() {
        return status;
    }

    public boolean isPaid() {
        return status == InstallmentStatus.PAID;
    }

    public BigDecimal getRemainingAmount() {
        BigDecimal remaining = amountDue.subtract(amountPaid);

        // just in case of an overpayment, don't return a negative number
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return remaining;
    }

    // Puts money towards this installment, but never more than what is
    // actually owed. Returns how much of "amount" was actually used, so
    // the caller knows if some of it was left over (overpayment).
    public BigDecimal applyPayment(BigDecimal amount, LocalDate paymentDate) {
        BigDecimal remaining = getRemainingAmount();

        BigDecimal applied = amount;
        if (applied.compareTo(remaining) > 0) {
            applied = remaining;
        }

        amountPaid = amountPaid.add(applied);

        if (amountPaid.compareTo(amountDue) >= 0) {
            amountPaid = amountDue;
            status = InstallmentStatus.PAID;
            paidDate = paymentDate;
        }

        return applied;
    }

    // If this installment is still unpaid and its due date is in the past,
    // mark it OVERDUE. Called every so often so the status stays up to date.
    public void refreshOverdueStatus(LocalDate today) {
        if (status == InstallmentStatus.PENDING && dueDate.isBefore(today)) {
            status = InstallmentStatus.OVERDUE;
        }
    }
}
