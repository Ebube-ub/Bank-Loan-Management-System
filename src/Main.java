import java.io.IOException;
import java.util.Scanner;
import src.model.Bank;
import src.model.CreditTier;
import src.model.Customer;
import src.persistence.BankFileRepository;
import src.model.Loan;
import src.model.LoanStatus;

public class Main {

    public static void main(String[] args) {

        System.out.println("Bank Loan Management System");

        Bank bank;

        BankFileRepository repository = new BankFileRepository();

        // Load previous bank state
        try {
            bank = repository.load("bank.dat");
            System.out.println("Previous bank state loaded.");
        } catch (Exception e) {
            bank = new Bank(100000);
            System.out.println("No saved bank state found. Starting a new bank.");
        }

        Scanner scanner = new Scanner(System.in);

        boolean running = true;

        while (running) {

            System.out.println("1. Register Customer");
            System.out.println("2. Apply for Loan");
            System.out.println("3. Make Payment");
            System.out.println("4. Search Loans");
            System.out.println("5. Exit");

            System.out.print("Enter your choice: ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {

                case 1:

                System.out.print("Enter customer ID: ");
                String customerId = scanner.nextLine();

                System.out.print("Enter customer name: ");
                String name = scanner.nextLine();

                System.out.print("Enter customer email: ");
                String email = scanner.nextLine();

                System.out.println("Choose credit tier:");
                System.out.println("1. STANDARD");
                System.out.println("2. PREMIUM");

                System.out.print("Enter choice: ");
                int tierChoice = scanner.nextInt();
                scanner.nextLine();

                CreditTier creditTier;

                 if (tierChoice == 1) {
                    creditTier = CreditTier.STANDARD;
                } else if (tierChoice == 2) {
                        creditTier = CreditTier.PREMIUM;
                } else {
                    System.out.println("Invalid credit tier.");
                    break;
                }

    Customer customer = new Customer(
            customerId,
            name,
            email,
            creditTier
    );

    try {
        bank.registerCustomer(customer);
        System.out.println("Customer registered successfully!");
    } catch (Exception e) {
        System.out.println("Error: " + e.getMessage());
    }

    break;

                case 2:

                    System.out.print("Enter customer ID: ");
                    String loancustomerId = scanner.nextLine();

                    System.out.print("Enter loan ID: ");
                    String loanId = scanner.nextLine();

                    System.out.print("Enter loan amount: ");
                    double principal = scanner.nextDouble();

                    System.out.print("Enter annual interest rate (e.g. 0.12 for 12%): ");
                    double interestRate = scanner.nextDouble();

                    System.out.print("Enter term in months: ");
                    int termMonths = scanner.nextInt();
                    scanner.nextLine();

                     try {

                        Loan loan = bank.applyForLoan(
                                loancustomerId,
                                loanId,
                                principal,
                                interestRate,
                                termMonths
                        );

                        System.out.println("Loan approved successfully!");
                        System.out.println("Loan ID: " + loan.getLoanId());
                        System.out.println("Principal: " + loan.getPrincipal());
                        System.out.println("Number of installments: "
                                + loan.getInstallments().size());

                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }

                break;

                case 3:

                    System.out.print("Enter loan ID: ");
                    String paymentLoanId = scanner.nextLine();

                    System.out.print("Enter payment amount: ");
                    double paymentAmount = scanner.nextDouble();
                    scanner.nextLine();

                    try {

                        bank.makePayment(paymentLoanId, paymentAmount);

                        System.out.println("Payment applied successfully!");

                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }

                    break;

                case 4:

                    System.out.println("\nSearch Loans");
                    System.out.println("1. Search by Customer");
                    System.out.println("2. Search by Status");

                    System.out.print("Enter choice: ");
                    int searchChoice = scanner.nextInt();
                    scanner.nextLine();

                    if (searchChoice == 1) {

                        System.out.print("Enter customer ID: ");
                        String searchCustomerId = scanner.nextLine();

                        try {

                            java.util.List<Loan> customerLoans =
                                    bank.findLoansByCustomer(searchCustomerId);

                            if (customerLoans.isEmpty()) {
                                System.out.println("No loans found.");
                            } else {
                                for (Loan loan : customerLoans) {
                                    System.out.println(
                                            "Loan ID: " + loan.getLoanId()
                                            + " | Principal: " + String.format("%.2f", loan.getPrincipal())
                                            + " | Status: " + loan.getStatus()
                                    );
                                }
                            }

                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }

                    } else if (searchChoice == 2) {

                        System.out.println("1. ACTIVE");
                        System.out.println("2. PAID_OFF");
                        System.out.println("3. DEFAULTED");

                        System.out.print("Enter status choice: ");
                        int statusChoice = scanner.nextInt();
                        scanner.nextLine();

                        LoanStatus status;

                        if (statusChoice == 1) {
                            status = LoanStatus.ACTIVE;
                        } else if (statusChoice == 2) {
                            status = LoanStatus.PAID_OFF;
                        } else if (statusChoice == 3) {
                            status = LoanStatus.DEFAULTED;
                        } else {
                            System.out.println("Invalid status choice.");
                            break;
                        }

                        java.util.List<Loan> matchingLoans =
                                bank.findLoansByStatus(status);

                        if (matchingLoans.isEmpty()) {
                            System.out.println("No loans found.");
                        } else {
                            for (Loan loan : matchingLoans) {
                                System.out.println(
                                        "Loan ID: " + loan.getLoanId()
                                        + " | Principal: " + String.format("%.2f", loan.getPrincipal())
                                        + " | Status: " + loan.getStatus()
                                );
                            }
                        }

                    } else {
                        System.out.println("Invalid search choice.");
                    }

                    break;
                
                case 5:
                    running = false;
                    System.out.println("Exiting...");
                    break;

                default:
                    System.out.println("Invalid choice.");
            }
        }

        scanner.close();

        // Save bank state before exiting
        try {
            repository.save(bank, "bank.dat");
            System.out.println("Bank state saved successfully!");
        } catch (IOException e) {
            System.out.println("Could not save bank state: " + e.getMessage());
        }
    }
}