package bank;

import bank.demo.DemoDataSeeder;
import bank.model.Bank;
import bank.model.CreditBand;
import bank.model.Customer;
import bank.model.LoanStatus;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests that the 11 demo customers end up in the bands they were designed for.
class DemoDataTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static Bank bank;

    @BeforeAll
    static void seed() {
        bank = new Bank(new BigDecimal("100000"));
        DemoDataSeeder.seed(bank, TODAY);
    }

    private CreditBand band(String customerId) throws Exception {
        return bank.scoreOf(bank.findCustomerById(customerId), TODAY).getBand();
    }

    private int score(String customerId) throws Exception {
        return bank.scoreOf(bank.findCustomerById(customerId), TODAY).getScore();
    }

    @Test
    void elevenCustomersAreCreated() {
        assertEquals(11, bank.getAllCustomers().size());
        assertTrue(DemoDataSeeder.isLoaded(bank));
    }

    @Test
    void reliableBorrowers_areExcellent() throws Exception {
        assertEquals(CreditBand.EXCELLENT, band("D001"));
        assertEquals(CreditBand.EXCELLENT, band("D002"));
        assertEquals(825, score("D001"));
    }

    @Test
    void mostlyReliableBorrowers_areGood() throws Exception {
        assertEquals(CreditBand.GOOD, band("D003"));
        assertEquals(CreditBand.GOOD, band("D004"));
        assertEquals(CreditBand.GOOD, band("D005"));
        assertEquals(725, score("D003"));
    }

    @Test
    void newCustomers_areFairAtSixHundred() throws Exception {
        assertEquals(CreditBand.FAIR, band("D006"));
        assertEquals(CreditBand.FAIR, band("D007"));
        assertEquals(600, score("D006"));
        assertTrue(bank.findCustomerById("D006").getLoans().isEmpty());
    }

    @Test
    void mixedRecord_isFair() throws Exception {
        assertEquals(CreditBand.FAIR, band("D008"));
        assertEquals(645, score("D008"));
    }

    @Test
    void strugglingBorrowers_arePoor_withAnOverdueInstallment() throws Exception {
        assertEquals(CreditBand.POOR, band("D009"));
        assertEquals(CreditBand.POOR, band("D010"));

        for (String id : new String[] { "D009", "D010" }) {
            Customer customer = bank.findCustomerById(id);
            assertEquals(1, customer.getActiveLoans().size());
            assertTrue(customer.getActiveLoans().get(0).hasOverdueInstallments());
        }
    }

    @Test
    void theDefaultedCustomer_isPoor_andTheirLoanDefaulted() throws Exception {
        assertEquals(CreditBand.POOR, band("D011"));
        assertEquals(LoanStatus.DEFAULTED, bank.findLoanById("D011-A").getStatus());
        assertEquals(350, score("D011"));
    }

    @Test
    void paidOffLoans_arePaidOff() throws Exception {
        assertEquals(LoanStatus.PAID_OFF, bank.findLoanById("D001-A").getStatus());
        assertEquals(LoanStatus.PAID_OFF, bank.findLoanById("D001-C").getStatus());
        assertEquals(LoanStatus.ACTIVE, bank.findLoanById("D001-D").getStatus());
    }

    @Test
    void demoCustomersHaveLogins_thatMustBeReset() {
        var account = bank.findUserForCustomer("D001");

        assertNotNull(account);
        assertTrue(account.isMustChangePassword());
    }

    @Test
    void theBankStillHasMoneyToLend() {
        assertTrue(bank.getAvailableFunds().compareTo(new BigDecimal("50000")) > 0);
    }

    @Test
    void loadingTwice_isRefused() {
        assertThrows(IllegalStateException.class, () -> DemoDataSeeder.seed(bank, TODAY));
    }

    @Test
    void aBankWithTooLittleMoney_isRefused() {
        Bank poorBank = new Bank(new BigDecimal("1000"));

        assertThrows(IllegalStateException.class, () -> DemoDataSeeder.seed(poorBank, TODAY));
        assertFalse(DemoDataSeeder.isLoaded(poorBank));
        assertTrue(poorBank.getAllCustomers().isEmpty());
    }
}
