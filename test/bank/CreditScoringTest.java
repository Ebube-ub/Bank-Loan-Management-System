package bank;

import bank.model.Bank;
import bank.model.CreditBand;
import bank.model.CreditScore;
import bank.model.CreditTier;
import bank.model.Customer;
import bank.model.Installment;
import bank.model.Loan;
import bank.model.LoanStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Tests for the credit score: the starting point, each thing that adds or
// takes away points, and the bands the score falls into.
class CreditScoringTest {

    private Bank bank;
    private Customer customer;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() throws Exception {
        bank = new Bank(new BigDecimal("100000"));
        customer = new Customer("C1", "Test C1", "c1@example.com");
        bank.registerCustomer(customer);
    }

    private int score() {
        return bank.scoreOf(customer, today).getScore();
    }

    // pays one installment on its due date, or a few days after it
    private void pay(Loan loan, int installmentNumber, int daysLate) throws Exception {
        Installment installment = loan.getInstallments().get(installmentNumber - 1);
        LocalDate paymentDate = installment.getDueDate().plusDays(daysLate);

        // a late installment gets its late fee when it is first seen overdue,
        // so do that check first and then pay the amount that is really due
        bank.refreshDelinquencyStatus(paymentDate);

        bank.makePayment(loan.getLoanId(), installment.getAmountDue(), paymentDate, "test", false);
    }

    @Test
    void newCustomer_startsAtSixHundred_inTheFairBand() {
        CreditScore result = bank.scoreOf(customer, today);

        assertEquals(600, result.getScore());
        assertEquals(CreditBand.FAIR, result.getBand());
    }

    @Test
    void bandEdges() {
        assertEquals(CreditBand.EXCELLENT, CreditBand.fromScore(850));
        assertEquals(CreditBand.EXCELLENT, CreditBand.fromScore(750));
        assertEquals(CreditBand.GOOD, CreditBand.fromScore(749));
        assertEquals(CreditBand.GOOD, CreditBand.fromScore(670));
        assertEquals(CreditBand.FAIR, CreditBand.fromScore(669));
        assertEquals(CreditBand.FAIR, CreditBand.fromScore(580));
        assertEquals(CreditBand.POOR, CreditBand.fromScore(579));
        assertEquals(CreditBand.POOR, CreditBand.fromScore(300));
    }

    @Test
    void bandLimits_matchThePlan() {
        assertEquals(new BigDecimal("1000000"), CreditBand.EXCELLENT.getMaxSingleLoan());
        assertEquals(new BigDecimal("600000"), CreditBand.GOOD.getMaxSingleLoan());
        assertEquals(new BigDecimal("300000"), CreditBand.FAIR.getMaxSingleLoan());
        assertEquals(new BigDecimal("2000000"), Bank.MAX_TOTAL_BORROWED);
        assertEquals(0, CreditBand.POOR.getMaxSingleLoan().compareTo(BigDecimal.ZERO));

        assertEquals(3, CreditBand.EXCELLENT.getMaxActiveLoans());
        assertEquals(3, CreditBand.GOOD.getMaxActiveLoans());
        assertEquals(2, CreditBand.FAIR.getMaxActiveLoans());
        assertEquals(0, CreditBand.POOR.getMaxActiveLoans());
    }

    @Test
    void tierFollowsTheBand() {
        assertEquals(CreditTier.PREMIUM, CreditBand.EXCELLENT.getTier());
        assertEquals(CreditTier.PREMIUM, CreditBand.GOOD.getTier());
        assertEquals(CreditTier.STANDARD, CreditBand.FAIR.getTier());
    }

    @Test
    void onTimePayments_addTenPointsEach() throws Exception {
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12,
                today.minusMonths(3));
        pay(loan, 1, 0);
        pay(loan, 2, 0);

        // 600 + 2 x 10 on time + 10 for using under 25% of the borrowing limit
        assertEquals(630, score());
    }

    @Test
    void latePayments_takeFifteenPointsEach() throws Exception {
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12,
                today.minusMonths(3));
        pay(loan, 1, 5);
        pay(loan, 2, 5);

        // 600 - 2 x 15 for paying late + 10 for the limit in use
        assertEquals(580, score());
    }

    @Test
    void anUnpaidInstallmentThatIsPastDue_countsAsOverdue_evenBeforeAnyRefresh() throws Exception {
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12,
                today.minusMonths(3));
        pay(loan, 1, 0);

        // installment 2 was due a month ago and is still unpaid
        // 600 + 10 on time - 40 overdue + 10 for the limit in use
        assertEquals(580, score());
    }

    @Test
    void overdueInstallment_takesFortyPoints() throws Exception {
        // disbursed 2 months ago: the first installment was due a month ago and is unpaid
        bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12, today.minusMonths(2));
        bank.refreshDelinquencyStatus(today);

        // 600 - 40 overdue + 10 for the limit in use
        assertEquals(570, score());
        assertEquals(CreditBand.POOR, bank.scoreOf(customer, today).getBand());
    }

    @Test
    void defaultedLoan_takesTheOverdueAndTheDefaultPoints() throws Exception {
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12,
                today.minusMonths(4));
        bank.refreshDelinquencyStatus(today);

        assertEquals(LoanStatus.DEFAULTED, loan.getStatus());

        // 600 - 3 x 40 overdue - 150 default; a defaulted loan is not "active", so no limit points
        assertEquals(330, score());
    }

    @Test
    void paidOffLoan_addsTwentyFivePoints() throws Exception {
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1000"), new BigDecimal("0.12"), 2);
        bank.makePayment("L1", loan.payoffAmount(LocalDate.now(), Bank.PREPAYMENT_FEE_RATE));

        assertEquals(LoanStatus.PAID_OFF, loan.getStatus());

        // 600 + 2 x 10 on time + 25 for the paid-off loan; no active loan, so no limit points
        assertEquals(645, score());
    }

    @Test
    void limitInUse_changesThePoints() throws Exception {
        // 60% of the borrowing limit in use costs 20 points
        Bank bigBank = new Bank(new BigDecimal("10000000"));
        Customer borrower = new Customer("C9", "Test C9", "c9@example.com");
        bigBank.registerCustomer(borrower);

        BigDecimal sixtyPercent = Bank.MAX_TOTAL_BORROWED.multiply(new BigDecimal("0.6"));
        bigBank.applyForLoan("C9", "L9", sixtyPercent, new BigDecimal("0.1"), 12);

        assertEquals(580, bigBank.scoreOf(borrower, today).getScore());
    }

    @Test
    void scoreBreakdown_addsUpToTheScore() throws Exception {
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12,
                today.minusMonths(3));
        pay(loan, 1, 0);
        pay(loan, 2, 7);

        CreditScore result = bank.scoreOf(customer, today);

        int sum = 0;
        for (var factor : result.getFactors()) {
            sum += factor.getPoints();
        }
        assertEquals(result.getScore(), sum);
    }
}
