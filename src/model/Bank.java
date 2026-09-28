package src.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import src.exception.CustomerAlreadyExistsException;
import src.exception.CustomerNotFoundException;
import src.exception.InsufficientFundsException;
import src.exception.LoanLimitExceededException;
import src.exception.LoanNotFoundException;

public class Bank implements java.io.Serializable {

    private List<Customer> customers;
    private List<Loan> loans;
    private double availableFunds;

    public Bank(double availableFunds) {
        this.customers = new ArrayList<>();
        this.loans = new ArrayList<>();
        this.availableFunds = availableFunds;
    }

    public void registerCustomer(Customer customer) throws CustomerAlreadyExistsException {

    for (Customer existingCustomer : customers) {

        if (existingCustomer.getCustomerId().equals(customer.getCustomerId())) {
            throw new CustomerAlreadyExistsException("Customer ID already exists.");
        }
    }

    customers.add(customer);
    }

    public Customer findCustomerById(String customerId) throws CustomerNotFoundException {

    for (Customer customer : customers) {

        if (customer.getCustomerId().equals(customerId)) {
            return customer;
        }
    }

    throw new CustomerNotFoundException(
        "Customer with ID " + customerId + " was not found."
    );
    }

    public Loan applyForLoan(
        String customerId,
        String loanId,
        double principal,
        double interestRate,
        int termMonths
) throws CustomerNotFoundException,
         InsufficientFundsException,
         LoanLimitExceededException {

    Customer customer = findCustomerById(customerId);
    
    for (Loan existingLoan : loans) {
        if (existingLoan.getLoanId().equals(loanId)) {
            throw new IllegalArgumentException(
                    "Loan ID already exists."
            );
        }
    }

    if (principal <= 0) {
    throw new IllegalArgumentException(
            "Loan amount must be greater than zero."
    );
}

if (termMonths <= 0) {
    throw new IllegalArgumentException(
            "Loan term must be greater than zero."
    );
}

if (interestRate < 0) {
    throw new IllegalArgumentException(
            "Interest rate cannot be negative."
    );
}

    int maxActiveLoans;

    if (customer.getCreditTier() == CreditTier.PREMIUM) {
        maxActiveLoans = 3;
    } else {
        maxActiveLoans = 2;
    }

    if (customer.getActiveLoans().size() >= maxActiveLoans) {
        throw new LoanLimitExceededException(
            "Customer has reached the maximum number of active loans."
        );
    }

    double maximumTotalBorrowed = 50000;

double currentTotalBorrowed = getTotalBorrowed(customer);

if (currentTotalBorrowed + principal > maximumTotalBorrowed) {
    throw new LoanLimitExceededException(
        "Customer would exceed the maximum total borrowed limit of "
        + maximumTotalBorrowed
    );
    }

    if (principal > availableFunds) {
        throw new InsufficientFundsException(
            "Bank does not have enough funds for this loan."
        );
    }

    Loan loan = new Loan(
        loanId,
        customer,
        principal,
        interestRate,
        termMonths,
        LocalDate.now()
    );

    availableFunds -= principal;

    loans.add(loan);
    customer.getActiveLoans().add(loan);

    generateRepaymentSchedule(loan);

    return loan;
    }

    private void generateRepaymentSchedule(Loan loan) {

    double totalInterest =
            loan.getPrincipal()
            * loan.getInterestRate()
            * loan.getTermMonths()
            / 12;

    double totalAmount =
            loan.getPrincipal() + totalInterest;

    double monthlyPayment =
            totalAmount / loan.getTermMonths();

    for (int i = 1; i <= loan.getTermMonths(); i++) {

        LocalDate dueDate =
                loan.getDisbursementDate().plusMonths(i);

        Installment installment =
                new Installment(dueDate, monthlyPayment);

        loan.getInstallments().add(installment);
    }
    }

    public void makePayment(String loanId, double paymentAmount)
        throws LoanNotFoundException {

    if (paymentAmount <= 0) {
        throw new IllegalArgumentException(
                "Payment amount must be greater than zero."
        );
    }

    Loan loan = null;

    for (Loan currentLoan : loans) {
        if (currentLoan.getLoanId().equals(loanId)) {
            loan = currentLoan;
            break;
        }
    }

    if (loan == null) {
        throw new LoanNotFoundException(
                "Loan with ID " + loanId + " was not found."
        );
    }

    double remainingPayment = paymentAmount;

    for (Installment installment : loan.getInstallments()) {

        if (remainingPayment <= 0) {
            break;
        }

        double remainingInstallment =
                installment.getRemainingAmount();

        if (remainingInstallment > 0) {

            double paymentForInstallment =
                    Math.min(remainingPayment, remainingInstallment);

            installment.makePayment(paymentForInstallment);

            remainingPayment -= paymentForInstallment;
        }
    }

    // Check whether every installment has been paid
    boolean allPaid = true;

    for (Installment installment : loan.getInstallments()) {
        if (installment.getStatus() != InstallmentStatus.PAID) {
            allPaid = false;
            break;
        }
    }

    if (allPaid) {
    loan.setStatus(LoanStatus.PAID_OFF);
    loan.getCustomer().getActiveLoans().remove(loan);
    }
}

    private double getTotalBorrowed(Customer customer) {

    double total = 0;

    for (Loan loan : customer.getActiveLoans()) {
        total += loan.getPrincipal();
    }

    return total;
    }

    public Loan findLoanById(String loanId)
        throws LoanNotFoundException {

    for (Loan loan : loans) {

        if (loan.getLoanId().equals(loanId)) {
            return loan;
        }
    }

    throw new LoanNotFoundException(
            "Loan with ID " + loanId + " was not found."
    );
}

    public List<Loan> findLoansByCustomer(String customerId)
        throws CustomerNotFoundException {

    Customer customer = findCustomerById(customerId);

    List<Loan> customerLoans = new ArrayList<>();

    for (Loan loan : loans) {
        if (loan.getCustomer().getCustomerId().equals(customerId)) {
            customerLoans.add(loan);
        }
    }

    return customerLoans;
    }
    public List<Loan> findLoansByStatus(LoanStatus status) {

    List<Loan> matchingLoans = new ArrayList<>();

    for (Loan loan : loans) {
        if (loan.getStatus() == status) {
            matchingLoans.add(loan);
        }
    }

    return matchingLoans;
}
}