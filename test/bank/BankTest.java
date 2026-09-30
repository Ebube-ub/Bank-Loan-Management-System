package bank;

import bank.exception.CustomerAlreadyExistsException;
import bank.exception.InsufficientFundsException;
import bank.exception.LoanLimitExceededException;
import bank.exception.LoanNotFoundException;
import bank.exception.OverdueInstallmentsException;
import bank.model.Bank;
import bank.model.CreditTier;
import bank.model.Customer;
import bank.model.Installment;
import bank.model.InstallmentStatus;
import bank.model.Loan;
import bank.model.LoanStatus;
import bank.model.LoanStatusSummary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests for Bank.java. Phase 1 tests come first, Phase 2 tests are below.
class BankTest {

    private Bank bank;

    // runs before every single @Test method, so each test starts with a fresh bank
    @BeforeEach
    void setUp() {
        bank = new Bank(new BigDecimal("100000"));
    }

    // small helpers so tests don't have to repeat the same setup code
    private Customer registerStandardCustomer(String id) throws CustomerAlreadyExistsException {
        Customer customer = new Customer(id, "Test " + id, id + "@example.com", CreditTier.STANDARD);
        bank.registerCustomer(customer);
        return customer;
    }

    private Customer registerPremiumCustomer(String id) throws CustomerAlreadyExistsException {
        Customer customer = new Customer(id, "Test " + id, id + "@example.com", CreditTier.PREMIUM);
        bank.registerCustomer(customer);
        return customer;
    }

    // ---------- Phase 1: core system ----------

    @Test
    void registerCustomer_success() throws Exception {
        registerStandardCustomer("C1");

        assertEquals("C1", bank.findCustomerById("C1").getCustomerId());
    }

    @Test
    void registerCustomer_duplicateId_throws() throws Exception {
        registerStandardCustomer("C1");

        // registering "C1" again should fail, not silently overwrite it
        assertThrows(CustomerAlreadyExistsException.class, () -> registerStandardCustomer("C1"));
    }

    @Test
    void applyForLoan_success_generatesScheduleAndDeductsFunds() throws Exception {
        registerStandardCustomer("C1");
        BigDecimal fundsBefore = bank.getAvailableFunds();

        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12);

        assertEquals(12, loan.getInstallments().size());
        assertEquals(fundsBefore.subtract(new BigDecimal("1200")), bank.getAvailableFunds());

