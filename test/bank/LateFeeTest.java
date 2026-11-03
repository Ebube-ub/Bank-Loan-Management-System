package bank;

import bank.model.Bank;
import bank.model.Customer;
import bank.model.Installment;
import bank.model.InstallmentStatus;
import bank.model.Loan;
import bank.model.LoanStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests for the late fee (5% of an installment, added once when it goes overdue)
// and for the money the bank starts with.
class LateFeeTest {

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

    // 1200 at 12% for 12 months: 112.00 an installment
    private Loan loanDisbursedMonthsAgo(int months) throws Exception {
        return bank.applyForLoan("C1", "L1", money("1200"), money("0.12"), 12, today.minusMonths(months));
    }

    @Test
    void theFee_isFivePercentOfTheInstallment() {
        assertEquals(0, money("0.05").compareTo(Bank.LATE_FEE_RATE));
    }

    @Test
    void anOverdueInstallment_getsTheFeeAddedToWhatIsDue() throws Exception {
        Loan loan = loanDisbursedMonthsAgo(2);
        bank.refreshDelinquencyStatus(today);

        Installment late = loan.getInstallments().get(0);   // due a month ago, unpaid
        Installment notYet = loan.getInstallments().get(1); // due today

        assertEquals(InstallmentStatus.OVERDUE, late.getStatus());
        assertEquals(0, money("5.60").compareTo(late.getLateFee()));

        // what is due is the installment, plus its late fee, plus the interest on the late amount
        assertEquals(0, money("112.00").compareTo(late.getBaseAmount()));
        assertEquals(0, money("112.00").add(late.getLateFee()).add(late.getLateInterest())
                .compareTo(late.getAmountDue()));

        assertEquals(0, BigDecimal.ZERO.compareTo(notYet.getLateFee()));
        assertEquals(0, money("112.00").compareTo(notYet.getAmountDue()));
    }

    @Test
    void theFee_isChargedOnlyOnce() throws Exception {
        Loan loan = loanDisbursedMonthsAgo(2);

        bank.refreshDelinquencyStatus(today);
        bank.refreshDelinquencyStatus(today.plusDays(10));
        bank.refreshDelinquencyStatus(today.plusDays(20));

        assertEquals(0, money("5.60").compareTo(loan.getInstallments().get(0).getLateFee()));
    }

    @Test
    void paidOnTheDueDate_hasNoFee() throws Exception {
        Loan loan = loanDisbursedMonthsAgo(2);
        Installment first = loan.getInstallments().get(0);

        bank.refreshDelinquencyStatus(first.getDueDate());
        bank.makePayment("L1", first.getAmountDue(), first.getDueDate(), "test", false);

        assertEquals(InstallmentStatus.PAID, first.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(first.getLateFee()));
    }

    @Test
    void payingTheInstallmentWithItsFee_clearsIt() throws Exception {
        Loan loan = loanDisbursedMonthsAgo(2);
        bank.refreshDelinquencyStatus(today);
        Installment late = loan.getInstallments().get(0);
        BigDecimal owedBefore = loan.getOutstandingBalance();

        BigDecimal due = late.getAmountDue();
        bank.makePayment("L1", due, today, "test", false);

        assertEquals(InstallmentStatus.PAID, late.getStatus());
        assertEquals(0, owedBefore.subtract(due).compareTo(loan.getOutstandingBalance()));
    }

    @Test
    void payingOnlyTheOriginalAmount_leavesTheFeeOwing() throws Exception {
        Loan loan = loanDisbursedMonthsAgo(2);
        bank.refreshDelinquencyStatus(today);
        Installment late = loan.getInstallments().get(0);

        bank.makePayment("L1", money("112.00"), today, "test", false);

        // the 112.00 pays the installment itself; the fee and the interest are still owed
        assertFalse(late.isPaid());
        assertEquals(0, late.getLateFee().add(late.getLateInterest()).compareTo(late.getRemainingAmount()));
    }

    @Test
    void theLoanAddsUpItsLateFees() throws Exception {
        // disbursed 4 months ago: installments 1 to 3 are overdue, so the loan defaults
        Loan loan = loanDisbursedMonthsAgo(4);
        bank.refreshDelinquencyStatus(today);

        assertEquals(LoanStatus.DEFAULTED, loan.getStatus());
        assertEquals(0, money("16.80").compareTo(loan.getTotalLateFees()));
    }

    @Test
    void theTotalToRepayOfTheLoan_isNotChangedByFees() throws Exception {
        Loan loan = loanDisbursedMonthsAgo(2);
        BigDecimal before = loan.getTotalRepayable();

        bank.refreshDelinquencyStatus(today);

        assertEquals(0, before.compareTo(loan.getTotalRepayable()));
    }

    // ---------- The bank's starting money ----------

    @Test
    void theStartingMoney_isFiveMillionNaira() {
        assertEquals(0, money("5000000").compareTo(Bank.STARTING_CAPITAL));
    }

    @Test
    void increasingTheCapital_addsOnlyTheDifference_andOnlyOnce() throws Exception {
        // 1200 is lent out of the original 100,000
        bank.applyForLoan("C1", "L1", money("1200"), money("0.12"), 12);
        assertEquals(0, money("98800").compareTo(bank.getAvailableFunds()));

        assertTrue(bank.increaseCapitalTo(Bank.STARTING_CAPITAL));

        // 98,800 + the 4,900,000 that was added
        assertEquals(0, money("4998800").compareTo(bank.getAvailableFunds()));

        assertFalse(bank.increaseCapitalTo(Bank.STARTING_CAPITAL));
        assertEquals(0, money("4998800").compareTo(bank.getAvailableFunds()));
    }

    @Test
    void aBankThatAlreadyHasEnough_isLeftAlone() {
        Bank rich = new Bank(new BigDecimal("6000000"));

        assertFalse(rich.increaseCapitalTo(Bank.STARTING_CAPITAL));
        assertEquals(0, money("6000000").compareTo(rich.getAvailableFunds()));
    }
}
