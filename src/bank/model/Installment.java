package bank.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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

    // The fee added when an installment is not paid on time. The fee is added
    // to amountDue, so paying it off works exactly like paying the installment.
    // Both fields are missing (null / false) in files saved before late fees existed.
    private BigDecimal lateFee;
    private boolean lateFeeCharged;

    // Interest on a late amount, added to amountDue just like the late fee.
    // It is worked out from a "checkpoint": the amount still unpaid on a day
    // (interestBase on interestFrom), plus the interest charged up to then
    // (interestAtCheckpoint). A payment starts a new checkpoint.
    // All four are missing (null) until the installment first goes late.
    private BigDecimal lateInterest;
    private LocalDate interestFrom;
    private BigDecimal interestBase;
    private BigDecimal interestAtCheckpoint;

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

    // the late fee that was added to this installment (zero if there is none)
    public BigDecimal getLateFee() {
        if (lateFee == null) {
            return BigDecimal.ZERO;
        }
        return lateFee;
    }

    // the interest added to this installment because it is late (zero if there is none)
    public BigDecimal getLateInterest() {
        if (lateInterest == null) {
            return BigDecimal.ZERO;
        }
        return lateInterest;
    }

    // what the installment was before any late fee or late interest was added
    public BigDecimal getBaseAmount() {
        return amountDue.subtract(getLateFee()).subtract(getLateInterest());
    }

    // Interest on a late amount, compounded monthly: base x ((1 + rate / 12) ^ (days / 30) - 1).
    // rate is the yearly rate (0.20 for 20%). A month counts as 30 days, and part
    // months count. Both the real charge and the example shown to customers use this.
    public static BigDecimal lateInterestFor(BigDecimal base, BigDecimal yearlyRate, long days) {
        if (days <= 0 || base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }

        double monthlyFactor = 1.0 + yearlyRate.doubleValue() / 12.0;
        double growth = Math.pow(monthlyFactor, days / 30.0) - 1.0;

        return base.multiply(BigDecimal.valueOf(growth)).setScale(2, RoundingMode.HALF_UP);
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

        // From the day of a payment, interest grows only on what is still unpaid
        if (interestFrom != null && status != InstallmentStatus.PAID) {
            interestFrom = paymentDate;
            interestBase = getRemainingAmount();
            interestAtCheckpoint = getLateInterest();
        }

        return applied;
    }

    // If this installment is still unpaid and its due date is in the past,
    // mark it OVERDUE, and add the late fee (a share of the installment, set by
    // lateFeeRate, e.g. 0.05 for 5%). The fee is added only once, the first time
    // the installment is seen overdue. Called every so often so the status stays up to date.
    public void refreshOverdueStatus(LocalDate today, BigDecimal lateFeeRate) {
        if (status == InstallmentStatus.PENDING && dueDate.isBefore(today)) {
            status = InstallmentStatus.OVERDUE;
        }

        if (status == InstallmentStatus.OVERDUE && !lateFeeCharged) {
            BigDecimal fee = amountDue.multiply(lateFeeRate).setScale(2, RoundingMode.HALF_UP);

            lateFee = fee;
            amountDue = amountDue.add(fee);
            lateFeeCharged = true;
        }
    }

    // Adds the interest that has built up on this unpaid installment between
    // startFrom and asOf (startFrom is the due date, or for a defaulted loan the
    // day it defaulted). Like the late fee, the interest goes into amountDue.
    // Safe to call as often as we like: it works out the total from the checkpoint
    // each time, and it never takes interest away.
    public void accrueInterest(LocalDate asOf, LocalDate startFrom, BigDecimal yearlyRate) {
        if (status == InstallmentStatus.PAID || startFrom == null) {
            return;
        }

        if (interestFrom == null) {
            interestFrom = startFrom;
            interestBase = getRemainingAmount();
            interestAtCheckpoint = getLateInterest();
        }

        long days = ChronoUnit.DAYS.between(interestFrom, asOf);
        BigDecimal newTotal = interestAtCheckpoint.add(lateInterestFor(interestBase, yearlyRate, days));

        if (newTotal.compareTo(getLateInterest()) <= 0) {
            return;
        }

        amountDue = amountDue.add(newTotal.subtract(getLateInterest()));
        lateInterest = newTotal;
    }
}
