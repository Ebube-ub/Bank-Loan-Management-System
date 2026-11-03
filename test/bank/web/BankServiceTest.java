package bank.web;

import bank.model.Customer;
import bank.model.LoanApplication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests for BankService: that a new bank starts with demo customers and an
// admin login, and that every change is saved to the data file straight away,
// since the web version has no "Exit" step.
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
    void newBank_startsWithDemoCustomersAndAnAdminLogin() throws Exception {
        int customers = service.read(bank -> bank.getAllCustomers().size());
        boolean hasAdmin = service.read(bank -> bank.hasAdmin());

        assertEquals(16, customers); // 11 demo customers + 5 watch list examples
        assertTrue(hasAdmin);
    }

    @Test
    void firstAdminPassword_isGivenOnlyOnce() {
        assertNotNull(service.takeFirstAdminPassword());
        assertNull(service.takeFirstAdminPassword());
    }

    @Test
    void change_isSavedImmediately() throws Exception {
        service.change(bank -> bank.registerCustomerWithLogin(new Customer("T1", "Test One", "t1@example.com")));

        // a second service reading the same file should see the customer
        BankService reloaded = new BankService(dataFile);

        int customers = reloaded.read(bank -> bank.getAllCustomers().size());
        boolean found = reloaded.read(bank -> bank.findCustomerById("T1") != null);

        assertEquals(17, customers); // 16 demo customers + T1, and no second round of demo data
        assertTrue(found);
    }

    @Test
    void reloadedBank_keepsLoginsAndApplications() throws Exception {
        String temporary = service.change(bank ->
                bank.registerCustomerWithLogin(new Customer("T1", "Test One", "t1@example.com")));

        service.change(bank -> bank.submitApplication("T1", new BigDecimal("5000"), null, 6, LocalDate.now()));

        BankService reloaded = new BankService(dataFile);

        assertNotNull(reloaded.read(bank -> bank.authenticate("t1", temporary)));

        int applications = reloaded.read(bank -> bank.applicationsForCustomer("T1").size());
        assertEquals(1, applications);
    }

    @Test
    void readThatThrows_doesNotSaveOrBreakTheService() throws Exception {
        assertThrows(Exception.class, () -> service.change(bank -> {
            throw new IllegalStateException("boom");
        }));

        // still usable afterwards
        int customers = service.read(bank -> bank.getAllCustomers().size());
        assertEquals(16, customers);
    }
}
