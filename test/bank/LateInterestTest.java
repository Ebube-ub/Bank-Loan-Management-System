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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

// Tests for interest on late amounts: a late installment grows every day, compounded
// monthly, and the interest is added to what is due, like the late fee.
class LateInterestTest {

    private final LocalDate today = LocalDate.now();
    private final BigDecimal rate = new BigDecimal("0.20");
    private Bank bank;
    private Loan loan;
    private LocalDate due1;

    @BeforeEach
    void setUp() throws Exception {
        bank = new Bank(new BigDecimal("5000000"));
        bank.registerCustomer(new Customer("C1", "Test C1", "c1@example.com"));

        // 1,000,000 at 20% for 12 months: 1,200,000 to repay, so 100,000 an installment
        loan = bank.applyForLoan("C1", "L1", money("1000000"), rate, 12, today);
        due1 = loan.getInstallments().get(0).getDueDate();
    }

    private BigDecimal money(String text) {
        return new BigDecimal(text);
    }

    private Installment first() {
        return loan.getInstallments().get(0);
    }

    @Test
    void theFormula_matchesTheExamplesInThePlan() {
        // 105,000 (a 100,000 installment plus its 5,000 fee), 75 days late: 4,429.84
        assertEquals(0, money("4429.84").compareTo(Installment.lateInterestFor(money("105000"), rate, 75)));

        // one month (30 days) is 1.6667% of the amount
        assertEquals(0, money("1666.67").compareTo(Installment.lateInterestFor(money("100000"), rate, 30)));

        // two months: 100,000 grows to 103,361.11
        assertEquals(0, money("3361.11").compareTo(Installment.lateInterestFor(money("100000"), rate, 60)));

        assertEquals(0, BigDecimal.ZERO.compareTo(Installment.lateInterestFor(money("100000"), rate, 0)));
    }

    @Test
    void aLateInstallment_getsItsFeeAndThenInterestOnTheInstallmentPlusFee() {
        bank.refreshDelinquencyStatus(due1.plusDays(20));

        Installment late = first();
        BigDecimal expected = Installment.lateInterestFor(money("105000.00"), rate, 20);

        assertEquals(InstallmentStatus.OVERDUE, late.getStatus());
        assertEquals(0, money("5000.00").compareTo(late.getLateFee()));
        assertEquals(0, expected.compareTo(late.getLateInterest()));
        assertEquals(0, money("100000.00").compareTo(late.getBaseAmount()));
        assertEquals(0, money("105000.00").add(expected).compareTo(late.getAmountDue()));
    }

    @Test
    void theInterestGrowsEveryDay_andIsNotAddedTwice() {
        bank.refreshDelinquencyStatus(due1.plusDays(20));
        BigDecimal after20 = first().getLateInterest();

        bank.refreshDelinquencyStatus(due1.plusDays(25));
        BigDecimal after25 = first().getLateInterest();

        assertNotEquals(0, after20.compareTo(after25));
        // worked out from the due date each time, not piled up, so the figure is exactly the formula
        assertEquals(0, Installment.lateInterestFor(money("105000.00"), rate, 25).compareTo(after25));

        // looking again on the same day changes nothing
        bank.refreshDelinquencyStatus(due1.plusDays(25));
        assertEquals(0, after25.compareTo(first().getLateInterest()));
    }

    @Test
    void anInstallmentThatIsNotLateYet_hasNoInterest() {
        bank.refreshDelinquencyStatus(due1.plusDays(20));

        Installment notYet = loan.getInstallments().get(1);
        assertEquals(0, BigDecimal.ZERO.compareTo(notYet.getLateInterest()));
        assertEquals(0, money("100000.00").compareTo(notYet.getAmountDue()));
    }

    @Test
    void theOutstandingBalance_includesTheFeeAndTheInterest() {
        bank.refreshDelinquencyStatus(due1.plusDays(20));

        BigDecimal expected = money("1200000.00").add(first().getLateFee()).add(first().getLateInterest());
        assertEquals(0, expected.compareTo(loan.getOutstandingBalance()));
    }

    @Test
    void payingTheWholeAmountDue_clearsTheInstallmentWithItsFeeAndInterest() throws Exception {
        bank.refreshDelinquencyStatus(due1.plusDays(20));
        BigDecimal due = first().getAmountDue();

        bank.makePayment("L1", due, due1.plusDays(20), "test", false);

        assertEquals(InstallmentStatus.PAID, first().getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(first().getRemainingAmount()));
    }

