package bank;

import bank.exception.CustomerAlreadyExistsException;
import bank.model.Bank;
import bank.model.Customer;
import bank.model.FinanceReport;
import bank.model.Installment;
import bank.model.Loan;
import bank.model.MonthlyFinance;
import bank.model.PaymentRecord;
import bank.model.UserAccount;
import bank.model.UserRole;
import bank.security.PasswordHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests for the finance report (revenue, deductions, profit), the payment
// log, and logins.
class FinanceAndAccountsTest {

    private final LocalDate today = LocalDate.now();
    private Bank bank;

    @BeforeEach
    void setUp() throws Exception {
        bank = new Bank(new BigDecimal("100000"));
        bank.registerCustomer(new Customer("C1", "Test C1", "c1@example.com"));
    }

    private BigDecimal money(String text) {
        return new BigDecimal(text);
    }

    // ---------- Finance ----------

    @Test
    void emptyBank_hasAZeroReport() {
        FinanceReport report = bank.financeReport(today);

        assertEquals(1, report.getMonths().size());
        assertEquals(0, report.getRevenue().compareTo(BigDecimal.ZERO));
        assertEquals(0, report.getProfit().compareTo(BigDecimal.ZERO));
    }

    // pays one installment in full, checking first for a late fee, as the real system does
    private void payInstallment(String loanId, Loan loan, int number, LocalDate date) throws Exception {
        bank.refreshDelinquencyStatus(date);
        bank.makePayment(loanId, loan.getInstallments().get(number - 1).getAmountDue(), date, "test", false);
    }

