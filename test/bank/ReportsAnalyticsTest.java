package bank;

import bank.model.Bank;
import bank.model.CreditBand;
import bank.model.Customer;
import bank.model.Loan;
import bank.model.MonthlyLending;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests for the numbers behind the charts on the Reports screen.
class ReportsAnalyticsTest {

    private final LocalDate today = LocalDate.now();
    private Bank bank;

    @BeforeEach
    void setUp() throws Exception {
        bank = new Bank(new BigDecimal("100000"));
    }

    private BigDecimal money(String text) {
        return new BigDecimal(text);
    }

    // ---------- Customers by credit band ----------

    @Test
    void customersByBand_listsEveryBand_evenWithNoCustomers() {
        Map<CreditBand, Integer> counts = bank.customersByBand(today);

        assertEquals(CreditBand.values().length, counts.size());
        for (CreditBand band : CreditBand.values()) {
            assertEquals(0, counts.get(band));
        }
    }

    @Test
    void customersByBand_countsEveryCustomerOnce() throws Exception {
        bank.registerCustomer(new Customer("C1", "Test C1", "c1@example.com"));
        bank.registerCustomer(new Customer("C2", "Test C2", "c2@example.com"));
        bank.registerCustomer(new Customer("C3", "Test C3", "c3@example.com"));

        int total = 0;
        for (int count : bank.customersByBand(today).values()) {
            total += count;
        }

        assertEquals(3, total);
    }

    // ---------- Lent vs earned per month ----------

    @Test
    void lendingByMonth_emptyBank_hasOnlyThisMonth_withZeros() {
        List<MonthlyLending> months = bank.lendingByMonth(today);

        assertEquals(1, months.size());
        assertEquals(YearMonth.from(today), months.get(0).getMonth());
        assertEquals(0, BigDecimal.ZERO.compareTo(months.get(0).getLent()));
        assertEquals(0, BigDecimal.ZERO.compareTo(months.get(0).getEarned()));
    }

    @Test
    void lendingByMonth_putsALoanInTheMonthItWasHandedOut() throws Exception {
        bank.registerCustomer(new Customer("C1", "Test C1", "c1@example.com"));
        bank.applyForLoan("C1", "L1", money("1000"), money("0.2"), 2, today.minusMonths(2));

        List<MonthlyLending> months = bank.lendingByMonth(today);

        // two months ago, last month and this month, oldest first
        assertEquals(3, months.size());
        assertEquals(YearMonth.from(today).minusMonths(2), months.get(0).getMonth());
        assertEquals(0, money("1000").compareTo(months.get(0).getLent()));
        assertEquals(0, BigDecimal.ZERO.compareTo(months.get(1).getLent()));
        assertEquals(0, BigDecimal.ZERO.compareTo(months.get(2).getLent()));
    }

    @Test
    void lendingByMonth_earnedMatchesTheFinanceReport() throws Exception {
        bank.registerCustomer(new Customer("C1", "Test C1", "c1@example.com"));
        Loan loan = bank.applyForLoan("C1", "L1", money("1000"), money("0.2"), 2, today.minusMonths(2));

        // pay the first installment on its due date, so there is no late fee
        LocalDate dueDate = loan.getInstallments().get(0).getDueDate();
        bank.refreshDelinquencyStatus(dueDate);
        bank.makePayment("L1", loan.getInstallments().get(0).getAmountDue(), dueDate, "test", false);

        BigDecimal earnedInChart = BigDecimal.ZERO;
        for (MonthlyLending month : bank.lendingByMonth(today)) {
            earnedInChart = earnedInChart.add(month.getEarned());
        }

        assertTrue(earnedInChart.signum() > 0);
        assertEquals(0, bank.financeReport(today).getRevenue().compareTo(earnedInChart));
    }

    @Test
    void lendingByMonth_showsNoMoreThanTwelveMonths() throws Exception {
        bank.registerCustomer(new Customer("C1", "Test C1", "c1@example.com"));
        bank.applyForLoan("C1", "L1", money("1000"), money("0.2"), 2, today.minusMonths(30));

        List<MonthlyLending> months = bank.lendingByMonth(today);

        assertEquals(12, months.size());
        assertEquals(YearMonth.from(today), months.get(11).getMonth());
    }
}