    @Test
    void aPartPayment_meansInterestGrowsOnlyOnWhatIsLeft() throws Exception {
        LocalDate payDay = due1.plusDays(20);
        bank.refreshDelinquencyStatus(payDay);
        BigDecimal interestAtPayment = first().getLateInterest();

        bank.makePayment("L1", money("50000"), payDay, "test", false);
        BigDecimal leftAfterPayment = first().getRemainingAmount();

        // 30 days later, the interest grew on what was left, not on the original amount
        bank.refreshDelinquencyStatus(payDay.plusDays(30));

        BigDecimal expected = interestAtPayment.add(Installment.lateInterestFor(leftAfterPayment, rate, 30));
        assertEquals(0, expected.compareTo(first().getLateInterest()));
    }

    @Test
    void aDefaultedLoan_growsOnEverythingUnpaid_fromTheDayItDefaulted() {
        LocalDate defaultDay = due1.plusDays(75);
        bank.refreshDelinquencyStatus(defaultDay);

        assertEquals(LoanStatus.DEFAULTED, loan.getStatus());

        Installment later = loan.getInstallments().get(3); // not due yet when the loan defaulted
        assertEquals(0, BigDecimal.ZERO.compareTo(later.getLateInterest()));

        bank.refreshDelinquencyStatus(defaultDay.plusDays(30));

        // it starts growing from the default day: one month on 100,000 is 1,666.67
        assertEquals(0, money("1666.67").compareTo(later.getLateInterest()));
        assertEquals(InstallmentStatus.PENDING, later.getStatus());

        // an installment that was already overdue carries on from its own due date
        BigDecimal expectedFirst = Installment.lateInterestFor(money("105000.00"), rate, 105);
        assertEquals(0, expectedFirst.compareTo(first().getLateInterest()));
    }

    @Test
    void aDefaultedLoan_hasNoPrepaymentCharge() {
        bank.refreshDelinquencyStatus(due1.plusDays(75));

        assertEquals(LoanStatus.DEFAULTED, loan.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(loan.payoffFee(today, Bank.PREPAYMENT_FEE_RATE)));
    }

    @Test
    void theDefaulterExampleInThePlan_comesToExactlyThatFigure() throws Exception {
        // Installments of 100,000 due on 1 June, 1 July and 1 August 2026, none paid.
        // The loan defaults on 2 August. On 19 October the three owe 334,651.79 in total.
        Bank planBank = new Bank(new BigDecimal("5000000"));
        planBank.registerCustomer(new Customer("C2", "Plan Customer", "c2@example.com"));
        Loan planLoan = planBank.applyForLoan("C2", "L2", money("1000000"), rate, 12, LocalDate.of(2026, 5, 1));

        planBank.refreshDelinquencyStatus(LocalDate.of(2026, 8, 2));
        assertEquals(LoanStatus.DEFAULTED, planLoan.getStatus());
        assertEquals(LocalDate.of(2026, 8, 2), planLoan.getDefaultedDate());

        planBank.refreshDelinquencyStatus(LocalDate.of(2026, 10, 19));

        BigDecimal owedOnTheThree = BigDecimal.ZERO;
        for (int i = 0; i < 3; i++) {
            owedOnTheThree = owedOnTheThree.add(planLoan.getInstallments().get(i).getRemainingAmount());
        }

        // each installment is worked out on its own (rounded to the kobo), so allow 2 kobo
        BigDecimal difference = owedOnTheThree.subtract(money("334651.79")).abs();
        assertEquals(true, difference.compareTo(money("0.02")) <= 0, "owed " + owedOnTheThree);
    }

    @Test
    void aLoanListsItsLateAmounts_inThreeLines() {
        LocalDate day = due1.plusDays(20);
        bank.refreshDelinquencyStatus(day);

        var entries = loan.lateEntries(day);

        assertEquals(1, entries.size());
        assertEquals(20, entries.get(0).getDaysOverdue());
        assertEquals(0, money("100000.00").compareTo(entries.get(0).getBaseAmount()));
        assertEquals(0, money("5000.00").compareTo(entries.get(0).getLateFee()));
        assertEquals(0, first().getLateInterest().compareTo(entries.get(0).getLateInterest()));
        assertEquals(0, first().getRemainingAmount().compareTo(entries.get(0).getOwedToday()));
        assertEquals(false, entries.get(0).isDueBecauseDefaulted());
    }

    @Test
    void aDefaultedLoan_marksTheInstallmentsThatAreDueOnlyBecauseItDefaulted() {
        LocalDate day = due1.plusDays(75);
        bank.refreshDelinquencyStatus(day);

        var entries = loan.lateEntries(day);

        assertEquals(12, entries.size());
        assertEquals(false, entries.get(0).isDueBecauseDefaulted());
        assertEquals(true, entries.get(11).isDueBecauseDefaulted());
    }
}
