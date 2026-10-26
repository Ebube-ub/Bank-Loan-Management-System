package bank.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import bank.exception.CustomerNotFoundException;
import bank.model.Bank;
import bank.model.Customer;
import bank.model.Installment;
import bank.model.Loan;

// Creates 11 made-up customers with loan histories, so credit scores have
// something to read. The loans are created with dates in the past and their
// installments are paid on time, late, or not at all, to give each customer
// the record described below. Dates are worked out from today, so the
// overdue and default statuses come out right whenever this runs.
//
//   D001, D002  reliable: 3 loans paid off on time, 1 active loan paid on time   -> Excellent
//   D003-D005   mostly reliable: 1 loan paid off, 1 active, 2 payments late      -> Good
//   D006, D007  new customers with no loans                                      -> Fair
//   D008        mixed: 12 installments, 4 paid late                              -> Fair
//   D009, D010  struggling: some late payments and 1 overdue installment now     -> Poor
//   D011        defaulted: missed 3 installments in a row                        -> Poor
//
// The customers get logins too, but their passwords are random and not shown:
// use "Reset password" on the Customers page to get one.
public class DemoDataSeeder {

    private static final BigDecimal RATE = new BigDecimal("0.20");

    // the demo loans lend out roughly this much, so the bank needs it available
    private static final BigDecimal FUNDS_NEEDED = new BigDecimal("85000");

    private static final int LATE_BY_DAYS = 5;

    public static boolean isLoaded(Bank bank) {
        try {
            bank.findCustomerById("D001");
            return true;
        } catch (CustomerNotFoundException e) {
            return false;
        }
    }

    // returns how many customers were added
    public static int seed(Bank bank, LocalDate today) {

        if (isLoaded(bank)) {
            throw new IllegalStateException("The demo customers are already loaded.");
        }

        if (bank.getAvailableFunds().compareTo(FUNDS_NEEDED) < 0) {
            throw new IllegalStateException("The bank needs at least " + Bank.naira(FUNDS_NEEDED)
                    + " available to create the demo loans.");
        }

        try {
            // reliable: three loans paid off on time, then an active one paid on time so far
            for (String[] who : new String[][] {
                    { "D001", "Chinedu Okafor", "20000" },
                    { "D002", "Amara Nwosu", "15000" } }) {

                String id = who[0];
                addCustomer(bank, id, who[1]);

                Loan first = loan(bank, today, id, id + "-A", who[2], 4, 17, 3);
                pay(bank, first, 4);
                Loan second = loan(bank, today, id, id + "-B", who[2], 4, 12, 3);
                pay(bank, second, 4);
                Loan third = loan(bank, today, id, id + "-C", who[2], 4, 7, 3);
                pay(bank, third, 4);

                Loan active = loan(bank, today, id, id + "-D", "8000", 6, 2, 2);
                pay(bank, active, 2);
            }

            // mostly reliable: one loan paid off (one late payment), one active (one late payment)
            String[][] mostlyReliable = {
                    { "D003", "Tunde Bello", "15000", "3", "2" },
                    { "D004", "Ngozi Eze", "12000", "1", "4" },
                    { "D005", "Emeka Obi", "10000", "5", "6" } };

            for (String[] who : mostlyReliable) {
                String id = who[0];
                addCustomer(bank, id, who[1]);

                Loan paidOff = loan(bank, today, id, id + "-A", who[2], 6, 14, 3);
                pay(bank, paidOff, 6, Integer.parseInt(who[3]));

                Loan active = loan(bank, today, id, id + "-B", "8000", 12, 8, 3);
                pay(bank, active, 8, Integer.parseInt(who[4]));
            }

            // new customers: nothing to read yet
            addCustomer(bank, "D006", "Folake Adeyemi");
            addCustomer(bank, "D007", "Ibrahim Musa");

            // mixed: 12 installments, 4 of them paid late, loan fully paid in the end
            addCustomer(bank, "D008", "Zainab Yusuf");
            Loan mixed = loan(bank, today, "D008", "D008-A", "8000", 12, 12, 3);
            pay(bank, mixed, 12, 2, 5, 8, 11);

            // struggling: late payments, and the last installment is overdue right now
            addCustomer(bank, "D009", "Kelechi Anyanwu");
            Loan struggling1 = loan(bank, today, "D009", "D009-A", "10000", 8, 8, 14);
            pay(bank, struggling1, 7, 2, 4, 6);

            addCustomer(bank, "D010", "Segun Alade");
            Loan struggling2 = loan(bank, today, "D010", "D010-A", "12000", 6, 6, 12);
            pay(bank, struggling2, 5, 2, 4, 5);

            // defaulted: pays 2, then misses 3 in a row
            addCustomer(bank, "D011", "Obinna Chukwu");
            Loan defaulted = loan(bank, today, "D011", "D011-A", "10000", 6, 5, 10);
            pay(bank, defaulted, 2);

            // the loan defaulted the day after its 5th installment was missed
            List<Installment> installments = defaulted.getInstallments();
            bank.refreshDelinquencyStatus(installments.get(4).getDueDate().plusDays(1));

            bank.refreshDelinquencyStatus(today);

        } catch (Exception e) {
            throw new IllegalStateException("The demo data could not be created: " + e.getMessage(), e);
        }

        return 11;
    }


