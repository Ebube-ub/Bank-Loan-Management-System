package bank.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Works out a customer's credit score (300 to 850) from the loans recorded
// in this system. The score is calculated every time it is needed and never
// stored, so it can't get out of date.
//
// A customer with no history starts at 600 (the FAIR band): cautious, not blocked.
// Then points are added or taken away:
//
//   +10  each installment paid on or before its due date   (at most +150)
//   -15  each installment paid late
//   -40  each installment overdue right now
//   +25  each loan fully paid off                           (at most +75)
//   -150 each loan that defaulted
//   +10 / 0 / -20 / -40  how much of the borrowing limit is in use
//                        (only while the customer has an active loan)
public class CreditScorer {

    public static final int START_SCORE = 600;
    public static final int MIN_SCORE = 300;
    public static final int MAX_SCORE = 850;

    private static final int ON_TIME_POINTS = 10;
    private static final int ON_TIME_MAX_COUNT = 15;
    private static final int LATE_POINTS = -15;
    private static final int OVERDUE_POINTS = -40;
    private static final int PAID_OFF_POINTS = 25;
    private static final int PAID_OFF_MAX_COUNT = 3;
    private static final int DEFAULTED_POINTS = -150;

    public static CreditScore score(Customer customer, LocalDate asOf) {

        int onTime = 0;
        int late = 0;
        int overdue = 0;
        int paidOff = 0;
        int defaulted = 0;
        BigDecimal activePrincipal = BigDecimal.ZERO;
        boolean hasActiveLoan = false;

        for (Loan loan : customer.getLoans()) {

            if (loan.getStatus() == LoanStatus.PAID_OFF) {
                paidOff++;
            } else if (loan.getStatus() == LoanStatus.DEFAULTED) {
                defaulted++;
            } else {
                hasActiveLoan = true;
                activePrincipal = activePrincipal.add(loan.getPrincipal());
            }

            for (Installment installment : loan.getInstallments()) {

                if (installment.getStatus() == InstallmentStatus.PAID) {
                    LocalDate paidDate = installment.getPaidDate();
                    if (paidDate != null && paidDate.isAfter(installment.getDueDate())) {
                        late++;
                    } else {
                        onTime++;
                    }
                } else if (installment.getStatus() == InstallmentStatus.OVERDUE
                        || installment.getDueDate().isBefore(asOf)) {
                    // not paid and past its due date (even if nobody has refreshed the status yet)
                    overdue++;
                }
            }
        }

        List<ScoreFactor> factors = new ArrayList<>();
        int total = START_SCORE;

        factors.add(new ScoreFactor("Starting score", START_SCORE));

        int onTimePoints = Math.min(onTime, ON_TIME_MAX_COUNT) * ON_TIME_POINTS;
        if (onTime > 0) {
            factors.add(new ScoreFactor(onTime + " installment(s) paid on time", onTimePoints));
        }
        total += onTimePoints;

        int latePoints = late * LATE_POINTS;
        if (late > 0) {
            factors.add(new ScoreFactor(late + " installment(s) paid late", latePoints));
        }
        total += latePoints;

        int overduePoints = overdue * OVERDUE_POINTS;
        if (overdue > 0) {
            factors.add(new ScoreFactor(overdue + " installment(s) overdue now", overduePoints));
        }
        total += overduePoints;

        int paidOffPoints = Math.min(paidOff, PAID_OFF_MAX_COUNT) * PAID_OFF_POINTS;
        if (paidOff > 0) {
            factors.add(new ScoreFactor(paidOff + " loan(s) fully paid off", paidOffPoints));
        }
        total += paidOffPoints;

        int defaultedPoints = defaulted * DEFAULTED_POINTS;
        if (defaulted > 0) {
            factors.add(new ScoreFactor(defaulted + " loan(s) defaulted", defaultedPoints));
        }
        total += defaultedPoints;

        if (hasActiveLoan) {
            int usePoints = limitInUsePoints(activePrincipal);
            int percentInUse = activePrincipal
                    .multiply(BigDecimal.valueOf(100))
                    .divide(Bank.MAX_TOTAL_BORROWED, 0, java.math.RoundingMode.HALF_UP)
                    .intValue();
            factors.add(new ScoreFactor(percentInUse + "% of the borrowing limit in use", usePoints));
            total += usePoints;
        }

        if (total < MIN_SCORE) {
            total = MIN_SCORE;
        }
        if (total > MAX_SCORE) {
            total = MAX_SCORE;
        }

        return new CreditScore(total, CreditBand.fromScore(total), factors);
    }

    // +10 under 25% in use, 0 up to 50%, -20 up to 80%, -40 above that
    private static int limitInUsePoints(BigDecimal activePrincipal) {
        BigDecimal limit = Bank.MAX_TOTAL_BORROWED;

        if (activePrincipal.compareTo(limit.multiply(new BigDecimal("0.25"))) < 0) {
            return 10;
        } else if (activePrincipal.compareTo(limit.multiply(new BigDecimal("0.50"))) <= 0) {
            return 0;
        } else if (activePrincipal.compareTo(limit.multiply(new BigDecimal("0.80"))) <= 0) {
            return -20;
        }
        return -40;
    }
}
