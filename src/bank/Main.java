package bank;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

import bank.exception.ApplicationNotFoundException;
import bank.exception.CustomerAlreadyExistsException;
import bank.exception.CustomerNotFoundException;
import bank.exception.InsufficientFundsException;
import bank.exception.LoanLimitExceededException;
import bank.exception.LoanNotFoundException;
import bank.exception.OverdueInstallmentsException;
import bank.model.ApplicationStatus;
import bank.model.Bank;
import bank.model.Customer;
import bank.model.Installment;
import bank.model.Loan;
import bank.model.PaymentResult;
import bank.model.LoanApplication;
import bank.model.LoanStatus;
import bank.model.LoanStatusSummary;
import bank.model.OverdueEntry;
import bank.persistence.BankFileRepository;

public class Main {

    private static final String DATA_FILE = "bank.dat";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static Scanner scanner = new Scanner(System.in);
    private static BankFileRepository repository = new BankFileRepository();

    public static void main(String[] args) {

        System.out.println("Bank Loan Management System");

        Bank bank = loadBank();

        boolean running = true;

        while (running) {

            // check for newly-overdue installments / defaulted loans every
            // time before showing the menu, so reports and checks use fresh data
            bank.refreshDelinquencyStatus(LocalDate.now());

            printMenu();
            int choice = readInt("Enter your choice: ");

            switch (choice) {
                case 1:
                    registerCustomer(bank);
                    break;
                case 2:
                    applyForLoan(bank);
                    break;
                case 3:
                    makePayment(bank);
                    break;
                case 4:
                    searchLoans(bank);
                    break;
                case 5:
                    viewInstallments(bank);
                    break;
                case 6:
                    showReports(bank);
                    break;
                case 7:
                    running = false;
                    System.out.println("Exiting...");
                    break;
                default:
                    System.out.println("Invalid choice.");
            }
        }

        saveBank(bank);
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("1. Register Customer");
        System.out.println("2. Apply for Loan");
        System.out.println("3. Make Payment");
        System.out.println("4. Search Loans");
        System.out.println("5. View Loan Installments");
        System.out.println("6. Reports");
        System.out.println("7. Exit");
    }

    // ---------- Menu actions ----------

