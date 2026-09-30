package bank.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
    // a real repayment would work. Whatever is left over after everything
    // is paid off (an overpayment) is returned so Bank/Main can tell the user.
    public BigDecimal applyPayment(BigDecimal amount, LocalDate paymentDate) {
        BigDecimal amountLeft = amount;

        for (Installment installment : installments) {
            if (amountLeft.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            if (installment.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal applied = installment.applyPayment(amountLeft, paymentDate);
                amountLeft = amountLeft.subtract(applied);
            }
        }

        if (isFullyPaid()) {
            status = LoanStatus.PAID_OFF;
        }

        BigDecimal amountApplied = amount.subtract(amountLeft);
        return amountApplied;
    }
}
