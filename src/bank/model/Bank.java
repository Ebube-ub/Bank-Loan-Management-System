package bank.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import bank.exception.CustomerAlreadyExistsException;
import bank.exception.CustomerNotFoundException;
import bank.exception.InsufficientFundsException;
import bank.exception.LoanLimitExceededException;
import bank.exception.LoanNotFoundException;
import bank.exception.OverdueInstallmentsException;

public class Bank implements Serializable {

    private static final long serialVersionUID = 1L;

    // no customer can have more than this much borrowed at once (added up
    // across all their active loans)
    private static final BigDecimal MAX_TOTAL_BORROWED = new BigDecimal("50000");

    // how many overdue installments in a row before we mark a loan DEFAULTED
    private static final int CONSECUTIVE_MISSED_FOR_DEFAULT = 3;

    private List<Customer> customers;
    private List<Loan> loans;
    private BigDecimal availableFunds;

    public Bank(BigDecimal availableFunds) {
        this.customers = new ArrayList<>();
        this.loans = new ArrayList<>();
        this.availableFunds = availableFunds;
    }

    public BigDecimal getAvailableFunds() {
        return availableFunds;
    }

    public List<Customer> getAllCustomers() {
        return customers;
    }

    public List<Loan> getAllLoans() {
        return loans;
    }

    // ---------- Customers ----------

