package bank.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import bank.exception.ApplicationNotFoundException;
import bank.exception.CustomerAlreadyExistsException;
import bank.exception.CustomerNotFoundException;
import bank.exception.InsufficientFundsException;
import bank.exception.LoanLimitExceededException;
import bank.exception.LoanNotFoundException;
import bank.exception.OverdueInstallmentsException;
import bank.security.PasswordHasher;

public class Bank implements Serializable {

    private static final long serialVersionUID = 1L;

    // no customer can have more than this much borrowed at once (added up
    // across all their active loans)
    public static final BigDecimal MAX_TOTAL_BORROWED = new BigDecimal("2000000");

    // The money the bank starts with. A bank saved with less than this is topped up
    // to it once when it is loaded (see increaseCapitalTo).
    public static final BigDecimal STARTING_CAPITAL = new BigDecimal("5000000");

    // The fee for paying an installment late: this share of the installment,
    // added once when it goes overdue. 0.05 means 5%. To change the fee, change it here.
    public static final BigDecimal LATE_FEE_RATE = new BigDecimal("0.05");

    // The charge for paying ahead: this share of any money that pays installments which
    // are not due yet (except the next one in line). It comes out of the payment.
    // 0.02 means 2%. To change it, change it here.
    public static final BigDecimal PREPAYMENT_FEE_RATE = new BigDecimal("0.02");

    // The Send warning button appears once a customer's oldest late installment is MORE
    // than this many days overdue.
    public static final int WARNING_AFTER_DAYS = 10;

    // Days a customer has to pay after the warning is sent. If they have not paid by
    // then, the case needs escalation.
    public static final int ESCALATE_AFTER_DAYS = 5;

    // how many days of daily scores are kept
    private static final int SCORE_HISTORY_DAYS = 120;

    // how many overdue installments in a row before we mark a loan DEFAULTED
    private static final int CONSECUTIVE_MISSED_FOR_DEFAULT = 3;

    // the yearly interest rate used for new applications unless the admin changes it
    private static final BigDecimal STARTING_INTEREST_RATE = new BigDecimal("0.20");

    private List<Customer> customers;
    private List<Loan> loans;
    private BigDecimal availableFunds;

    // Added after the first version. A bank.dat saved by an older version has
    // none of these, so readResolve() below fills them in when it is loaded.
    private List<LoanApplication> applications;
    private List<PaymentRecord> payments;
    private List<UserAccount> users;
    private List<CollectionCase> cases;     // warnings sent to late customers
    private BigDecimal defaultInterestRate; // yearly, for new applications
    private BigDecimal startingCapital;     // the money the bank was started (or topped up) with

    // One credit score per customer per day (customer ID -> day -> score). Scores are
    // never stored otherwise, so this is what lets the early-warning list see that a
    // score has dropped. Missing in files saved before it existed.
    private Map<String, TreeMap<LocalDate, Integer>> scoreHistory;
    private LocalDate scoresRecordedOn;

    public Bank(BigDecimal availableFunds) {
        this.customers = new ArrayList<>();
        this.loans = new ArrayList<>();
        this.availableFunds = availableFunds;
        this.applications = new ArrayList<>();
        this.payments = new ArrayList<>();
        this.users = new ArrayList<>();
        this.cases = new ArrayList<>();
        this.defaultInterestRate = STARTING_INTEREST_RATE;
        this.startingCapital = availableFunds;
        this.scoreHistory = new TreeMap<>();
    }

    // Java calls this after loading a saved bank. It gives older saved files
    // the lists and settings that didn't exist when they were saved.
    private Object readResolve() {
        if (applications == null) {
            applications = new ArrayList<>();
        }
        if (payments == null) {
            payments = new ArrayList<>();
        }
        if (users == null) {
            users = new ArrayList<>();
        }
        if (cases == null) {
            cases = new ArrayList<>();
        }
        if (defaultInterestRate == null) {
            defaultInterestRate = STARTING_INTEREST_RATE;
        }
        if (startingCapital == null) {
            // banks saved before this existed were all started with 100,000
            startingCapital = new BigDecimal("100000");
        }
        if (scoreHistory == null) {
            scoreHistory = new TreeMap<>();
        }
        return this;
    }

    public BigDecimal getAvailableFunds() {
        return availableFunds;
    }

