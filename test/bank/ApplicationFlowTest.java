package bank;

import bank.demo.DemoDataSeeder;
import bank.exception.LoanLimitExceededException;
import bank.model.Advice;
import bank.model.ApplicationStatus;
import bank.model.Bank;
import bank.model.CreditBand;
import bank.model.CreditTier;
import bank.model.Customer;
import bank.model.Loan;
import bank.model.LoanApplication;
import bank.model.LoanAssessment;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests for the loan application flow: the system's advice (Lend / Review /
// Decline), the limits set by the credit band, and the admin's approval.
class ApplicationFlowTest {

    private static final LocalDate TODAY = LocalDate.now();

    // The demo customers are read-only here, so one shared bank is enough
    // (creating it hashes a password per customer, which takes a moment).
    private static Bank demoBank;

    // a fresh empty bank for the tests that change things
    private Bank bank;

    @BeforeAll
    static void createDemoBank() {
        demoBank = new Bank(Bank.STARTING_CAPITAL);
        DemoDataSeeder.seed(demoBank, TODAY);
    }

    @BeforeEach
    void setUp() throws Exception {
        bank = new Bank(Bank.STARTING_CAPITAL);
        bank.registerCustomer(new Customer("C1", "Test C1", "c1@example.com"));
    }

    private LoanAssessment assessDemo(String customerId, String amount) throws Exception {
        Customer customer = demoBank.findCustomerById(customerId);
        return demoBank.assess(customer, new BigDecimal(amount), TODAY);
    }

    // ---------- The advice ----------

    @Test
    void newCustomer_smallRequest_getsReviewAdvice() throws Exception {
        LoanAssessment assessment = bank.assess(bank.findCustomerById("C1"), new BigDecimal("5000"), TODAY);

        assertEquals(Advice.REVIEW, assessment.getAdvice());
        assertEquals(0, CreditBand.FAIR.getMaxSingleLoan().compareTo(assessment.getMaxAmount()));
    }

    @Test
    void requestAboveTheBandLimit_isDeclinedAndTheLimitIsOffered() throws Exception {
        BigDecimal tooMuch = CreditBand.FAIR.getMaxSingleLoan().add(BigDecimal.ONE);
        LoanAssessment assessment = bank.assess(bank.findCustomerById("C1"), tooMuch, TODAY);

        assertEquals(Advice.DECLINE, assessment.getAdvice());
        assertEquals(0, CreditBand.FAIR.getMaxSingleLoan().compareTo(assessment.getMaxAmount()));
        assertTrue(String.join(" ", assessment.getReasons()).contains("largest single loan"));
    }

    @Test
    void excellentCustomer_getsLend_untilTheirLimit() throws Exception {
        // D001 is an Excellent customer with one small active loan
        String limit = CreditBand.EXCELLENT.getMaxSingleLoan().toPlainString();
        String overLimit = CreditBand.EXCELLENT.getMaxSingleLoan().add(BigDecimal.ONE).toPlainString();

        assertEquals(Advice.LEND, assessDemo("D001", "20000").getAdvice());
        assertEquals(Advice.LEND, assessDemo("D001", limit).getAdvice());
        assertEquals(Advice.DECLINE, assessDemo("D001", overLimit).getAdvice());
    }

    @Test
    void goodCustomer_getsLend_butWithALowerLimit() throws Exception {
        String limit = CreditBand.GOOD.getMaxSingleLoan().toPlainString();
        String overLimit = CreditBand.GOOD.getMaxSingleLoan().add(BigDecimal.ONE).toPlainString();

        assertEquals(Advice.LEND, assessDemo("D003", limit).getAdvice());
        assertEquals(Advice.DECLINE, assessDemo("D003", overLimit).getAdvice());
    }

    @Test
    void poorCustomer_isDeclined() throws Exception {
        LoanAssessment assessment = assessDemo("D009", "1000");

        assertEquals(Advice.DECLINE, assessment.getAdvice());
        assertEquals(0, BigDecimal.ZERO.compareTo(assessment.getMaxAmount().max(BigDecimal.ZERO)));
        assertTrue(String.join(" ", assessment.getReasons()).contains("Poor band"));
    }

    @Test
    void customerWithUnpaidDefault_isDeclined() throws Exception {
        LoanAssessment assessment = assessDemo("D011", "1000");

        assertEquals(Advice.DECLINE, assessment.getAdvice());
        assertTrue(String.join(" ", assessment.getReasons()).contains("defaulted"));
    }

    @Test
    void customerWithOverdueInstallment_isDeclined() throws Exception {
        assertTrue(String.join(" ", assessDemo("D009", "1000").getReasons()).contains("overdue"));
    }

    @Test
    void tierFollowsTheScore() throws Exception {
        assertEquals(CreditTier.PREMIUM, demoBank.tierOf(demoBank.findCustomerById("D001"), TODAY));
        assertEquals(CreditTier.PREMIUM, demoBank.tierOf(demoBank.findCustomerById("D003"), TODAY));
        assertEquals(CreditTier.STANDARD, demoBank.tierOf(demoBank.findCustomerById("D006"), TODAY));
    }

    @Test
    void poorBand_cannotHaveAnyActiveLoan() {
        assertThrows(LoanLimitExceededException.class, () -> demoBank.checkActiveLoanLimit("D011", TODAY));
    }

    // ---------- Applications and approval ----------

    @Test
    void goodRequest_waitsForTheAdmin_andIsNotALoanYet() throws Exception {
        LoanApplication application = bank.submitApplication("C1", new BigDecimal("5000"), null, 6, TODAY);

        assertEquals(ApplicationStatus.PENDING, application.getStatus());
        assertEquals(Advice.REVIEW, application.getAdvice());
        assertTrue(bank.getAllLoans().isEmpty());
        assertEquals(Bank.STARTING_CAPITAL, bank.getAvailableFunds());
    }

