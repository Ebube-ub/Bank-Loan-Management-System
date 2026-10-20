package bank.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class Loan implements Serializable {

    private static final long serialVersionUID = 1L;

    private String loanId;
    private Customer customer;
    private BigDecimal principal;
    private BigDecimal interestRate; // yearly rate, e.g. 0.12 means 12%
    private int termMonths;
    private LocalDate disbursementDate;
    private LoanStatus status;
    private List<Installment> installments;

    // the day the loan was marked DEFAULTED (null for loans saved before this existed)
    private LocalDate defaultedDate;

    public Loan(
            String loanId,
            Customer customer,
            BigDecimal principal,
            BigDecimal interestRate,
            int termMonths,
            LocalDate disbursementDate
    ) {
        this.loanId = loanId;
        this.customer = customer;
        this.principal = principal;
        this.interestRate = interestRate;
        this.termMonths = termMonths;
        this.disbursementDate = disbursementDate;
        this.status = LoanStatus.ACTIVE;
        this.installments = new ArrayList<>();
    }

    public String getLoanId() {
        return loanId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public LocalDate getDisbursementDate() {
        return disbursementDate;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    // marks the loan DEFAULTED and remembers the day it happened
    // (the finance report needs that day to know which month the loss belongs to)
    public void markDefaulted(LocalDate date) {
        this.status = LoanStatus.DEFAULTED;
        this.defaultedDate = date;
    }

    // For loans saved before defaultedDate existed, fall back to the due date
    // of the last overdue installment, which is roughly when it went bad.
    public LocalDate getDefaultedDate() {
        if (defaultedDate != null) {
            return defaultedDate;
        }

        LocalDate latestOverdue = null;
        for (Installment installment : installments) {
            if (installment.getStatus() == InstallmentStatus.OVERDUE) {
                if (latestOverdue == null || installment.getDueDate().isAfter(latestOverdue)) {
                    latestOverdue = installment.getDueDate();
                }
            }
        }
        return latestOverdue;
    }

    public List<Installment> getInstallments() {
        return installments;
    }

    public void addInstallment(Installment installment) {
        installments.add(installment);
    }

    // Simple interest for the whole loan: principal * rate * months / 12
    public BigDecimal getTotalInterest() {
        return principal
                .multiply(interestRate)
                .multiply(BigDecimal.valueOf(termMonths))
                .divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalRepayable() {
        return principal.add(getTotalInterest()).setScale(2, RoundingMode.HALF_UP);
    }

    // roughly how much interest is inside one installment (used for the
    // "total interest earned" report)
    public BigDecimal getInterestPerInstallment() {
        return getTotalInterest().divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
    }

    // all the late fees added to this loan's installments so far
    public BigDecimal getTotalLateFees() {
        BigDecimal total = BigDecimal.ZERO;

        for (Installment installment : installments) {
            total = total.add(installment.getLateFee());
        }

        return total;
    }

    // all the interest added to this loan's installments because they were late
    public BigDecimal getTotalLateInterest() {
        BigDecimal total = BigDecimal.ZERO;

        for (Installment installment : installments) {
            total = total.add(installment.getLateInterest());
        }

        return total;
    }

    // Adds the interest that has built up on late amounts, up to asOf.
    //  - an active loan: on each overdue installment, from its due date
    //  - a defaulted loan: on everything still unpaid. Overdue installments carry on
    //    from their due date, and the rest start from the day the loan defaulted.
    public void accrueLateInterest(LocalDate asOf) {
        for (Installment installment : installments) {

            LocalDate startFrom;

            if (installment.getStatus() == InstallmentStatus.OVERDUE) {
                startFrom = installment.getDueDate();
            } else if (status == LoanStatus.DEFAULTED && !installment.isPaid()) {
                startFrom = getDefaultedDate();
            } else {
                continue;
            }

            installment.accrueInterest(asOf, startFrom, interestRate);
        }
    }

    // One line for each late amount on this loan, as of today: an overdue installment,
    // and, for a defaulted loan, every unpaid installment (they are all due now).
    // Each line is split into the installment, the late fee and the interest.
    public List<OverdueEntry> lateEntries(LocalDate today) {
        List<OverdueEntry> result = new ArrayList<>();
        boolean defaulted = status == LoanStatus.DEFAULTED;

        for (Installment installment : installments) {
            boolean overdue = installment.getStatus() == InstallmentStatus.OVERDUE;
            boolean dueBecauseDefaulted = defaulted && !installment.isPaid() && !overdue;

            if (!overdue && !dueBecauseDefaulted) {
                continue;
            }

            long days = overdue ? Math.max(0, ChronoUnit.DAYS.between(installment.getDueDate(), today)) : 0;

            result.add(new OverdueEntry(
                    customer.getCustomerId(), customer.getName(), loanId,
                    installment.getDueDate(), days,
                    installment.getBaseAmount(), installment.getLateFee(), installment.getLateInterest(),
                    installment.getAmountPaid(), installment.getRemainingAmount(), dueBecauseDefaulted));
        }

        return result;
    }

    public BigDecimal getOutstandingBalance() {
        BigDecimal outstanding = BigDecimal.ZERO;

        for (Installment installment : installments) {
            outstanding = outstanding.add(installment.getRemainingAmount());
        }

        return outstanding;
    }

    public boolean isFullyPaid() {
        for (Installment installment : installments) {
            if (!installment.isPaid()) {
                return false;
            }
        }
        return true;
    }

    public boolean hasOverdueInstallments() {
        for (Installment installment : installments) {
            if (installment.getStatus() == InstallmentStatus.OVERDUE) {
                return true;
            }
        }
        return false;
    }

    // Counts how many OVERDUE installments are in a row. Because payments
    // always go towards the earliest unpaid installment first, the overdue
    // ones always end up next to each other, so a simple counter works fine.
    public int countConsecutiveOverdue() {
        int longestStreak = 0;
        int currentStreak = 0;

        for (Installment installment : installments) {
            if (installment.getStatus() == InstallmentStatus.OVERDUE) {
                currentStreak++;
                if (currentStreak > longestStreak) {
                    longestStreak = currentStreak;
                }
            } else if (installment.getStatus() == InstallmentStatus.PAID) {
                currentStreak = 0;
            }
        }

        return longestStreak;
    }

    // Puts a payment towards the earliest unpaid installment(s) first, like
    // a real repayment would work.
    //
    // Paying ahead has a charge (prepaymentRate, e.g. 0.02 for 2%): money that goes
    // to an installment that is not due yet is charged that share, and the charge is
    // taken out of the payment. The earliest unpaid installment is never charged, so
    // paying your next installment a few days early costs nothing.
    //
    // Whatever is left over after everything is paid off (an overpayment) is
    // returned in the result so Bank/Main can tell the user.
    public PaymentResult applyPayment(BigDecimal amount, LocalDate paymentDate, BigDecimal prepaymentRate) {
        BigDecimal amountLeft = amount;
        BigDecimal applied = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;

        Installment next = earliestUnpaid();

        for (Installment installment : installments) {
            if (amountLeft.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal remaining = installment.getRemainingAmount();
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (!isPaidAhead(installment, next, paymentDate)) {
                BigDecimal used = installment.applyPayment(amountLeft, paymentDate);
                applied = applied.add(used);
                amountLeft = amountLeft.subtract(used);
                continue;
            }

            BigDecimal feeToClear = prepaymentFee(remaining, prepaymentRate);

            if (amountLeft.compareTo(remaining.add(feeToClear)) >= 0) {
                // enough money to pay this installment off, with its charge
                installment.applyPayment(remaining, paymentDate);
                applied = applied.add(remaining);
                fee = fee.add(feeToClear);
                amountLeft = amountLeft.subtract(remaining).subtract(feeToClear);
            } else {
                // only part of it: split the money between the installment and the charge
                BigDecimal part = amountLeft.divide(BigDecimal.ONE.add(prepaymentRate), 2, RoundingMode.DOWN);
                installment.applyPayment(part, paymentDate);
                applied = applied.add(part);
                fee = fee.add(amountLeft.subtract(part));
                amountLeft = BigDecimal.ZERO;
            }
        }

        if (isFullyPaid()) {
            status = LoanStatus.PAID_OFF;
        }

        return new PaymentResult(applied, fee, amountLeft);
    }

    // The charge for paying off the whole loan on this date (zero if nothing is paid ahead).
    public BigDecimal payoffFee(LocalDate paymentDate, BigDecimal prepaymentRate) {
        BigDecimal fee = BigDecimal.ZERO;
        Installment next = earliestUnpaid();

        for (Installment installment : installments) {
            if (installment.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0
                    && isPaidAhead(installment, next, paymentDate)) {
                fee = fee.add(prepaymentFee(installment.getRemainingAmount(), prepaymentRate));
            }
        }

        return fee;
    }

    // what it costs to pay everything off on this date: what is owed, plus the charge
    public BigDecimal payoffAmount(LocalDate paymentDate, BigDecimal prepaymentRate) {
        return getOutstandingBalance().add(payoffFee(paymentDate, prepaymentRate));
    }

    // the earliest installment that still has something to pay (null if none)
    private Installment earliestUnpaid() {
        for (Installment installment : installments) {
            if (installment.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
                return installment;
            }
        }
        return null;
    }

    // money paid to this installment counts as paid ahead if it is not due yet
    // and it is not the next installment in line
    private boolean isPaidAhead(Installment installment, Installment next, LocalDate paymentDate) {
        if (status == LoanStatus.DEFAULTED) {
            return false; // a defaulted loan is all due now
        }

        return installment != next && installment.getDueDate().isAfter(paymentDate);
    }

    private BigDecimal prepaymentFee(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
