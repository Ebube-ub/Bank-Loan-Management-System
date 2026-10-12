package bank.web;

import bank.exception.CustomerAlreadyExistsException;
import bank.exception.LoanLimitExceededException;
import bank.model.CreditTier;
import bank.model.Loan;
import bank.model.LoanStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Tests for BankService: mainly that every change is saved to the data
// file straight away, since the web version has no "Exit" step.
class BankServiceTest {

    // JUnit makes a fresh empty folder for each test, so we never touch
    // the real bank.dat
    @TempDir
    Path tempFolder;

    private String dataFile;
    private BankService service;

    @BeforeEach
    void setUp() {
        dataFile = tempFolder.resolve("test-bank.dat").toString();
        service = new BankService(dataFile);
    }

    @Test
    void newCustomer_isSavedImmediately() throws Exception {
        service.registerCustomer("C1", "Ada", "ada@example.com", CreditTier.STANDARD);

        // a second service reading the same file should see the customer
        BankService reloaded = new BankService(dataFile);

        assertEquals(1, reloaded.getCustomers().size());
        assertEquals("Ada", reloaded.getCustomers().get(0).getName());
    }

    @Test
    void loanAndPayment_areSavedImmediately() throws Exception {
        service.registerCustomer("C1", "Ada", "ada@example.com", CreditTier.STANDARD);
        service.applyForLoan("C1", "L1", new BigDecimal("100"), new BigDecimal("0.1"), 2);
        service.makePayment("L1", new BigDecimal("101.67"));

        BankService reloaded = new BankService(dataFile);
        Loan loan = reloaded.getLoan("L1");

        assertEquals(LoanStatus.PAID_OFF, loan.getStatus());
        assertEquals(0, loan.getOutstandingBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void duplicateCustomer_throwsSameExceptionAsConsole() throws Exception {
        service.registerCustomer("C1", "Ada", "ada@example.com", CreditTier.STANDARD);

        assertThrows(CustomerAlreadyExistsException.class, () ->
                service.registerCustomer("C1", "Someone else", "x@example.com", CreditTier.PREMIUM));
    }

    @Test
    void checkEligibility_failsAtTierLimit() throws Exception {
        service.registerCustomer("C1", "Ada", "ada@example.com", CreditTier.STANDARD);
        service.applyForLoan("C1", "L1", new BigDecimal("100"), new BigDecimal("0.1"), 2);
        service.applyForLoan("C1", "L2", new BigDecimal("100"), new BigDecimal("0.1"), 2);

        assertThrows(LoanLimitExceededException.class, () -> service.checkEligibility("C1"));
    }
}