    private static void registerCustomer(Bank bank) {

        String customerId = readLine("Enter customer ID: ");
        String name = readLine("Enter customer name: ");
        String email = readLine("Enter customer email: ");

        // the customer's tier is not chosen here: it follows their credit score
        try {
            String temporaryPassword = bank.registerCustomerWithLogin(new Customer(customerId, name, email));
            System.out.println("Customer registered successfully!");
            System.out.println("Login username: " + customerId.toLowerCase());
            System.out.println("Temporary password: " + temporaryPassword + " (shown only once)");
        } catch (CustomerAlreadyExistsException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // The console acts as the admin: it records the application, shows the
    // system's advice, and asks whether to approve it.
    private static void applyForLoan(Bank bank) {

        String customerId = readLine("Enter customer ID: ");

        // stop straight away if the customer can't take another loan,
        // before asking for the rest of the details
        try {
            bank.checkActiveLoanLimit(customerId);
        } catch (CustomerNotFoundException | LoanLimitExceededException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        BigDecimal principal = readAmount("Enter loan amount: ");
        BigDecimal interestRate = readAmount("Enter annual interest rate (e.g. 0.12 for 12%): ");
        int termMonths = readInt("Enter term in months: ");

        try {
            LoanApplication application = bank.submitApplication(
                    customerId, principal, interestRate, termMonths, LocalDate.now());

            System.out.println("Application " + application.getApplicationId() + ": advice is "
                    + application.getAdvice() + " (score " + application.getScoreAtSubmission()
                    + ", " + application.getBandAtSubmission() + " band)");
            for (String reason : application.getAdviceReasons()) {
                System.out.println(" - " + plain(reason));
            }

            if (application.getStatus() == ApplicationStatus.DECLINED) {
                System.out.println("The application was declined.");
                return;
            }

            String answer = readLine("Approve this loan? (y/n): ");
            if (!answer.equalsIgnoreCase("y")) {
                bank.declineApplication(application.getApplicationId(), "Declined from the console.",
                        "console", LocalDate.now());
                System.out.println("The application was declined.");
                return;
            }

            Loan loan = bank.approveApplication(application.getApplicationId(), null, "console", LocalDate.now());

            System.out.println("Loan approved successfully!");
            System.out.println("Loan ID: " + loan.getLoanId());
            System.out.println("Principal: " + money(loan.getPrincipal()));
            System.out.println("Total repayable: " + money(loan.getTotalRepayable()));
            System.out.println("Number of installments: " + loan.getInstallments().size());

        } catch (CustomerNotFoundException | ApplicationNotFoundException | InsufficientFundsException
                | LoanLimitExceededException | OverdueInstallmentsException
                | IllegalArgumentException e) {
            System.out.println("Error: " + plain(e.getMessage()));
        }
    }

    private static void makePayment(Bank bank) {

        String loanId = readLine("Enter loan ID: ");
        BigDecimal paymentAmount = readAmount("Enter payment amount: ");

        try {
            PaymentResult result = bank.makePaymentDetailed(
                    loanId, paymentAmount, LocalDate.now(), "console", false);
            System.out.println("Payment applied successfully!");

            if (result.getPrepaymentFee().compareTo(BigDecimal.ZERO) > 0) {
                System.out.println(
                        "Note: " + money(result.getPrepaymentFee())
                        + " of it was a prepayment charge, for paying installments before they are due."
                );
            }

            if (result.getOverpayment().compareTo(BigDecimal.ZERO) > 0) {
                System.out.println(
                        "Note: " + money(result.getOverpayment())
                        + " was not applied because the loan is now fully paid."
                );
            }

            Loan loan = bank.findLoanById(loanId);
            System.out.println("Loan status: " + loan.getStatus());
            System.out.println("Outstanding balance: " + money(loan.getOutstandingBalance()));

        } catch (LoanNotFoundException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void searchLoans(Bank bank) {

        System.out.println("1. Search by Customer");
        System.out.println("2. Search by Status");
        System.out.println("3. All loans, sorted by outstanding balance (highest first)");
        int choice = readInt("Enter choice: ");

        if (choice == 1) {

            String customerId = readLine("Enter customer ID: ");

            try {
                printLoans(bank.findLoansByCustomer(customerId));
            } catch (CustomerNotFoundException e) {
                System.out.println("Error: " + e.getMessage());
            }

        } else if (choice == 2) {

            LoanStatus status = readLoanStatus();
            if (status != null) {
                printLoans(bank.findLoansByStatus(status));
            }

        } else if (choice == 3) {
            printLoans(bank.loansSortedByOutstandingBalanceDesc());
        } else {
            System.out.println("Invalid choice.");
        }
    }

    private static LoanStatus readLoanStatus() {

        System.out.println("1. ACTIVE  2. PAID_OFF  3. DEFAULTED");
        int statusChoice = readInt("Enter status choice: ");

        if (statusChoice == 1) {
            return LoanStatus.ACTIVE;
        } else if (statusChoice == 2) {
            return LoanStatus.PAID_OFF;
        } else if (statusChoice == 3) {
            return LoanStatus.DEFAULTED;
        } else {
            System.out.println("Invalid status choice.");
            return null;
        }
    }

    private static void printLoans(List<Loan> loans) {

        if (loans.isEmpty()) {
            System.out.println("No loans found.");
            return;
        }

        for (Loan loan : loans) {
            System.out.println(
                    "Loan ID: " + loan.getLoanId()
                    + " | Customer: " + loan.getCustomer().getCustomerId()
                    + " | Principal: " + money(loan.getPrincipal())
                    + " | Outstanding: " + money(loan.getOutstandingBalance())
                    + " | Status: " + loan.getStatus()
            );
        }
    }

    private static void viewInstallments(Bank bank) {

        String loanId = readLine("Enter loan ID: ");

        try {
            Loan loan = bank.findLoanById(loanId);

            System.out.println("1. All  2. Paid only  3. Unpaid only");
            int choice = readInt("Enter choice: ");

            List<Installment> installments;

            if (choice == 2) {
                installments = bank.installmentsByPaidStatus(loan, true);
            } else if (choice == 3) {
                installments = bank.installmentsByPaidStatus(loan, false);
            } else {
                installments = loan.getInstallments();
            }

            if (installments.isEmpty()) {
                System.out.println("No installments found.");
            } else {
                for (Installment installment : installments) {
                    System.out.println(
                            "Due: " + installment.getDueDate().format(DATE_FORMAT)
                            + " | Amount due: " + money(installment.getAmountDue())
                            + " | Paid: " + money(installment.getAmountPaid())
                            + " | Status: " + installment.getStatus()
                    );
                }
            }

        } catch (LoanNotFoundException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void showReports(Bank bank) {

        System.out.println("1. Top borrowers");
        System.out.println("2. Currently overdue loans");
        System.out.println("3. Loan status summary");
        System.out.println("4. Total interest earned");
        System.out.println("5. Available bank funds");
        int choice = readInt("Enter choice: ");

        if (choice == 1) {

            int limit = readInt("How many top borrowers to show? ");
            List<Customer> topBorrowers = bank.topBorrowers(limit);

            if (topBorrowers.isEmpty()) {
                System.out.println("No customers found.");
            }

            for (Customer customer : topBorrowers) {
                System.out.println(
                        customer.getName() + " (" + customer.getCustomerId() + "): "
                        + money(bank.totalPrincipalBorrowed(customer)) + " total borrowed"
                );
            }

        } else if (choice == 2) {

            List<OverdueEntry> overdue = bank.currentlyOverdueLoans(LocalDate.now());

            if (overdue.isEmpty()) {
                System.out.println("No overdue installments.");
            }

            for (OverdueEntry entry : overdue) {
                System.out.println(
                        entry.getCustomerName()
                        + " | Loan: " + entry.getLoanId()
                        + " | Due: " + entry.getDueDate().format(DATE_FORMAT)
                        + " | Days overdue: " + entry.getDaysOverdue()
                );
            }

        } else if (choice == 3) {

            LoanStatusSummary summary = bank.loanStatusSummary();
            System.out.println("Active: " + summary.getActiveCount());
            System.out.println("Paid off: " + summary.getPaidOffCount());
            System.out.println("Defaulted: " + summary.getDefaultedCount());

        } else if (choice == 4) {
            System.out.println("Total interest earned: " + money(bank.totalInterestEarned()));
        } else if (choice == 5) {
            System.out.println("Available funds: " + money(bank.getAvailableFunds()));
        } else {
            System.out.println("Invalid choice.");
        }
    }

    // ---------- Input helpers ----------
    // Everything is read as a plain line of text and then parsed by hand.
    // That way, typing letters where a number is expected just prints an
    // error and asks again, instead of crashing the whole program.

    private static String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
            }
        }
    }

    private static BigDecimal readAmount(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return new BigDecimal(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    // formats a money amount with exactly 2 decimal places
    // (the console can't show the naira sign, so amounts are written as NGN)
    private static String money(BigDecimal amount) {
        return "NGN " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // messages from Bank use the naira sign; swap it for NGN so the console can print it
    private static String plain(String message) {
        return message.replace("₦", "NGN ");
    }

    // ---------- Persistence ----------

    private static Bank loadBank() {
        try {
            Bank bank = repository.load(DATA_FILE);
            bank.increaseCapitalTo(Bank.STARTING_CAPITAL);
            System.out.println("Previous bank state loaded.");
            return bank;
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

    private static void saveBank(Bank bank) {
        try {
            repository.save(bank, DATA_FILE);
            System.out.println("Bank state saved successfully!");
        } catch (IOException e) {
            System.out.println("Could not save bank state: " + e.getMessage());
        }
    }
}
