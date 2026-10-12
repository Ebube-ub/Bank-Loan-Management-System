package bank.web;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import bank.exception.CustomerAlreadyExistsException;
import bank.exception.CustomerNotFoundException;
import bank.exception.InsufficientFundsException;
import bank.exception.LoanLimitExceededException;
import bank.exception.LoanNotFoundException;
import bank.exception.OverdueInstallmentsException;
import bank.model.Bank;
import bank.model.CreditTier;
import bank.model.Customer;
import bank.model.Installment;
import bank.model.Loan;
import bank.model.LoanStatus;
import bank.model.LoanStatusSummary;
import bank.model.OverdueEntry;
import bank.persistence.BankFileRepository;

// The one place the web server touches the Bank object.
//
// It does three jobs the console's main loop used to do:
//  1. loads bank.dat when the server starts
//  2. refreshes overdue/defaulted statuses before every request
//  3. saves bank.dat after every change (a website has no "Exit" option)
//
// Every method is synchronized, so if two requests arrive at once they take
// turns instead of changing the bank at the same time.
public class BankService {

    private static final BigDecimal STARTING_FUNDS = new BigDecimal("100000");

    private final BankFileRepository repository = new BankFileRepository();
    private final String dataFile;
    private final Bank bank;

    public BankService(String dataFile) {
        this.dataFile = dataFile;
        this.bank = loadBank();
    }

    // ---------- Customers ----------

    public synchronized List<Customer> getCustomers() {
        refresh();
        return bank.getAllCustomers();
    }

    public synchronized Customer registerCustomer(String customerId, String name, String email,
            CreditTier creditTier) throws CustomerAlreadyExistsException, IOException {

        Customer customer = new Customer(customerId, name, email, creditTier);
        bank.registerCustomer(customer);
        save();
        return customer;
    }

    // throws if the customer doesn't exist or can't open another loan
    public synchronized void checkEligibility(String customerId)
            throws CustomerNotFoundException, LoanLimitExceededException {
        refresh();
        bank.checkActiveLoanLimit(customerId);
    }

    // ---------- Loans ----------

    public synchronized List<Loan> getAllLoans() {
        refresh();
        return bank.getAllLoans();
    }

    public synchronized List<Loan> findLoansByCustomer(String customerId) throws CustomerNotFoundException {
        refresh();
        return bank.findLoansByCustomer(customerId);
    }

    public synchronized List<Loan> findLoansByStatus(LoanStatus status) {
        refresh();
        return bank.findLoansByStatus(status);
    }

    public synchronized List<Loan> loansSortedByBalance() {
        refresh();
        return bank.loansSortedByOutstandingBalanceDesc();
    }

    public synchronized Loan getLoan(String loanId) throws LoanNotFoundException {
        refresh();
        return bank.findLoanById(loanId);
    }

    public synchronized List<Installment> installmentsByPaidStatus(Loan loan, boolean paid) {
        return bank.installmentsByPaidStatus(loan, paid);
    }

    public synchronized Loan applyForLoan(String customerId, String loanId, BigDecimal principal,
            BigDecimal interestRate, int termMonths)
            throws CustomerNotFoundException, InsufficientFundsException,
            LoanLimitExceededException, OverdueInstallmentsException, IOException {

        Loan loan = bank.applyForLoan(customerId, loanId, principal, interestRate, termMonths);
        save();
        return loan;
    }

    // returns the part of the payment that wasn't needed (overpayment)
    public synchronized BigDecimal makePayment(String loanId, BigDecimal amount)
            throws LoanNotFoundException, IOException {

        BigDecimal overpayment = bank.makePayment(loanId, amount);
        save();
        return overpayment;
    }

    // ---------- Reports ----------

    public synchronized LoanStatusSummary loanStatusSummary() {
        refresh();
        return bank.loanStatusSummary();
    }

    public synchronized BigDecimal totalInterestEarned() {
        return bank.totalInterestEarned();
    }

    public synchronized BigDecimal availableFunds() {
        return bank.getAvailableFunds();
    }

    public synchronized List<Customer> topBorrowers(int limit) {
        refresh();
        return bank.topBorrowers(limit);
    }

    public synchronized BigDecimal totalPrincipalBorrowed(Customer customer) {
        return bank.totalPrincipalBorrowed(customer);
    }

    public synchronized List<OverdueEntry> overdueLoans() {
        refresh();
        return bank.currentlyOverdueLoans(LocalDate.now());
    }

    // ---------- Helpers ----------

    private void refresh() {
        bank.refreshDelinquencyStatus(LocalDate.now());
    }

    private void save() throws IOException {
        repository.save(bank, dataFile);
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
            return new Bank(STARTING_FUNDS);
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(
                    "Could not read saved bank state (" + e.getMessage() + "). Starting a new bank."
            );
            return new Bank(STARTING_FUNDS);
        }
    }
}
