package bank;

import bank.model.Bank;
import bank.model.Customer;
import bank.model.FinanceReport;
import bank.model.Installment;
import bank.model.InstallmentStatus;
import bank.model.Loan;
import bank.model.LoanStatus;
import bank.model.PaymentResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Tests for the prepayment charge: 2% of any money that pays installments which are
// not due yet (except the next one in line), taken out of the payment.
class PrepaymentPenaltyTest {

    private final LocalDate today = LocalDate.now();
    private Bank bank;
    private Loan loan;

    @BeforeEach
    void setUp() throws Exception {
        bank = new Bank(new BigDecimal("1000000"));
        bank.registerCustomer(new Customer("C1", "Test C1", "c1@example.com"));

        // 1200 at 12% for 12 months: 112.00 an installment, the first due in a month
        loan = bank.applyForLoan("C1", "L1", money("1200"), money("0.12"), 12, today);
    }

    private BigDecimal money(String text) {
        return new BigDecimal(text);
    }

    private PaymentResult pay(String amount) throws Exception {
        return bank.makePaymentDetailed("L1", money(amount), today, "test", false);
    }

    @Test
    void theCharge_isTwoPercent() {
        assertEquals(0, money("0.02").compareTo(Bank.PREPAYMENT_FEE_RATE));
    }

    @Test
    void payingTheNextInstallmentEarly_hasNoCharge() throws Exception {
        PaymentResult result = pay("112.00");

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getPrepaymentFee()));
        assertEquals(0, money("112.00").compareTo(result.getApplied()));
        assertEquals(InstallmentStatus.PAID, loan.getInstallments().get(0).getStatus());
    }

    @Test
    void payingMoreThanTheNextInstallment_chargesTheExtraThatPaysLaterOnes() throws Exception {
        // 112.00 pays installment 1 for free, then installment 2 is paid ahead:
        // it costs 112.00 + 2.24 = 114.24
        PaymentResult result = pay("226.24");

        assertEquals(0, money("224.00").compareTo(result.getApplied()));
        assertEquals(0, money("2.24").compareTo(result.getPrepaymentFee()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getOverpayment()));

        assertEquals(InstallmentStatus.PAID, loan.getInstallments().get(0).getStatus());
        assertEquals(InstallmentStatus.PAID, loan.getInstallments().get(1).getStatus());
        assertEquals(InstallmentStatus.PENDING, loan.getInstallments().get(2).getStatus());
    }

    @Test
    void aPartPaymentAhead_isSplitBetweenTheInstallmentAndTheCharge() throws Exception {
        // 112.00 clears installment 1; the other 51.00 goes towards installment 2
        PaymentResult result = pay("163.00");

        // 51.00 / 1.02 = 50.00, so the charge is 1.00
        assertEquals(0, money("162.00").compareTo(result.getApplied()));
        assertEquals(0, money("1.00").compareTo(result.getPrepaymentFee()));
        assertEquals(0, money("50.00").compareTo(loan.getInstallments().get(1).getAmountPaid()));
    }

    @Test
    void theChargeComesOutOfThePayment_soThePaymentCostsExactlyWhatWasPaid() throws Exception {
        BigDecimal fundsBefore = bank.getAvailableFunds();
        BigDecimal owedBefore = loan.getOutstandingBalance();

        PaymentResult result = pay("500.00");

        BigDecimal total = result.getApplied().add(result.getPrepaymentFee()).add(result.getOverpayment());
        assertEquals(0, money("500.00").compareTo(total));

        // the bank received the whole 500.00; the loan went down by the part that was applied
        assertEquals(0, fundsBefore.add(money("500.00")).compareTo(bank.getAvailableFunds()));
        assertEquals(0, owedBefore.subtract(result.getApplied()).compareTo(loan.getOutstandingBalance()));
    }

    @Test
    void anInstallmentThatIsDueOnThePaymentDate_isNotPaidAhead() throws Exception {
        Installment first = loan.getInstallments().get(0);
        Installment second = loan.getInstallments().get(1);

        // On the day installment 2 is due, installment 1 is overdue (with its late fee and
        // interest), and installment 2 (112.00) is due that day, so it is not paid ahead.
        bank.refreshDelinquencyStatus(second.getDueDate());
        BigDecimal both = first.getAmountDue().add(second.getAmountDue());
        PaymentResult result = bank.makePaymentDetailed("L1", both, second.getDueDate(), "test", false);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getPrepaymentFee()));
        assertEquals(InstallmentStatus.PAID, first.getStatus());
        assertEquals(InstallmentStatus.PAID, second.getStatus());
    }

    @Test
    void payingOffTheWholeLoanEarly_chargesTwoPercentOfAllButTheNextInstallment() throws Exception {
        // 11 installments of 112.00 come after the next one: 11 x 2.24 = 24.64
        BigDecimal fee = loan.payoffFee(today, Bank.PREPAYMENT_FEE_RATE);
        BigDecimal payoff = loan.payoffAmount(today, Bank.PREPAYMENT_FEE_RATE);

        assertEquals(0, money("24.64").compareTo(fee));
        assertEquals(0, loan.getOutstandingBalance().add(fee).compareTo(payoff));

        PaymentResult result = bank.makePaymentDetailed("L1", payoff, today, "test", false);

        assertEquals(LoanStatus.PAID_OFF, loan.getStatus());
        assertEquals(0, fee.compareTo(result.getPrepaymentFee()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getOverpayment()));
    }

    @Test
    void theLastInstallmentLeft_hasNoChargeToPayOff() throws Exception {
        // 2 installments: while both are unpaid, the second is paid ahead
        Loan short2 = bank.applyForLoan("C1", "L2", money("1000"), money("0.12"), 2, today);
        assertEquals(0, money("10.20").compareTo(short2.payoffFee(today, Bank.PREPAYMENT_FEE_RATE)));

        // once the first is paid, the second is the next one in line, so it is not charged
        bank.makePaymentDetailed("L2", short2.getInstallments().get(0).getAmountDue(), today, "test", false);
        assertEquals(0, BigDecimal.ZERO.compareTo(short2.payoffFee(today, Bank.PREPAYMENT_FEE_RATE)));
    }

    @Test
    void theCharge_countsAsMoneyEarned_inTheMonthOfThePayment() throws Exception {
        pay("226.24"); // 2.24 of it is the charge

        FinanceReport report = bank.financeReport(today);

        assertEquals(0, money("2.24").compareTo(report.getPrepaymentFees()));
        assertEquals(0, report.getInterest().add(report.getLateFees()).add(money("2.24"))
                .compareTo(report.getRevenue()));
    }

    @Test
    void thePaymentRecord_showsWhatWasPaidAndTheCharge() throws Exception {
        pay("226.24");

        var records = bank.paymentsForLoan("L1");
        assertEquals(1, records.size());
        assertEquals(0, money("226.24").compareTo(records.get(0).getTotalPaid()));
        assertEquals(0, money("2.24").compareTo(records.get(0).getPrepaymentFee()));
        assertEquals(0, money("224.00").compareTo(records.get(0).getAmountApplied()));
    }
}