    @Test
    void brokenRule_isDeclinedBySystem_withTheReasonKept() throws Exception {
        BigDecimal tooMuch = CreditBand.FAIR.getMaxSingleLoan().add(BigDecimal.ONE);
        LoanApplication application = bank.submitApplication("C1", tooMuch, null, 6, TODAY);

        assertEquals(ApplicationStatus.DECLINED, application.getStatus());
        assertEquals("system", application.getDecidedBy());
        assertTrue(application.getDecisionNote().contains("largest single loan"));
    }

    @Test
    void secondApplication_whileOneIsPending_isRefused() throws Exception {
        bank.submitApplication("C1", new BigDecimal("5000"), null, 6, TODAY);

        assertThrows(IllegalArgumentException.class, () ->
                bank.submitApplication("C1", new BigDecimal("1000"), null, 6, TODAY));
    }

    @Test
    void invalidRequests_areRefused() {
        assertThrows(IllegalArgumentException.class, () ->
                bank.submitApplication("C1", BigDecimal.ZERO, null, 6, TODAY));
        assertThrows(IllegalArgumentException.class, () ->
                bank.submitApplication("C1", new BigDecimal("1000"), null, 0, TODAY));
        assertThrows(IllegalArgumentException.class, () ->
                bank.submitApplication("C1", new BigDecimal("1000"), null, 61, TODAY));
    }

    @Test
    void approve_createsTheLoan_withASystemMadeId() throws Exception {
        LoanApplication application = bank.submitApplication("C1", new BigDecimal("5000"), null, 6, TODAY);

        Loan loan = bank.approveApplication(application.getApplicationId(), null, "admin", TODAY);

        assertEquals("L001", loan.getLoanId());
        assertEquals(ApplicationStatus.APPROVED, application.getStatus());
        assertEquals("L001", application.getLoanId());
        assertEquals("admin", application.getDecidedBy());
        assertEquals(6, loan.getInstallments().size());
        assertEquals(Bank.STARTING_CAPITAL.subtract(new BigDecimal("5000")), bank.getAvailableFunds());

        // the bank's default rate (20%) was used
        assertEquals(0, new BigDecimal("0.20").compareTo(loan.getInterestRate()));
    }

    @Test
    void approve_canChangeTheInterestRate() throws Exception {
        LoanApplication application = bank.submitApplication("C1", new BigDecimal("5000"), null, 6, TODAY);

        Loan loan = bank.approveApplication(application.getApplicationId(), new BigDecimal("0.25"), "admin", TODAY);

        assertEquals(0, new BigDecimal("0.25").compareTo(loan.getInterestRate()));
    }

    @Test
    void approve_twice_isRefused() throws Exception {
        LoanApplication application = bank.submitApplication("C1", new BigDecimal("5000"), null, 6, TODAY);
        bank.approveApplication(application.getApplicationId(), null, "admin", TODAY);

        assertThrows(IllegalArgumentException.class, () ->
                bank.approveApplication(application.getApplicationId(), null, "admin", TODAY));
    }

    @Test
    void approve_checksTheRulesAgain() throws Exception {
        LoanApplication application = bank.submitApplication("C1", new BigDecimal("5000"), null, 6, TODAY);

        // before the admin decides, the customer ends up with an overdue installment
        bank.applyForLoan("C1", "OLD", new BigDecimal("1000"), new BigDecimal("0.1"), 6, TODAY.minusMonths(2));
        bank.refreshDelinquencyStatus(TODAY);

        assertThrows(LoanLimitExceededException.class, () ->
                bank.approveApplication(application.getApplicationId(), null, "admin", TODAY));
        assertEquals(ApplicationStatus.PENDING, application.getStatus());
    }

    @Test
    void decline_byTheAdmin_keepsTheNote() throws Exception {
        LoanApplication application = bank.submitApplication("C1", new BigDecimal("5000"), null, 6, TODAY);

        bank.declineApplication(application.getApplicationId(), "Please come back next month.", "admin", TODAY);

        assertEquals(ApplicationStatus.DECLINED, application.getStatus());
        assertEquals("Please come back next month.", application.getDecisionNote());
        assertEquals("admin", application.getDecidedBy());
        assertTrue(bank.getAllLoans().isEmpty());
    }

    @Test
    void afterADecision_theCustomerCanApplyAgain() throws Exception {
        LoanApplication first = bank.submitApplication("C1", new BigDecimal("5000"), null, 6, TODAY);
        bank.declineApplication(first.getApplicationId(), null, "admin", TODAY);

        LoanApplication second = bank.submitApplication("C1", new BigDecimal("2000"), null, 6, TODAY);

        assertEquals("A002", second.getApplicationId());
        assertEquals(ApplicationStatus.PENDING, second.getStatus());
    }

    @Test
    void interestRateSetting_mustBeSensible() {
        assertThrows(IllegalArgumentException.class, () -> bank.setDefaultInterestRate(new BigDecimal("-0.1")));
        assertThrows(IllegalArgumentException.class, () -> bank.setDefaultInterestRate(new BigDecimal("1.5")));

        bank.setDefaultInterestRate(new BigDecimal("0.15"));
        assertEquals(0, new BigDecimal("0.15").compareTo(bank.getDefaultInterestRate()));
    }

    @Test
    void nairaFormatting() {
        assertEquals("₦50,000", Bank.naira(new BigDecimal("50000")));
        assertEquals("₦7,500", Bank.naira(new BigDecimal("7500.00")));
        assertEquals("₦99.50", Bank.naira(new BigDecimal("99.5")));
    }
}