    @Test
    void revenue_isTheInterestOfPaidInstallments() throws Exception {
        // 1000 at 20% for 2 months: 33.33 interest, so 16.67 in each installment.
        // Paid on the due date, so there is no late fee.
        Loan loan = bank.applyForLoan("C1", "L1", money("1000"), money("0.2"), 2, today.minusMonths(2));

        payInstallment("L1", loan, 1, loan.getInstallments().get(0).getDueDate());

        // one installment paid: one installment's share of the interest
        assertEquals(0, money("16.67").compareTo(bank.financeReport(today).getRevenue()));

        payInstallment("L1", loan, 2, loan.getInstallments().get(1).getDueDate());

        assertEquals(0, money("33.34").compareTo(bank.financeReport(today).getRevenue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(bank.financeReport(today).getLateFees()));
    }

    @Test
    void lateFees_countAsMoneyEarned_onceTheyArePaid() throws Exception {
        // disbursed 3 months ago, so both installments are overdue and get a 5% fee
        Loan loan = bank.applyForLoan("C1", "L1", money("1000"), money("0.2"), 2, today.minusMonths(3));
        bank.refreshDelinquencyStatus(today);

        // the fees and interest exist but nothing is paid yet, so nothing is earned
        assertEquals(0, BigDecimal.ZERO.compareTo(bank.financeReport(today).getRevenue()));

        // the interest on each late amount (the installment plus its fee), worked out by the formula
        BigDecimal expectedLateInterest = BigDecimal.ZERO;
        for (Installment installment : loan.getInstallments()) {
            long daysLate = java.time.temporal.ChronoUnit.DAYS.between(installment.getDueDate(), today);
            BigDecimal lateAmount = installment.getBaseAmount().add(installment.getLateFee());
            expectedLateInterest = expectedLateInterest.add(
                    Installment.lateInterestFor(lateAmount, money("0.2"), daysLate));
        }

        payInstallment("L1", loan, 1, today);
        payInstallment("L1", loan, 2, today);

        FinanceReport report = bank.financeReport(today);

        // fees: 5% of 516.67 and 5% of 516.66, each 25.83
        assertEquals(0, money("33.34").compareTo(report.getInterest()));
        assertEquals(0, money("51.66").compareTo(report.getLateFees()));
        assertEquals(0, expectedLateInterest.compareTo(report.getLateInterest()));
        assertEquals(0, money("85.00").add(expectedLateInterest).compareTo(report.getRevenue()));
    }

    @Test
    void profit_isTheMoneyEarnedMinusTheMoneyLost() throws Exception {
        // a loan paid in full on its due dates: 33.34 of interest earned
        Loan paid = bank.applyForLoan("C1", "L1", money("1000"), money("0.2"), 2, today.minusMonths(2));
        payInstallment("L1", paid, 1, paid.getInstallments().get(0).getDueDate());
        payInstallment("L1", paid, 2, paid.getInstallments().get(1).getDueDate());

        // a second customer whose 600 loan defaults without a payment
        bank.registerCustomer(new Customer("C2", "Test C2", "c2@example.com"));
        bank.applyForLoan("C2", "L2", money("600"), money("0.2"), 6, today.minusMonths(5));
        bank.refreshDelinquencyStatus(today);

        FinanceReport report = bank.financeReport(today);
        MonthlyFinance thisMonth = report.getMonths().get(report.getMonths().size() - 1);

        assertEquals(YearMonth.from(today), thisMonth.getMonth());
        assertEquals(0, money("600").compareTo(thisMonth.getBadDebt()));

        assertEquals(0, money("33.34").compareTo(report.getRevenue()));
        assertEquals(0, money("600").compareTo(report.getBadDebt()));
        assertEquals(0, money("-566.66").compareTo(report.getProfit()));
    }

    @Test
    void badDebt_isTheUnpaidPrincipalOfDefaultedLoans() throws Exception {
        // 3000 at 20% for 6 months, disbursed 5 months ago and never paid: it defaults
        bank.applyForLoan("C1", "L1", money("3000"), money("0.2"), 6, today.minusMonths(5));
        bank.refreshDelinquencyStatus(today);

        FinanceReport report = bank.financeReport(today);

        // all 3000 of the principal is unpaid, but the unearned interest is not counted as lost
        assertEquals(0, money("3000").compareTo(report.getBadDebt()));
        assertEquals(0, money("-3000").compareTo(report.getProfit()));
    }

    // ---------- Payment log ----------

    @Test
    void payments_areLogged_withWhoRecordedThem() throws Exception {
        Loan loan = bank.applyForLoan("C1", "L1", money("1000"), money("0.2"), 2, today.minusDays(1));
        BigDecimal installment = loan.getInstallments().get(0).getAmountDue();

        bank.makePayment("L1", installment, today, "c1", false);
        bank.makePayment("L1", money("100"), today, "admin", true);

        List<PaymentRecord> log = bank.paymentsForLoan("L1");

        assertEquals(2, log.size());
        assertEquals("c1", log.get(0).getRecordedBy());
        assertFalse(log.get(0).isRecordedByAdmin());
        assertEquals("admin", log.get(1).getRecordedBy());
        assertTrue(log.get(1).isRecordedByAdmin());
        assertEquals(0, money("100").compareTo(log.get(1).getAmountApplied()));
    }

    @Test
    void anOverpayment_isLoggedOnlyForTheAmountUsed() throws Exception {
        Loan loan = bank.applyForLoan("C1", "L1", money("1000"), money("0.2"), 2, today);
        bank.makePayment("L1", loan.getTotalRepayable().add(money("500")), today, "c1", false);

        assertEquals(0, loan.getTotalRepayable().compareTo(bank.paymentsForLoan("L1").get(0).getAmountApplied()));
    }

    // ---------- Logins ----------

    @Test
    void registerWithLogin_givesATemporaryPassword_thatMustBeChanged() throws Exception {
        String temporary = bank.registerCustomerWithLogin(new Customer("D100", "Ada", "ada@example.com"));

        UserAccount account = bank.authenticate("D100", temporary); // usernames ignore case
        assertNotNull(account);
        assertEquals(UserRole.USER, account.getRole());
        assertEquals("D100", account.getCustomerId());
        assertTrue(account.isMustChangePassword());
    }

    @Test
    void wrongPassword_orUnknownUser_isRejected() throws Exception {
        String temporary = bank.registerCustomerWithLogin(new Customer("D100", "Ada", "ada@example.com"));

        assertNull(bank.authenticate("d100", "wrong-password"));
        assertNull(bank.authenticate("nobody", temporary));
        assertNull(bank.authenticate("d100", null));
    }

    @Test
    void duplicateCustomer_isRefused_andLeavesNoExtraLogin() throws Exception {
        bank.registerCustomerWithLogin(new Customer("D100", "Ada", "ada@example.com"));

        assertThrows(CustomerAlreadyExistsException.class, () ->
                bank.registerCustomerWithLogin(new Customer("D100", "Someone else", "x@example.com")));
    }

    @Test
    void changePassword_clearsTheTemporaryFlag_andEnforcesRules() throws Exception {
        String temporary = bank.registerCustomerWithLogin(new Customer("D100", "Ada", "ada@example.com"));

        assertThrows(IllegalArgumentException.class, () -> bank.changePassword("d100", "not-right", "NewPassword1"));
        assertThrows(IllegalArgumentException.class, () -> bank.changePassword("d100", temporary, "short"));
        assertThrows(IllegalArgumentException.class, () -> bank.changePassword("d100", temporary, temporary));
        assertThrows(IllegalArgumentException.class, () -> bank.changePassword("d100", temporary, "D100"));

        bank.changePassword("d100", temporary, "MyOwnPassword9");

        assertNull(bank.authenticate("d100", temporary));
        UserAccount account = bank.authenticate("d100", "MyOwnPassword9");
        assertNotNull(account);
        assertFalse(account.isMustChangePassword());
    }

    @Test
    void resetPassword_givesANewTemporaryPassword_andCreatesMissingLogins() throws Exception {
        // C1 was registered without a login
        assertNull(bank.findUserForCustomer("C1"));

        String first = bank.resetPassword("C1");
        assertNotNull(bank.authenticate("c1", first));

        String second = bank.resetPassword("C1");
        assertNull(bank.authenticate("c1", first));
        assertNotNull(bank.authenticate("c1", second));
        assertTrue(bank.findUserForCustomer("C1").isMustChangePassword());
    }

    @Test
    void createAdmin_makesAnAdminLogin() {
        assertFalse(bank.hasAdmin());

        String temporary = bank.createAdmin("admin");

        assertTrue(bank.hasAdmin());
        assertEquals(UserRole.ADMIN, bank.authenticate("admin", temporary).getRole());
    }

    // ---------- Password hashing ----------

    @Test
    void hash_checksOut_butIsNeverThePassword() {
        String hash = PasswordHasher.hash("Secret123");

        assertTrue(PasswordHasher.matches("Secret123", hash));
        assertFalse(PasswordHasher.matches("secret123", hash));
        assertFalse(hash.contains("Secret123"));
    }

    @Test
    void samePassword_getsADifferentHashEachTime() {
        assertNotEquals(PasswordHasher.hash("Secret123"), PasswordHasher.hash("Secret123"));
    }

    @Test
    void brokenStoredHash_isNeverAMatch() {
        assertFalse(PasswordHasher.matches("anything", "not-a-hash"));
        assertFalse(PasswordHasher.matches("anything", ""));
    }

    @Test
    void randomPasswords_areLongEnoughAndDifferent() {
        String a = PasswordHasher.randomPassword();
        String b = PasswordHasher.randomPassword();

        assertEquals(10, a.length());
        assertNotEquals(a, b);
    }
}