        // adding up every installment should give exactly the total repayable amount
        BigDecimal sum = BigDecimal.ZERO;
        for (Installment installment : loan.getInstallments()) {
            sum = sum.add(installment.getAmountDue());
        }
        assertEquals(loan.getTotalRepayable(), sum);
    }

    @Test
    void applyForLoan_insufficientFunds_throws() throws Exception {
        // give this bank very little money, so any real loan amount should fail
        Bank smallBank = new Bank(new BigDecimal("500"));
        smallBank.registerCustomer(new Customer("C1", "Test", "c1@example.com", CreditTier.STANDARD));

        assertThrows(InsufficientFundsException.class, () ->
                smallBank.applyForLoan("C1", "L1", new BigDecimal("1000"), new BigDecimal("0.1"), 6)
        );
    }

    @Test
    void applyForLoan_blockedByActiveLoanCountLimit() throws Exception {
        registerStandardCustomer("C1"); // STANDARD -> max 2 active loans at once

        bank.applyForLoan("C1", "L1", new BigDecimal("1000"), new BigDecimal("0.1"), 6);
        bank.applyForLoan("C1", "L2", new BigDecimal("1000"), new BigDecimal("0.1"), 6);

        // a 3rd loan should be rejected because of the loan-count limit, not the total-amount limit
        assertThrows(LoanLimitExceededException.class, () ->
                bank.applyForLoan("C1", "L3", new BigDecimal("1000"), new BigDecimal("0.1"), 6)
        );
    }

    @Test
    void applyForLoan_blockedByTotalBorrowedCap() throws Exception {
        registerPremiumCustomer("C1"); // higher loan-count limit, so this test only hits the amount cap

        bank.applyForLoan("C1", "L1", new BigDecimal("30000"), new BigDecimal("0.1"), 12);

        // 30000 + 25000 = 55000, which is over the 50000 total borrowed limit
        assertThrows(LoanLimitExceededException.class, () ->
                bank.applyForLoan("C1", "L2", new BigDecimal("25000"), new BigDecimal("0.1"), 12)
        );
    }

    @Test
    void makePayment_appliesToEarliestInstallment() throws Exception {
        registerStandardCustomer("C1");
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12);
        BigDecimal firstInstallmentDue = loan.getInstallments().get(0).getAmountDue();

        BigDecimal overpayment = bank.makePayment("L1", firstInstallmentDue);

        assertEquals(BigDecimal.ZERO.setScale(2), overpayment.setScale(2));
        assertEquals(InstallmentStatus.PAID, loan.getInstallments().get(0).getStatus());
        assertEquals(InstallmentStatus.PENDING, loan.getInstallments().get(1).getStatus());
    }

    @Test
    void makePayment_nonExistentLoan_throws() {
        assertThrows(LoanNotFoundException.class, () -> bank.makePayment("NOPE", new BigDecimal("100")));
    }

    @Test
    void makePayment_fullyRepaid_marksLoanPaidOffAndRestoresFunds() throws Exception {
        registerStandardCustomer("C1");
        BigDecimal fundsBeforeLoan = bank.getAvailableFunds();
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12);

        // once fully repaid, the bank should have its principal back plus the interest it earned
        BigDecimal expectedFundsAfterRepayment =
                fundsBeforeLoan.subtract(loan.getPrincipal()).add(loan.getTotalRepayable());

        bank.makePayment("L1", loan.getTotalRepayable());

        assertEquals(LoanStatus.PAID_OFF, loan.getStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), loan.getOutstandingBalance().setScale(2));
        assertEquals(expectedFundsAfterRepayment.setScale(2), bank.getAvailableFunds().setScale(2));
        assertTrue(loan.getCustomer().getActiveLoans().isEmpty());
    }

    @Test
    void makePayment_overpayment_reportsUnappliedAmount() throws Exception {
        registerStandardCustomer("C1");
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12);
        BigDecimal total = loan.getTotalRepayable();

        // pay 50 more than what's actually owed
        BigDecimal overpayment = bank.makePayment("L1", total.add(new BigDecimal("50")));

        assertEquals(new BigDecimal("50.00"), overpayment.setScale(2));
        assertEquals(LoanStatus.PAID_OFF, loan.getStatus());
    }

    // ---------- Phase 2: business rules & reporting ----------

    @Test
    void refreshDelinquencyStatus_marksPastDueInstallmentOverdue() throws Exception {
        registerStandardCustomer("C1");
        LocalDate longAgo = LocalDate.now().minusMonths(2);
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12, longAgo);

        bank.refreshDelinquencyStatus(LocalDate.now());

        // the first installment was due about a month ago and was never paid
        assertEquals(InstallmentStatus.OVERDUE, loan.getInstallments().get(0).getStatus());
    }

    @Test
    void loan_defaultsAfterConsecutiveMissedInstallments() throws Exception {
        registerStandardCustomer("C1");
        LocalDate longAgo = LocalDate.now().minusMonths(4);
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12, longAgo);

        bank.refreshDelinquencyStatus(LocalDate.now());

        // 3 installments in a row are now overdue, so the loan should default
        assertEquals(LoanStatus.DEFAULTED, loan.getStatus());
    }

    @Test
    void applyForLoan_blockedWhileCustomerHasOverdueInstallment() throws Exception {
        registerStandardCustomer("C1");
        LocalDate longAgo = LocalDate.now().minusMonths(2);
        bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12, longAgo);

        assertThrows(OverdueInstallmentsException.class, () ->
                bank.applyForLoan("C1", "L2", new BigDecimal("500"), new BigDecimal("0.1"), 6)
        );
    }

    @Test
    void topBorrowers_ordersByTotalPrincipalDescending() throws Exception {
        registerStandardCustomer("C1");
        registerStandardCustomer("C2");
        bank.applyForLoan("C1", "L1", new BigDecimal("1000"), new BigDecimal("0.1"), 6);
        bank.applyForLoan("C2", "L2", new BigDecimal("5000"), new BigDecimal("0.1"), 12);

        List<Customer> top = bank.topBorrowers(2);

        // C2 borrowed more, so they should be listed first
        assertEquals("C2", top.get(0).getCustomerId());
        assertEquals("C1", top.get(1).getCustomerId());
    }

    @Test
    void totalInterestEarned_reflectsOnlyPaidInstallments() throws Exception {
        registerStandardCustomer("C1");
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12);
        BigDecimal interestPerInstallment = loan.getInterestPerInstallment();

        // only pay the first installment
        bank.makePayment("L1", loan.getInstallments().get(0).getAmountDue());

        // interest earned so far should just be the interest from that one installment
        assertEquals(interestPerInstallment.setScale(2), bank.totalInterestEarned().setScale(2));
    }

    @Test
    void loansSortedByOutstandingBalance_highestFirst() throws Exception {
        registerStandardCustomer("C1");
        bank.applyForLoan("C1", "L1", new BigDecimal("1000"), new BigDecimal("0.1"), 6);
        bank.applyForLoan("C1", "L2", new BigDecimal("2000"), new BigDecimal("0.1"), 6);

        List<Loan> sorted = bank.loansSortedByOutstandingBalanceDesc();

        assertEquals("L2", sorted.get(0).getLoanId());
        assertEquals("L1", sorted.get(1).getLoanId());
    }

    @Test
    void installmentsByPaidStatus_filtersCorrectly() throws Exception {
        registerStandardCustomer("C1");
        Loan loan = bank.applyForLoan("C1", "L1", new BigDecimal("1200"), new BigDecimal("0.12"), 12);
        bank.makePayment("L1", loan.getInstallments().get(0).getAmountDue());

        assertEquals(1, bank.installmentsByPaidStatus(loan, true).size());
        assertEquals(11, bank.installmentsByPaidStatus(loan, false).size());
    }

    @Test
    void loanStatusSummary_countsEachStatus() throws Exception {
        registerStandardCustomer("C1");
        Loan paidOff = bank.applyForLoan("C1", "L1", new BigDecimal("1000"), new BigDecimal("0.1"), 6);
        bank.makePayment("L1", paidOff.getTotalRepayable());
        bank.applyForLoan("C1", "L2", new BigDecimal("500"), new BigDecimal("0.1"), 6);

        LoanStatusSummary summary = bank.loanStatusSummary();

        assertEquals(1, summary.getActiveCount());
        assertEquals(1, summary.getPaidOffCount());
        assertEquals(0, summary.getDefaultedCount());
    }
}