    // ---------- Examples for the Dashboard's watch list ----------
    //
    // The 11 customers above have installments that are weeks away, so on most days
    // nobody is "due within 7 days" and the watch list is empty. These 5 extra
    // customers each have one situation the watch list looks for, dated from today
    // (so they show up on the day they are created, and about a week after that the
    // dates have moved on):
    //
    //   D012  paid on the due date 3 times in a row, next one due in 2 days   -> pays on the last day
    //   D013  paid one installment late, next one due in 3 days               -> has paid late before
    //   D014  nothing special, an installment due in 5 days                   -> due soon
    //   D015  one loan is overdue, another has an installment due in 12 days  -> another loan overdue
    //   D016  credit score was 30 points higher 12 days ago, due in 6 days    -> score dropped
    private static final BigDecimal EXAMPLES_FUNDS_NEEDED = new BigDecimal("72000");

    public static boolean watchListExamplesLoaded(Bank bank) {
        try {
            bank.findCustomerById("D012");
            return true;
        } catch (CustomerNotFoundException e) {
            return false;
        }
    }

    // Adds D012 to D016. Does nothing (returns 0) if the demo customers are not loaded,
    // if these are already there, or if the bank has too little money for the loans.
    // Returns how many customers were added.
    public static int seedWatchListExamples(Bank bank, LocalDate today) {

        if (!isLoaded(bank) || watchListExamplesLoaded(bank)
                || bank.getAvailableFunds().compareTo(EXAMPLES_FUNDS_NEEDED) < 0) {
            return 0;
        }

        try {
            addCustomer(bank, "D012", "Ifeanyi Okoro");
            pay(bank, loanDueIn(bank, today, "D012", "D012-A", 6, 4, 2), 3);

            addCustomer(bank, "D013", "Chioma Nnadi");
            pay(bank, loanDueIn(bank, today, "D013", "D013-A", 6, 2, 3), 1, 1);

            addCustomer(bank, "D014", "Bashir Lawal");
            loanDueIn(bank, today, "D014", "D014-A", 6, 1, 5);

            // the new loan is made first: a customer who is already overdue cannot borrow again
            addCustomer(bank, "D015", "Yetunde Bakare");
            loanDueIn(bank, today, "D015", "D015-B", 6, 1, 12);
            loanDueIn(bank, today, "D015", "D015-A", 6, 1, -5);

            addCustomer(bank, "D016", "Femi Adebayo");
            loanDueIn(bank, today, "D016", "D016-A", 6, 1, 6);
            int scoreNow = bank.scoreOf(bank.findCustomerById("D016"), today).getScore();
            bank.recordScore("D016", today.minusDays(12), scoreNow + 30);

            bank.refreshDelinquencyStatus(today);

        } catch (Exception e) {
            throw new IllegalStateException("The watch list examples could not be created: " + e.getMessage(), e);
        }

        return 5;
    }

    // A loan of 12,000 whose installment number `dueNumber` falls `daysFromToday` days
    // from today (earlier installments are in the past, later ones in the future).
    private static Loan loanDueIn(Bank bank, LocalDate today, String customerId, String loanId,
            int termMonths, int dueNumber, int daysFromToday) throws Exception {

        LocalDate disbursed = today.plusDays(daysFromToday).minusMonths(dueNumber);
        return bank.applyForLoan(customerId, loanId, new BigDecimal("12000"), RATE, termMonths, disbursed);
    }

    private static void addCustomer(Bank bank, String id, String name) throws Exception {
        String email = name.toLowerCase().replace(' ', '.') + "@example.com";
        bank.registerCustomerWithLogin(new Customer(id, name, email));
    }

    // A loan disbursed monthsAgo months ago, plus a few extra days so that
    // every installment due so far is already in the past.
    private static Loan loan(Bank bank, LocalDate today, String customerId, String loanId,
            String principal, int termMonths, int monthsAgo, int extraDays) throws Exception {

        LocalDate disbursed = today.minusMonths(monthsAgo).minusDays(extraDays);
        return bank.applyForLoan(customerId, loanId, new BigDecimal(principal), RATE, termMonths, disbursed);
    }

    // Pays the first `count` installments in order. Installments listed in
    // lateOnes (counting from 1) are paid 5 days after their due date; the rest
    // are paid on the due date.
    private static void pay(Bank bank, Loan loan, int count, int... lateOnes) throws Exception {

        for (int i = 0; i < count; i++) {
            Installment installment = loan.getInstallments().get(i);

            LocalDate paymentDate = installment.getDueDate();
            if (contains(lateOnes, i + 1)) {
                paymentDate = paymentDate.plusDays(LATE_BY_DAYS);
            }

            // a late installment gets its late fee when it is first seen overdue,
            // so do that check first and then pay the amount that is really due
            bank.refreshDelinquencyStatus(paymentDate);

            bank.makePayment(loan.getLoanId(), installment.getAmountDue(), paymentDate, "demo data", false);
        }
    }

    private static boolean contains(int[] numbers, int wanted) {
        for (int number : numbers) {
            if (number == wanted) {
                return true;
            }
        }
        return false;
    }
}