    public void registerCustomer(Customer customer) throws CustomerAlreadyExistsException {

        for (Customer existingCustomer : customers) {
            if (existingCustomer.getCustomerId().equals(customer.getCustomerId())) {
                throw new CustomerAlreadyExistsException(
                        "Customer ID " + customer.getCustomerId() + " already exists."
                );
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

        throw new CustomerNotFoundException("Customer with ID " + customerId + " was not found.");
    }

    // ---------- Loans ----------

    public Loan applyForLoan(
            String customerId,
            String loanId,
            BigDecimal principal,
            BigDecimal interestRate,
            int termMonths
    ) throws CustomerNotFoundException, InsufficientFundsException,
            LoanLimitExceededException, OverdueInstallmentsException {

        return applyForLoan(customerId, loanId, principal, interestRate, termMonths, LocalDate.now());
    }

    // Same as above, but lets us pass in the disbursement date instead of
    // always using today. This is mainly so tests can create a loan that
    // was "disbursed" a few months ago, to check the overdue/default logic.
    public Loan applyForLoan(
            String customerId,
            String loanId,
            BigDecimal principal,
            BigDecimal interestRate,
            int termMonths,
            LocalDate disbursementDate
    ) throws CustomerNotFoundException, InsufficientFundsException,
            LoanLimitExceededException, OverdueInstallmentsException {

        Customer customer = findCustomerById(customerId);

        for (Loan existingLoan : loans) {
            if (existingLoan.getLoanId().equals(loanId)) {
                throw new IllegalArgumentException("Loan ID already exists.");
            }
        }

        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Loan amount must be greater than zero.");
        }

        if (termMonths <= 0) {
            throw new IllegalArgumentException("Loan term must be greater than zero.");
        }

        if (interestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Interest rate cannot be negative.");
        }

        // make sure overdue statuses are up to date before we check them below
        refreshDelinquencyStatus(disbursementDate);

        if (hasOverdueInstallments(customer)) {
            throw new OverdueInstallmentsException(
                    "Customer " + customerId + " has an overdue installment and cannot apply for a new loan."
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

        BigDecimal currentTotalBorrowed = getTotalActiveBorrowed(customer);

        if (currentTotalBorrowed.add(principal).compareTo(MAX_TOTAL_BORROWED) > 0) {
            throw new LoanLimitExceededException(
                    "Customer would exceed the maximum total borrowed limit of " + MAX_TOTAL_BORROWED
            );
        }

        if (principal.compareTo(availableFunds) > 0) {
            throw new InsufficientFundsException("Bank does not have enough funds for this loan.");
        }

        Loan loan = new Loan(loanId, customer, principal, interestRate, termMonths, disbursementDate);

        availableFunds = availableFunds.subtract(principal);

        loans.add(loan);
        customer.addLoan(loan);

        generateRepaymentSchedule(loan);

        return loan;
    }

    private void generateRepaymentSchedule(Loan loan) {

        BigDecimal totalRepayable = loan.getTotalRepayable();
        int term = loan.getTermMonths();

        BigDecimal baseInstallment = totalRepayable.divide(
                BigDecimal.valueOf(term), 2, RoundingMode.HALF_UP
        );

        BigDecimal allocatedSoFar = BigDecimal.ZERO;

        for (int i = 1; i <= term; i++) {

            LocalDate dueDate = loan.getDisbursementDate().plusMonths(i);

            BigDecimal installmentAmount;

            if (i == term) {
                // last installment gets whatever is left over, so the
                // installments always add up to exactly totalRepayable
                // (rounding each one the same way can leave a few cents out)
                installmentAmount = totalRepayable.subtract(allocatedSoFar);
            } else {
                installmentAmount = baseInstallment;
                allocatedSoFar = allocatedSoFar.add(installmentAmount);
            }

            loan.addInstallment(new Installment(dueDate, installmentAmount));
        }
    }

    public BigDecimal makePayment(String loanId, BigDecimal paymentAmount) throws LoanNotFoundException {
        return makePayment(loanId, paymentAmount, LocalDate.now());
    }

    // Applies a payment to a loan and gives back any part of it that
    // couldn't be used (e.g. the loan was already fully paid).
    public BigDecimal makePayment(String loanId, BigDecimal paymentAmount, LocalDate paymentDate)
            throws LoanNotFoundException {

        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }

        Loan loan = findLoanById(loanId);

        refreshDelinquencyStatus(paymentDate);

        BigDecimal amountApplied = loan.applyPayment(paymentAmount, paymentDate);

        // the money the bank collects (principal + interest) goes back into
        // the funds it has available to lend out again
        availableFunds = availableFunds.add(amountApplied);

        BigDecimal overpayment = paymentAmount.subtract(amountApplied);
        return overpayment;
    }

    public Loan findLoanById(String loanId) throws LoanNotFoundException {

        for (Loan loan : loans) {
            if (loan.getLoanId().equals(loanId)) {
                return loan;
            }
        }

        throw new LoanNotFoundException("Loan with ID " + loanId + " was not found.");
    }

    public List<Loan> findLoansByCustomer(String customerId) throws CustomerNotFoundException {
        Customer customer = findCustomerById(customerId);
        return customer.getLoans();
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

    // Sorts a copy of the loan list by outstanding balance, highest first.
    // Simple selection sort: repeatedly find the biggest remaining loan and
    // swap it into place.
    public List<Loan> loansSortedByOutstandingBalanceDesc() {

        List<Loan> sorted = new ArrayList<>(loans);

        for (int i = 0; i < sorted.size(); i++) {

            int biggestIndex = i;

            for (int j = i + 1; j < sorted.size(); j++) {
                BigDecimal balanceJ = sorted.get(j).getOutstandingBalance();
                BigDecimal balanceBiggest = sorted.get(biggestIndex).getOutstandingBalance();

                if (balanceJ.compareTo(balanceBiggest) > 0) {
                    biggestIndex = j;
                }
            }

            Loan temp = sorted.get(i);
            sorted.set(i, sorted.get(biggestIndex));
            sorted.set(biggestIndex, temp);
        }

        return sorted;
    }

    public List<Installment> installmentsByPaidStatus(Loan loan, boolean paid) {

        List<Installment> result = new ArrayList<>();

        for (Installment installment : loan.getInstallments()) {
            if (installment.isPaid() == paid) {
                result.add(installment);
            }
        }

        return result;
    }

    private boolean hasOverdueInstallments(Customer customer) {
        for (Loan loan : customer.getActiveLoans()) {
            if (loan.hasOverdueInstallments()) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal getTotalActiveBorrowed(Customer customer) {
        BigDecimal total = BigDecimal.ZERO;

        for (Loan loan : customer.getActiveLoans()) {
            total = total.add(loan.getPrincipal());
        }

        return total;
    }

    // ---------- Phase 2: overdue / default checking ----------

    // Goes through every active loan and:
    //  1. marks any installment OVERDUE if its due date has passed and it's still unpaid
    //  2. marks the loan DEFAULTED if it now has too many overdue installments in a row
    // Safe to call as often as we like (on startup, before showing the menu, etc).
    public void refreshDelinquencyStatus(LocalDate asOf) {

        for (Loan loan : loans) {

            if (loan.getStatus() != LoanStatus.ACTIVE) {
                continue;
            }

            for (Installment installment : loan.getInstallments()) {
                installment.refreshOverdueStatus(asOf);
            }

            if (loan.countConsecutiveOverdue() >= CONSECUTIVE_MISSED_FOR_DEFAULT) {
                loan.setStatus(LoanStatus.DEFAULTED);
            }
        }
    }

    // ---------- Phase 2: reports ----------

    public BigDecimal totalPrincipalBorrowed(Customer customer) {
        BigDecimal total = BigDecimal.ZERO;

        for (Loan loan : customer.getLoans()) {
            total = total.add(loan.getPrincipal());
        }

        return total;
    }

    // Top borrowers, biggest total principal first. Same simple selection
    // sort idea as loansSortedByOutstandingBalanceDesc above.
    public List<Customer> topBorrowers(int limit) {

        List<Customer> sorted = new ArrayList<>(customers);

        for (int i = 0; i < sorted.size(); i++) {

            int biggestIndex = i;

            for (int j = i + 1; j < sorted.size(); j++) {
                BigDecimal totalJ = totalPrincipalBorrowed(sorted.get(j));
                BigDecimal totalBiggest = totalPrincipalBorrowed(sorted.get(biggestIndex));

                if (totalJ.compareTo(totalBiggest) > 0) {
                    biggestIndex = j;
                }
            }

            Customer temp = sorted.get(i);
            sorted.set(i, sorted.get(biggestIndex));
            sorted.set(biggestIndex, temp);
        }

        if (limit >= 0 && sorted.size() > limit) {
            return sorted.subList(0, limit);
        }
        return sorted;
    }

    public List<OverdueEntry> currentlyOverdueLoans(LocalDate asOf) {

        List<OverdueEntry> result = new ArrayList<>();

        for (Loan loan : loans) {
            for (Installment installment : loan.getInstallments()) {
                if (installment.getStatus() == InstallmentStatus.OVERDUE) {

                    long daysOverdue = ChronoUnit.DAYS.between(installment.getDueDate(), asOf);

                    result.add(new OverdueEntry(
                            loan.getCustomer().getName(),
                            loan.getLoanId(),
                            installment.getDueDate(),
                            daysOverdue
                    ));
                }
            }
        }

        return result;
    }

    public LoanStatusSummary loanStatusSummary() {

        int active = 0;
        int paidOff = 0;
        int defaulted = 0;

        for (Loan loan : loans) {
            if (loan.getStatus() == LoanStatus.ACTIVE) {
                active++;
            } else if (loan.getStatus() == LoanStatus.PAID_OFF) {
                paidOff++;
            } else if (loan.getStatus() == LoanStatus.DEFAULTED) {
                defaulted++;
            }
        }

        return new LoanStatusSummary(active, paidOff, defaulted);
    }

    // Adds up the interest portion of every installment that has been paid
    // so far. Doesn't count anything from overdue installments, only actual
    // completed payments.
    public BigDecimal totalInterestEarned() {

        BigDecimal total = BigDecimal.ZERO;

        for (Loan loan : loans) {
            BigDecimal interestPerInstallment = loan.getInterestPerInstallment();

            for (Installment installment : loan.getInstallments()) {
                if (installment.isPaid()) {
                    total = total.add(interestPerInstallment);
                }
            }
        }

        return total;
    }
}
