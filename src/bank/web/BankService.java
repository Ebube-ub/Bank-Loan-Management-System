package bank.web;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDate;

import bank.demo.DemoDataSeeder;
import bank.model.Bank;
import bank.persistence.BankFileRepository;

// The one place the web server touches the Bank object.
//
// Every request does its work inside read(...) or change(...). Both hold a lock,
// so if two requests arrive at once they take turns instead of changing the
// bank at the same time. Both also refresh the overdue / defaulted statuses
// first, which is what the console's menu loop did before each menu.
//
//  read(...)   just looks at the bank
//  change(...) changes it, then saves bank.dat straight away (a website has no "Exit")
//
// Doing the work (including building the JSON answer) inside the lock means
// a request never sees the bank half-changed by another one.
public class BankService {

    // A piece of work to do on the bank. It may throw any of the bank's exceptions.
    public interface BankAction<T> {
        T run(Bank bank) throws Exception;
    }

    private final BankFileRepository repository = new BankFileRepository();
    private final String dataFile;
    private final Bank bank;

    // the admin's first temporary password; shown once in the terminal, then cleared
    private String firstAdminPassword;

    public BankService(String dataFile) {
        this.dataFile = dataFile;
        this.bank = loadBank();
        prepareFirstRun();
    }

    public synchronized <T> T read(BankAction<T> action) throws Exception {
        LocalDate today = LocalDate.now();
        bank.refreshDelinquencyStatus(today);
        bank.recordScoreSnapshots(today); // kept in memory; saved with the next change
        return action.run(bank);
    }

    public synchronized <T> T change(BankAction<T> action) throws Exception {
        LocalDate today = LocalDate.now();
        bank.refreshDelinquencyStatus(today);
        bank.recordScoreSnapshots(today);
        T result = action.run(bank);
        repository.save(bank, dataFile);
        return result;
    }

    // returns the admin's temporary password the first time it is asked for, then null
    public synchronized String takeFirstAdminPassword() {
        String password = firstAdminPassword;
        firstAdminPassword = null;
        return password;
    }

    // A brand-new bank gets the demo customers so there is something to look
    // at, and a bank with no admin login gets one (with a random password).
    private void prepareFirstRun() {
        boolean changed = false;

        // a bank started with less than the current starting money is topped up once
        if (bank.increaseCapitalTo(Bank.STARTING_CAPITAL)) {
            System.out.println("The bank's money was increased to " + Bank.naira(Bank.STARTING_CAPITAL) + ".");
            changed = true;
        }

        if (bank.getAllCustomers().isEmpty() && bank.getAllLoans().isEmpty()) {
            DemoDataSeeder.seed(bank, LocalDate.now());
            System.out.println("Demo customers D001 to D011 were created.");
            changed = true;
        }

        // Demo customers D012 to D016 give the Dashboard's watch list something to show.
        // A bank that already has the 11 demo customers gets them too, once.
        if (DemoDataSeeder.seedWatchListExamples(bank, LocalDate.now()) > 0) {
            System.out.println("Watch list examples D012 to D016 were created.");
            changed = true;
        }

        if (!bank.hasAdmin()) {
            firstAdminPassword = bank.createAdmin("admin");
            changed = true;
        }

        // one credit score per customer per day, so the early-warning list can see a drop
        if (bank.recordScoreSnapshots(LocalDate.now())) {
            changed = true;
        }

        if (changed) {
            try {
                repository.save(bank, dataFile);
            } catch (IOException e) {
                System.out.println("Could not save bank state: " + e.getMessage());
            }
        }
    }

    // same rules as Main.loadBank: use the saved file if there is one,
    // otherwise start a new bank
    private Bank loadBank() {
        try {
            Bank loaded = repository.load(dataFile);
            System.out.println("Previous bank state loaded from " + dataFile + ".");
            return loaded;
        } catch (FileNotFoundException e) {
            System.out.println("No saved bank state found. Starting a new bank.");
            return new Bank(Bank.STARTING_CAPITAL);
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(
                    "Could not read saved bank state (" + e.getMessage() + "). Starting a new bank."
            );
            return new Bank(Bank.STARTING_CAPITAL);
        }
    }
}