    // Adds money to the bank so that it has been started with at least this much.
    // It adds only the difference, so what is already lent out stays correct,
    // and asking again does nothing. Returns true if money was added.
    public boolean increaseCapitalTo(BigDecimal target) {
        if (target.compareTo(startingCapital) <= 0) {
            return false;
        }

        availableFunds = availableFunds.add(target.subtract(startingCapital));
        startingCapital = target;
        return true;
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

        checkActiveLoanLimit(customerId, disbursementDate);

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

    // Throws if this customer can't have another active loan: either their
    // credit band doesn't allow any loans (POOR), or they already have as many
    // active loans as their band allows. Callers use this right after the
    // customer is known, so nobody fills in a whole loan form for nothing.
    public void checkActiveLoanLimit(String customerId)
            throws CustomerNotFoundException, LoanLimitExceededException {
        checkActiveLoanLimit(customerId, LocalDate.now());
    }

    public void checkActiveLoanLimit(String customerId, LocalDate asOf)
            throws CustomerNotFoundException, LoanLimitExceededException {

        Customer customer = findCustomerById(customerId);
        CreditBand band = scoreOf(customer, asOf).getBand();

        if (band.getMaxActiveLoans() == 0) {
            throw new LoanLimitExceededException(
                    "Customer's credit score is in the Poor band, so new loans are not available."
            );
        }

        if (customer.getActiveLoans().size() >= band.getMaxActiveLoans()) {
            throw new LoanLimitExceededException(
                    "Customer has reached the maximum number of active loans ("
                    + band.getMaxActiveLoans() + ") for their credit band."
            );
        }
    }

    // ---------- Credit score ----------

    public CreditScore scoreOf(Customer customer, LocalDate asOf) {
        return CreditScorer.score(customer, asOf);
    }

    // Saves today's score for every customer who has none saved for today yet.
    // Returns true if anything was added (so the caller knows to save the file).
    // Cheap to call often: it does nothing once everyone has today's score.
    public boolean recordScoreSnapshots(LocalDate today) {
        if (today.equals(scoresRecordedOn) && scoreHistory.size() >= customers.size()) {
            return false;
        }

        boolean added = false;
        for (Customer customer : customers) {
            TreeMap<LocalDate, Integer> days =
                    scoreHistory.computeIfAbsent(customer.getCustomerId(), id -> new TreeMap<>());

            if (!days.containsKey(today)) {
                days.put(today, scoreOf(customer, today).getScore());
                added = true;
            }

            // keep about four months, no more
            days.headMap(today.minusDays(SCORE_HISTORY_DAYS)).clear();
        }

        scoresRecordedOn = today;
        return added;
    }

    // Saves one score for a given day (used by tests, and for scores worked out in the past).
    public void recordScore(String customerId, LocalDate day, int score) {
        scoreHistory.computeIfAbsent(customerId, id -> new TreeMap<>()).put(day, score);
    }

    // The oldest score saved between two days (both included), or null if there is none.
    public Integer oldestScoreBetween(String customerId, LocalDate from, LocalDate to) {
        TreeMap<LocalDate, Integer> days = scoreHistory.get(customerId);
        if (days == null) {
            return null;
        }

        Map.Entry<LocalDate, Integer> first = days.ceilingEntry(from);
        if (first == null || first.getKey().isAfter(to)) {
            return null;
        }
        return first.getValue();
    }

    // The tier follows the score: Excellent and Good are PREMIUM, the rest STANDARD.
    public CreditTier tierOf(Customer customer, LocalDate asOf) {
        return scoreOf(customer, asOf).getBand().getTier();
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

    public BigDecimal makePayment(String loanId, BigDecimal paymentAmount, LocalDate paymentDate)
            throws LoanNotFoundException {
        return makePayment(loanId, paymentAmount, paymentDate, "console", false);
    }

    // Applies a payment to a loan and gives back any part of it that
    // couldn't be used (e.g. the loan was already fully paid).
    // recordedBy is who entered it; recordedByAdmin is true when the admin
    // entered it on a customer's behalf (e.g. cash paid at the counter).
    public BigDecimal makePayment(String loanId, BigDecimal paymentAmount, LocalDate paymentDate,
            String recordedBy, boolean recordedByAdmin) throws LoanNotFoundException {
        return makePaymentDetailed(loanId, paymentAmount, paymentDate, recordedBy, recordedByAdmin).getOverpayment();
    }

    // Same as makePayment, but says where the money went: to the installments,
    // to the prepayment charge, or not used at all.
    public PaymentResult makePaymentDetailed(String loanId, BigDecimal paymentAmount, LocalDate paymentDate,
            String recordedBy, boolean recordedByAdmin) throws LoanNotFoundException {

        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }

        Loan loan = findLoanById(loanId);

        refreshDelinquencyStatus(paymentDate);

        PaymentResult result = loan.applyPayment(paymentAmount, paymentDate, PREPAYMENT_FEE_RATE);

        // the money the bank collects (principal, interest and the prepayment charge)
        // goes back into the funds it has available to lend out again
        BigDecimal collected = result.getApplied().add(result.getPrepaymentFee());
        availableFunds = availableFunds.add(collected);

        if (collected.compareTo(BigDecimal.ZERO) > 0) {
            payments.add(new PaymentRecord(loanId, result.getApplied(), result.getPrepaymentFee(),
                    paymentDate, recordedBy, recordedByAdmin));
        }

        closePaidUpCases(paymentDate);

        return result;
    }

    // every payment made on one loan, oldest first
    public List<PaymentRecord> paymentsForLoan(String loanId) {
        List<PaymentRecord> result = new ArrayList<>();

        for (PaymentRecord payment : payments) {
            if (payment.getLoanId().equals(loanId)) {
                result.add(payment);
            }
        }

        return result;
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

    // Goes through every loan and:
    //  1. marks any installment OVERDUE if its due date has passed and it's still unpaid
    //     (and adds its late fee, once)
    //  2. marks the loan DEFAULTED if it now has too many overdue installments in a row
    //  3. adds the interest that has built up on late amounts
    // Safe to call as often as we like (on startup, before showing the menu, etc).
    public void refreshDelinquencyStatus(LocalDate asOf) {

        for (Loan loan : loans) {

            if (loan.getStatus() == LoanStatus.ACTIVE) {

                for (Installment installment : loan.getInstallments()) {
                    installment.refreshOverdueStatus(asOf, LATE_FEE_RATE);
                }

                if (loan.countConsecutiveOverdue() >= CONSECUTIVE_MISSED_FOR_DEFAULT) {
                    loan.markDefaulted(asOf);
                }
            }

            if (loan.getStatus() == LoanStatus.ACTIVE || loan.getStatus() == LoanStatus.DEFAULTED) {
                loan.accrueLateInterest(asOf);
            }
        }

        closePaidUpCases(asOf);
    }

    // ---------- Late customers: warning, escalation, lawyer ----------
    //
    // A customer's oldest late installment sets the stage:
    //   up to 10 days late                      -> WATCHING (only the colour shows)
    //   more than 10 days late                  -> SEND_WARNING (the lender can send a warning)
    //   warning sent, deadline not passed       -> WARNED
    //   deadline passed, still late             -> NEEDS_ESCALATION
    //   lender passed it to a lawyer            -> REFERRED
    // When the customer has paid everything that was late, the case is resolved.

    // true if any installment of this customer is overdue right now
    private boolean hasLateInstallments(Customer customer) {
        for (Loan loan : customer.getLoans()) {
            for (Installment installment : loan.getInstallments()) {
                if (installment.getStatus() == InstallmentStatus.OVERDUE) {
                    return true;
                }
            }
        }
        return false;
    }

    // the case that is still open for this customer, or null
    private CollectionCase openCase(String customerId) {
        for (int i = cases.size() - 1; i >= 0; i--) {
            CollectionCase candidate = cases.get(i);
            if (candidate.getCustomerId().equals(customerId) && !candidate.isResolved()) {
                return candidate;
            }
        }
        return null;
    }

    private void closePaidUpCases(LocalDate today) {
        for (CollectionCase collectionCase : cases) {
            if (collectionCase.isResolved()) {
                continue;
            }

            boolean stillLate = false;
            for (Customer customer : customers) {
                if (customer.getCustomerId().equals(collectionCase.getCustomerId())) {
                    stillLate = hasLateInstallments(customer);
                }
            }

            if (!stillLate) {
                collectionCase.resolve(today);
            }
        }
    }

    // Works out where one customer stands today: their late amounts, the stage, and the totals.
    // For a defaulted loan, everything still unpaid counts (it is all due now).
    public CollectionStatus collectionStatus(Customer customer, LocalDate today) {

        List<OverdueEntry> lines = new ArrayList<>();
        long oldest = 0;
        BigDecimal installments = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (Loan loan : customer.getLoans()) {
            for (OverdueEntry entry : loan.lateEntries(today)) {

                if (!entry.isDueBecauseDefaulted() && entry.getDaysOverdue() > oldest) {
                    oldest = entry.getDaysOverdue();
                }

                installments = installments.add(entry.getBaseAmount());
                fees = fees.add(entry.getLateFee());
                interest = interest.add(entry.getLateInterest());
                paid = paid.add(entry.getPaid());
                total = total.add(entry.getOwedToday());

                lines.add(entry);
            }
        }

        CollectionCase open = openCase(customer.getCustomerId());
        CaseStage stage;

        if (!hasLateInstallments(customer)) {
            stage = CaseStage.NONE;
        } else if (open != null && open.isReferred()) {
            stage = CaseStage.REFERRED;
        } else if (open != null && today.isAfter(open.getDeadline())) {
            stage = CaseStage.NEEDS_ESCALATION;
        } else if (open != null) {
            stage = CaseStage.WARNED;
        } else if (oldest > WARNING_AFTER_DAYS) {
            stage = CaseStage.SEND_WARNING;
        } else {
            stage = CaseStage.WATCHING;
        }

        if (stage == CaseStage.NONE) {
            open = null;
        }

        return new CollectionStatus(customer, stage, oldest, lines, installments, fees, interest, paid, total, open);
    }

    // every customer who is late, the longest overdue first
    public List<CollectionStatus> collectionStatuses(LocalDate today) {
        List<CollectionStatus> result = new ArrayList<>();

        for (Customer customer : customers) {
            CollectionStatus status = collectionStatus(customer, today);
            if (status.getStage() != CaseStage.NONE) {
                result.add(status);
            }
        }

        result.sort((a, b) -> Long.compare(b.getOldestDaysOverdue(), a.getOldestDaysOverdue()));
        return result;
    }

    // how many customers are at this stage today
    public int countInStage(CaseStage stage, LocalDate today) {
        int count = 0;
        for (CollectionStatus status : collectionStatuses(today)) {
            if (status.getStage() == stage) {
                count++;
            }
        }
        return count;
    }

    // the message the lender is about to send (nothing is saved)
    public String warningPreview(String customerId, LocalDate today) throws CustomerNotFoundException {
        refreshDelinquencyStatus(today);
        CollectionStatus status = collectionStatus(findCustomerById(customerId), today);
        checkWarningCanBeSent(status);
        return CollectionMessages.warning(status, today.plusDays(ESCALATE_AFTER_DAYS));
    }

    private void checkWarningCanBeSent(CollectionStatus status) {
        if (status.getStage() == CaseStage.SEND_WARNING) {
            return;
        }

        if (status.getStage() == CaseStage.NONE) {
            throw new IllegalArgumentException("This customer has nothing overdue.");
        }
        if (status.getStage() == CaseStage.WATCHING) {
            throw new IllegalArgumentException("A warning can be sent once an installment is more than "
                    + WARNING_AFTER_DAYS + " days overdue.");
        }
        throw new IllegalArgumentException("A warning has already been sent to this customer.");
    }

    // Puts the warning on the customer's page and starts the clock: they have
    // ESCALATE_AFTER_DAYS days to pay.
    public CollectionCase sendWarning(String customerId, String sentBy, LocalDate today)
            throws CustomerNotFoundException {

        refreshDelinquencyStatus(today);
        CollectionStatus status = collectionStatus(findCustomerById(customerId), today);
        checkWarningCanBeSent(status);

        LocalDate deadline = today.plusDays(ESCALATE_AFTER_DAYS);
        CollectionCase collectionCase = new CollectionCase(
                customerId, today, sentBy, CollectionMessages.warning(status, deadline), deadline);

        cases.add(collectionCase);
        return collectionCase;
    }

    // The lender passes a case that needs escalation to a lawyer (the system never contacts the lawyer).
    public CollectionCase markReferred(String customerId, String note, String by, LocalDate today)
            throws CustomerNotFoundException {

        refreshDelinquencyStatus(today);
        CollectionStatus status = collectionStatus(findCustomerById(customerId), today);

        if (status.getStage() != CaseStage.NEEDS_ESCALATION) {
            throw new IllegalArgumentException("Only a case that needs escalation can be referred to a lawyer.");
        }

        status.getCollectionCase().refer(today, by, note);
        return status.getCollectionCase();
    }

    // every case ever opened, oldest first (used by the lawyer file)
    public List<CollectionCase> getCases() {
        return cases;
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
            for (OverdueEntry entry : loan.lateEntries(asOf)) {
                // only installments that are late themselves, not ones due because the loan defaulted
                if (!entry.isDueBecauseDefaulted()) {
                    result.add(entry);
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

    // ---------- Loan applications: advice and approval ----------
    //
    // A customer applies; the system checks the rules and gives advice
    // (Lend / Review / Decline); the admin makes the final decision. Only an
    // approved application becomes a loan.

    private static final int MAX_TERM_MONTHS = 60;

    public BigDecimal getDefaultInterestRate() {
        return defaultInterestRate;
    }

    public void setDefaultInterestRate(BigDecimal rate) {
        checkInterestRate(rate);
        this.defaultInterestRate = rate;
    }

    private void checkInterestRate(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("The interest rate must be between 0% and 100% a year.");
        }
    }

    // Gives the system's opinion on lending to this customer.
    // principal can be null: then it only works out what the customer is
    // allowed in general (used to show a customer their limit before they apply).
    public LoanAssessment assess(Customer customer, BigDecimal principal, LocalDate asOf) {

        // make sure overdue statuses are up to date before we look at them
        refreshDelinquencyStatus(asOf);

        CreditScore creditScore = scoreOf(customer, asOf);
        CreditBand band = creditScore.getBand();
        int activeLoans = customer.getActiveLoans().size();

        List<String> problems = new ArrayList<>();

        if (hasOverdueInstallments(customer)) {
            problems.add("There is an overdue installment on an active loan. It must be paid first.");
        }

        if (hasUnpaidDefault(customer)) {
            problems.add("A defaulted loan has not been paid off.");
        }

        if (band == CreditBand.POOR) {
            problems.add("The credit score (" + creditScore.getScore()
                    + ") is in the Poor band, so new loans are not available.");
        } else if (activeLoans >= band.getMaxActiveLoans()) {
            problems.add("There are already " + activeLoans + " active loan(s), the most the "
                    + bandName(band) + " band allows (" + band.getMaxActiveLoans() + ").");
        }

        // the most one new loan could be: the band's limit, what is left under
        // the total borrowing cap, and what the bank actually has to lend
        BigDecimal roomUnderCap = MAX_TOTAL_BORROWED.subtract(getTotalActiveBorrowed(customer));
        BigDecimal maxAmount = band.getMaxSingleLoan().min(roomUnderCap).min(availableFunds);
        if (maxAmount.compareTo(BigDecimal.ZERO) < 0) {
            maxAmount = BigDecimal.ZERO;
        }

        if (roomUnderCap.compareTo(BigDecimal.ZERO) <= 0) {
            problems.add("The most that can be borrowed at once (" + naira(MAX_TOTAL_BORROWED)
                    + ") is already in use.");
        }

        if (principal != null) {
            if (principal.compareTo(availableFunds) > 0) {
                problems.add("The bank does not have enough funds for " + naira(principal) + " right now.");
            }

            if (roomUnderCap.compareTo(BigDecimal.ZERO) > 0 && principal.compareTo(roomUnderCap) > 0) {
                problems.add(naira(principal) + " would take the total borrowed over " + naira(MAX_TOTAL_BORROWED)
                        + ". At most " + naira(roomUnderCap) + " more can be borrowed.");
            }

            if (band != CreditBand.POOR && principal.compareTo(band.getMaxSingleLoan()) > 0) {
                problems.add(naira(principal) + " is above the largest single loan for the "
                        + bandName(band) + " band (" + naira(band.getMaxSingleLoan()) + ").");
            }
        }

        if (!problems.isEmpty()) {
            return new LoanAssessment(Advice.DECLINE, problems, maxAmount, creditScore);
        }

        List<String> reasons = new ArrayList<>();
        reasons.add("Credit score " + creditScore.getScore() + " is in the " + bandName(band) + " band.");

        if (customer.getLoans().isEmpty()) {
            reasons.add("New customer with no loan history yet.");
        }

        if (band.getAdvice() == Advice.LEND) {
            reasons.add("Every rule is met and the record is good.");
        } else {
            reasons.add("Every rule is met, but this band always needs a closer look before lending.");
        }

        return new LoanAssessment(band.getAdvice(), reasons, maxAmount, creditScore);
    }

    // Records a customer's request. If a rule is broken it is declined straight
    // away (the customer is told why). Otherwise it waits for the admin.
    // interestRate can be null to use the bank's default rate.
    public LoanApplication submitApplication(String customerId, BigDecimal principal,
            BigDecimal interestRate, int termMonths, LocalDate today) throws CustomerNotFoundException {
        return submitApplication(customerId, principal, interestRate, termMonths, today, false);
    }

    // Same, and rulesAccepted says the customer ticked the box saying they have read and
    // understood the rules. The day is saved with the application.
    public LoanApplication submitApplication(String customerId, BigDecimal principal,
            BigDecimal interestRate, int termMonths, LocalDate today, boolean rulesAccepted)
            throws CustomerNotFoundException {

        Customer customer = findCustomerById(customerId);

        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("The loan amount must be greater than zero.");
        }

        if (termMonths <= 0 || termMonths > MAX_TERM_MONTHS) {
            throw new IllegalArgumentException("The loan term must be between 1 and " + MAX_TERM_MONTHS + " months.");
        }

        BigDecimal rate = interestRate;
        if (rate == null) {
            rate = defaultInterestRate;
        }
        checkInterestRate(rate);

        for (LoanApplication existing : applications) {
            if (existing.getCustomerId().equals(customerId)
                    && existing.getStatus() == ApplicationStatus.PENDING) {
                throw new IllegalArgumentException(
                        "There is already an application waiting for approval. It must be decided first."
                );
            }
        }

        LoanAssessment assessment = assess(customer, principal, today);

        LoanApplication application = new LoanApplication(
                nextApplicationId(), customerId, principal, rate, termMonths, today,
                assessment.getAdvice(), assessment.getReasons(),
                assessment.getCreditScore().getScore(), assessment.getCreditScore().getBand().name()
        );

        if (rulesAccepted) {
            application.acceptRules(today);
        }

        if (assessment.isDeclined()) {
            application.decline(String.join(" ", assessment.getReasons()), "system", today);
        }

        applications.add(application);
        return application;
    }

    // The admin approves: the application becomes a real loan. The rules are
    // checked again first, because things may have changed since the customer applied.
    // interestRateOverride can be null to keep the application's rate.
    public Loan approveApplication(String applicationId, BigDecimal interestRateOverride,
            String adminName, LocalDate today)
            throws ApplicationNotFoundException, CustomerNotFoundException, InsufficientFundsException,
            LoanLimitExceededException, OverdueInstallmentsException {

        LoanApplication application = findApplication(applicationId);

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalArgumentException("This application has already been "
                    + application.getStatus().name().toLowerCase() + ".");
        }

        BigDecimal rate = interestRateOverride;
        if (rate == null) {
            rate = application.getInterestRate();
        }
        checkInterestRate(rate);

        Customer customer = findCustomerById(application.getCustomerId());

        LoanAssessment assessment = assess(customer, application.getPrincipal(), today);
        if (assessment.isDeclined()) {
            throw new LoanLimitExceededException(
                    "This application can no longer be approved. " + String.join(" ", assessment.getReasons())
            );
        }

        Loan loan = applyForLoan(application.getCustomerId(), nextLoanId(), application.getPrincipal(),
                rate, application.getTermMonths(), today);

        application.approve(loan.getLoanId(), rate, adminName, today);
        return loan;
    }

    public void declineApplication(String applicationId, String note, String adminName, LocalDate today)
            throws ApplicationNotFoundException {

        LoanApplication application = findApplication(applicationId);

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalArgumentException("This application has already been "
                    + application.getStatus().name().toLowerCase() + ".");
        }

        String reason = note;
        if (reason == null || reason.isBlank()) {
            reason = "Declined by the admin.";
        }

        application.decline(reason.trim(), adminName, today);
    }

    public List<LoanApplication> getApplications() {
        return applications;
    }

    public List<LoanApplication> applicationsForCustomer(String customerId) {
        List<LoanApplication> result = new ArrayList<>();

        for (LoanApplication application : applications) {
            if (application.getCustomerId().equals(customerId)) {
                result.add(application);
            }
        }

        return result;
    }

    public LoanApplication findApplication(String applicationId) throws ApplicationNotFoundException {

        for (LoanApplication application : applications) {
            if (application.getApplicationId().equals(applicationId)) {
                return application;
            }
        }

        throw new ApplicationNotFoundException("Application " + applicationId + " was not found.");
    }

    private boolean hasUnpaidDefault(Customer customer) {
        for (Loan loan : customer.getLoans()) {
            if (loan.getStatus() == LoanStatus.DEFAULTED
                    && loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
        }
        return false;
    }

    // loan IDs are made by the system: L001, L002... skipping any already used
    private String nextLoanId() {
        int number = loans.size() + 1;

        while (true) {
            String id = "L" + String.format("%03d", number);
            boolean taken = false;

            for (Loan loan : loans) {
                if (loan.getLoanId().equals(id)) {
                    taken = true;
                }
            }

            if (!taken) {
                return id;
            }
            number++;
        }
    }

    // applications are never deleted, so counting them gives a unique number
    private String nextApplicationId() {
        return "A" + String.format("%03d", applications.size() + 1);
    }

    private static String bandName(CreditBand band) {
        String name = band.name().toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    // formats a naira amount: 12000 -> ₦12,000 and 99.5 -> ₦99.50
    public static String naira(BigDecimal amount) {
        String text = String.format(java.util.Locale.US, "%,.2f", amount.setScale(2, RoundingMode.HALF_UP));
        if (text.endsWith(".00")) {
            text = text.substring(0, text.length() - 3);
        }
        return "₦" + text;
    }

    // ---------- Logins ----------
    //
    // Passwords are only ever stored as hashes. New accounts get a random
    // temporary password that the person must change the first time they sign in.

    // used so a wrong username takes as long to reject as a wrong password
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password");

    public boolean hasAdmin() {
        for (UserAccount account : users) {
            if (account.getRole() == UserRole.ADMIN) {
                return true;
            }
        }
        return false;
    }

    // usernames are not case sensitive; returns null if there is no such account
    public UserAccount findUser(String username) {
        if (username == null) {
            return null;
        }

        for (UserAccount account : users) {
            if (account.getUsername().equalsIgnoreCase(username.trim())) {
                return account;
            }
        }
        return null;
    }

    public UserAccount findUserForCustomer(String customerId) {
        for (UserAccount account : users) {
            if (customerId.equals(account.getCustomerId())) {
                return account;
            }
        }
        return null;
    }

    // creates the admin login and returns the temporary password
    public String createAdmin(String username) {
        String temporary = PasswordHasher.randomPassword();
        users.add(new UserAccount(username, PasswordHasher.hash(temporary), UserRole.ADMIN, null, true));
        return temporary;
    }

    // Registers the customer and their login together.
    // The username is the customer ID in lower case. Returns the temporary password.
    public String registerCustomerWithLogin(Customer customer) throws CustomerAlreadyExistsException {

        registerCustomer(customer);

        String username = customer.getCustomerId().toLowerCase();

        if (findUser(username) != null) {
            customers.remove(customer);
            throw new IllegalArgumentException("The username " + username + " is already taken.");
        }

        String temporary = PasswordHasher.randomPassword();
        users.add(new UserAccount(username, PasswordHasher.hash(temporary),
                UserRole.USER, customer.getCustomerId(), true));
        return temporary;
    }

    // Gives the customer a new temporary password (creating their login if
    // they don't have one yet). Returns the temporary password.
    public String resetPassword(String customerId) throws CustomerNotFoundException {

        findCustomerById(customerId);

        String temporary = PasswordHasher.randomPassword();
        UserAccount account = findUserForCustomer(customerId);

        if (account == null) {
            String username = customerId.toLowerCase();

            if (findUser(username) != null) {
                throw new IllegalArgumentException("The username " + username + " is already taken.");
            }

            users.add(new UserAccount(username, PasswordHasher.hash(temporary),
                    UserRole.USER, customerId, true));
        } else {
            account.setPassword(PasswordHasher.hash(temporary), true);
        }

        return temporary;
    }

    // returns the account if the username and password are right, otherwise null
    public UserAccount authenticate(String username, String password) {
        UserAccount account = findUser(username);

        if (account == null || password == null) {
            PasswordHasher.matches("x", DUMMY_HASH);
            return null;
        }

        if (PasswordHasher.matches(password, account.getPasswordHash())) {
            return account;
        }
        return null;
    }

    public void changePassword(String username, String currentPassword, String newPassword) {

        UserAccount account = authenticate(username, currentPassword);
        if (account == null) {
            throw new IllegalArgumentException("The current password is not right.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("The new password must be at least 8 characters.");
        }

        if (newPassword.equalsIgnoreCase(account.getUsername())) {
            throw new IllegalArgumentException("The new password can't be the same as the username.");
        }

        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("The new password must be different from the current one.");
        }

        account.setPassword(PasswordHasher.hash(newPassword), false);
    }

    // ---------- The finance report ----------

    // Money earned: the interest collected plus the late fees, the interest on late amounts
    // and the prepayment charges paid.
    // Each paid installment carries its share of the loan's interest and any late fee on it,
    // counted in the month it was paid. A prepayment charge is counted in the month of the payment.
    // Money lost: the unpaid part of the money lent on loans that defaulted,
    // counted in the month the loan defaulted.
    // Profit: money earned minus money lost.
    // The table runs from the first month with anything in it up to this month.
    public FinanceReport financeReport(LocalDate today) {

        Map<YearMonth, BigDecimal> interest = new TreeMap<>();
        Map<YearMonth, BigDecimal> lateFees = new TreeMap<>();
        Map<YearMonth, BigDecimal> lateInterest = new TreeMap<>();
        Map<YearMonth, BigDecimal> prepaymentFees = new TreeMap<>();
        Map<YearMonth, BigDecimal> badDebt = new TreeMap<>();

        for (Loan loan : loans) {

            BigDecimal interestPerInstallment = loan.getInterestPerInstallment();

            for (Installment installment : loan.getInstallments()) {
                if (installment.isPaid() && installment.getPaidDate() != null) {
                    YearMonth paidIn = YearMonth.from(installment.getPaidDate());
                    addToMonth(interest, paidIn, interestPerInstallment);
                    addToMonth(lateFees, paidIn, installment.getLateFee());
                    addToMonth(lateInterest, paidIn, installment.getLateInterest());
                }
            }

            if (loan.getStatus() == LoanStatus.DEFAULTED) {

                // what is still owed includes interest that was never earned,
                // so only the principal share of it counts as a loss
                BigDecimal unpaidPrincipal = loan.getOutstandingBalance()
                        .multiply(loan.getPrincipal())
                        .divide(loan.getTotalRepayable().add(loan.getTotalLateFees()).add(loan.getTotalLateInterest()),
                                2, RoundingMode.HALF_UP);

                LocalDate when = loan.getDefaultedDate();
                if (when == null) {
                    when = today;
                }
                addToMonth(badDebt, YearMonth.from(when), unpaidPrincipal);
            }
        }

        for (PaymentRecord payment : payments) {
            addToMonth(prepaymentFees, YearMonth.from(payment.getDate()), payment.getPrepaymentFee());
        }

        YearMonth first = YearMonth.from(today);
        YearMonth last = YearMonth.from(today);

        for (Map<YearMonth, BigDecimal> map : List.of(interest, lateFees, lateInterest, prepaymentFees, badDebt)) {
            for (YearMonth month : map.keySet()) {
                if (month.isBefore(first)) {
                    first = month;
                }
                if (month.isAfter(last)) {
                    last = month;
                }
            }
        }

        List<MonthlyFinance> months = new ArrayList<>();

        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            months.add(new MonthlyFinance(
                    month,
                    interest.getOrDefault(month, BigDecimal.ZERO),
                    lateFees.getOrDefault(month, BigDecimal.ZERO),
                    lateInterest.getOrDefault(month, BigDecimal.ZERO),
                    prepaymentFees.getOrDefault(month, BigDecimal.ZERO),
                    badDebt.getOrDefault(month, BigDecimal.ZERO)
            ));
        }

        return new FinanceReport(months);
    }

    private void addToMonth(Map<YearMonth, BigDecimal> map, YearMonth month, BigDecimal amount) {
        map.put(month, map.getOrDefault(month, BigDecimal.ZERO).add(amount));
    }
}
