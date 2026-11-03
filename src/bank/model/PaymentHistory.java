package bank.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// The plain counts about how a customer has paid so far. The application
// summary and the early-warning list both read from here, so they count things
// the same way the credit score does (CreditScorer).
public class PaymentHistory {

    private final int onTime;          // paid on or before the due date
    private final int late;            // paid after the due date
    private final int paidOnDueDate;   // paid exactly on the due date (the "last day")
    private final int overdueNow;      // unpaid and past the due date
    private final int paidOffLoans;
    private final int defaultedLoans;
    private final int activeLoans;
    private final BigDecimal activePrincipal;

    private PaymentHistory(int onTime, int late, int paidOnDueDate, int overdueNow, int paidOffLoans,
            int defaultedLoans, int activeLoans, BigDecimal activePrincipal) {
        this.onTime = onTime;
        this.late = late;
        this.paidOnDueDate = paidOnDueDate;
        this.overdueNow = overdueNow;
        this.paidOffLoans = paidOffLoans;
        this.defaultedLoans = defaultedLoans;
        this.activeLoans = activeLoans;
        this.activePrincipal = activePrincipal;
    }

    public static PaymentHistory of(Customer customer, LocalDate asOf) {
        int onTime = 0;
        int late = 0;
        int paidOnDueDate = 0;
        int overdue = 0;
        int paidOff = 0;
        int defaulted = 0;
        int active = 0;
        BigDecimal activePrincipal = BigDecimal.ZERO;

        for (Loan loan : customer.getLoans()) {

            if (loan.getStatus() == LoanStatus.PAID_OFF) {
                paidOff++;
            } else if (loan.getStatus() == LoanStatus.DEFAULTED) {
                defaulted++;
            } else {
                active++;
                activePrincipal = activePrincipal.add(loan.getPrincipal());
            }

            for (Installment installment : loan.getInstallments()) {

                if (installment.getStatus() == InstallmentStatus.PAID) {
                    LocalDate paidDate = installment.getPaidDate();

                    if (paidDate != null && paidDate.isAfter(installment.getDueDate())) {
                        late++;
                    } else {
                        onTime++;
                        if (paidDate != null && paidDate.isEqual(installment.getDueDate())) {
                            paidOnDueDate++;
                        }
                    }
                } else if (installment.getStatus() == InstallmentStatus.OVERDUE
                        || installment.getDueDate().isBefore(asOf)) {
                    overdue++;
                }
            }
        }

        return new PaymentHistory(onTime, late, paidOnDueDate, overdue, paidOff, defaulted, active, activePrincipal);
    }

    public int getOnTime() {
        return onTime;
    }

    public int getLate() {
        return late;
    }

    public int getPaidOnDueDate() {
        return paidOnDueDate;
    }

    // every installment paid so far, on time or late
    public int getPaidCount() {
        return onTime + late;
    }

    public int getOverdueNow() {
        return overdueNow;
    }

    public int getPaidOffLoans() {
        return paidOffLoans;
    }

    public int getDefaultedLoans() {
        return defaultedLoans;
    }

    public int getActiveLoans() {
        return activeLoans;
    }

    public BigDecimal getActivePrincipal() {
        return activePrincipal;
    }

    // how much of the borrowing limit the active loans use, as a whole percent
    public int getPercentOfLimitInUse() {
        return activePrincipal
                .multiply(BigDecimal.valueOf(100))
                .divide(Bank.MAX_TOTAL_BORROWED, 0, java.math.RoundingMode.HALF_UP)
                .intValue();
    }

    // no installment has ever been due, paid or missed
    public boolean hasNoRecord() {
        return onTime + late + overdueNow + paidOffLoans + defaultedLoans + activeLoans == 0;
    }
}
