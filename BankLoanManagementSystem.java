import java.util.ArrayList;
import java.util.Scanner;

class Customer {
    int id;
    String name;
    String phone;

    Customer(int id, String name, String phone) {
        this.id = id;
        this.name = name;
        this.phone = phone;
    }

    void display() {
        System.out.println(id + " | " + name + " | " + phone);
    }
}

class Loan {
    int loanId;
    int customerId;
    double amount;
    double interestRate;
    int years;
    double paidAmount;

    Loan(int loanId, int customerId, double amount,
         double interestRate, int years) {

        this.loanId = loanId;
        this.customerId = customerId;
        this.amount = amount;
        this.interestRate = interestRate;
        this.years = years;
        this.paidAmount = 0;
    }

    double calculateInterest() {
        return (amount * interestRate * years) / 100;
    }

    double calculateTotalAmount() {
        return amount + calculateInterest();
    }

    double remainingAmount() {
        return calculateTotalAmount() - paidAmount;
    }

    void display() {
        System.out.println(
            loanId + " | Customer ID: " + customerId +
            " | Loan: ₹" + amount +
            " | Interest: ₹" + calculateInterest() +
            " | Total: ₹" + calculateTotalAmount() +
            " | Paid: ₹" + paidAmount +
            " | Remaining: ₹" + remainingAmount()
        );
    }
}

public class BankLoanManagementSystem {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        ArrayList<Customer> customers = new ArrayList<>();
        ArrayList<Loan> loans = new ArrayList<>();

        int customerId = 1;
        int loanId = 1;
        int choice;

        do {
            System.out.println("\n===== BANK LOAN MANAGEMENT SYSTEM =====");
            System.out.println("1. Add Customer");
            System.out.println("2. View Customers");
            System.out.println("3. Apply for Loan");
            System.out.println("4. View Loans");
            System.out.println("5. Calculate Loan Details");
            System.out.println("6. Make Loan Repayment");
            System.out.println("7. Search Customer");
            System.out.println("8. Exit");

            System.out.print("Enter your choice: ");
            choice = sc.nextInt();
            sc.nextLine();

            switch (choice) {

                case 1:
                    System.out.print("Enter Customer Name: ");
                    String name = sc.nextLine();

                    System.out.print("Enter Phone Number: ");
                    String phone = sc.nextLine();

                    customers.add(
                        new Customer(customerId++, name, phone)
                    );

                    System.out.println(
                        "Customer added successfully!"
                    );
                    break;

                case 2:
                    if (customers.isEmpty()) {
                        System.out.println("No customers found.");
                    } else {
                        System.out.println(
                            "\nID | Name | Phone"
                        );

                        for (Customer c : customers) {
                            c.display();
                        }
                    }
                    break;

                case 3:
                    if (customers.isEmpty()) {
                        System.out.println(
                            "Please add a customer first."
                        );
                        break;
                    }

                    System.out.print("Enter Customer ID: ");
                    int customerIdInput = sc.nextInt();

                    boolean customerFound = false;

                    for (Customer c : customers) {
                        if (c.id == customerIdInput) {
                            customerFound = true;
                            break;
                        }
                    }

                    if (!customerFound) {
                        System.out.println("Customer not found.");
                        break;
                    }

                    System.out.print("Enter Loan Amount: ");
                    double amount = sc.nextDouble();

                    System.out.print("Enter Interest Rate (%): ");
                    double interestRate = sc.nextDouble();

                    System.out.print("Enter Loan Period (Years): ");
                    int years = sc.nextInt();

                    loans.add(
                        new Loan(
                            loanId++,
                            customerIdInput,
                            amount,
                            interestRate,
                            years
                        )
                    );

                    System.out.println(
                        "Loan application submitted successfully!"
                    );
                    break;

                case 4:
                    if (loans.isEmpty()) {
                        System.out.println("No loan records found.");
                    } else {
                        System.out.println("\n--- Loan Details ---");

                        for (Loan loan : loans) {
                            loan.display();
                        }
                    }
                    break;

                case 5:
                    System.out.print("Enter Loan ID: ");
                    int searchLoanId = sc.nextInt();

                    boolean loanFound = false;

                    for (Loan loan : loans) {
                        if (loan.loanId == searchLoanId) {

                            System.out.println(
                                "\nLoan Amount: ₹" + loan.amount
                            );

                            System.out.println(
                                "Interest Amount: ₹" +
                                loan.calculateInterest()
                            );

                            System.out.println(
                                "Total Repayment: ₹" +
                                loan.calculateTotalAmount()
                            );

                            System.out.println(
                                "Remaining Amount: ₹" +
                                loan.remainingAmount()
                            );

                            loanFound = true;
                            break;
                        }
                    }

                    if (!loanFound) {
                        System.out.println("Loan ID not found.");
                    }
                    break;

                case 6:
                    System.out.print("Enter Loan ID: ");
                    int repaymentLoanId = sc.nextInt();

                    boolean repaymentFound = false;

                    for (Loan loan : loans) {

                        if (loan.loanId == repaymentLoanId) {

                            System.out.print(
                                "Enter Repayment Amount: ₹"
                            );

                            double repayment = sc.nextDouble();

                            if (repayment > 0 &&
                                repayment <= loan.remainingAmount()) {

                                loan.paidAmount += repayment;

                                System.out.println(
                                    "Repayment successful!"
                                );

                                System.out.println(
                                    "Remaining Amount: ₹" +
                                    loan.remainingAmount()
                                );

                            } else {
                                System.out.println(
                                    "Invalid repayment amount."
                                );
                            }

                            repaymentFound = true;
                            break;
                        }
                    }

                    if (!repaymentFound) {
                        System.out.println("Loan ID not found.");
                    }
                    break;

                case 7:
                    System.out.print(
                        "Enter Customer Name to Search: "
                    );

                    String searchName =
                        sc.nextLine().toLowerCase();

                    boolean found = false;

                    for (Customer c : customers) {

                        if (c.name.toLowerCase()
                            .contains(searchName)) {

                            c.display();
                            found = true;
                        }
                    }

                    if (!found) {
                        System.out.println(
                            "Customer not found."
                        );
                    }
                    break;

                case 8:
                    System.out.println(
                        "Thank you for using Bank Loan Management System!"
                    );
                    break;

                default:
                    System.out.println(
                        "Invalid choice. Please try again."
                    );
            }

        } while (choice != 8);

        sc.close();
    }
}
