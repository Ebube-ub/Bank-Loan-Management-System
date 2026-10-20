package bank.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import bank.exception.LoanNotFoundException;
import bank.model.ApplicationStatus;
import bank.model.Bank;
import bank.model.PaymentResult;
import bank.model.CreditBand;
import bank.model.CreditScore;
import bank.model.Customer;
import bank.model.Loan;
import bank.model.LoanApplication;
import bank.model.LoanAssessment;

// The routes for a signed-in customer (all under /api/my/...). Every one of
// them works on the customer the login belongs to, never on an ID the page
// sends, so a customer can't look at or pay for someone else's loans.
public class UserApi {

    private final BankService service;

    public UserApi(BankService service) {
        this.service = service;
    }

    // returns true if the request matched one of the customer routes
    public boolean handle(Request req) throws Exception {

        // parts are ["my", ...], so the interesting part starts at index 1
        if (req.matches("GET", "my", "summary")) {
            summary(req);
        } else if (req.matches("GET", "my", "loans")) {
            myLoans(req);
        } else if (req.matches("GET", "my", "loans", "*")) {
            loanDetails(req, req.part(2));
        } else if (req.matches("POST", "my", "loans", "*", "payments")) {
            makePayment(req, req.part(2));
        } else if (req.matches("GET", "my", "applications")) {
            myApplications(req);
        } else if (req.matches("POST", "my", "applications")) {
            apply(req);
        } else {
            return false;
        }

        return true;
    }

    // score, band, limits, and whether (and how much) they can borrow right now
    private void summary(Request req) throws Exception {
        String customerId = req.session().getCustomerId();

        String json = service.read(bank -> {
            LocalDate today = LocalDate.now();
            Customer customer = bank.findCustomerById(customerId);

            LoanAssessment assessment = bank.assess(customer, null, today);
            CreditScore score = assessment.getCreditScore();
            CreditBand band = score.getBand();

            boolean pending = hasPendingApplication(bank, customerId);

            // the reasons are only shown when the customer can't borrow
            List<String> reasons = new ArrayList<>();
            if (assessment.isDeclined()) {
                reasons.addAll(assessment.getReasons());
            } else if (pending) {
                reasons.add("You already have an application waiting for approval.");
            }

            return new Json()
                    .add("customerId", customer.getCustomerId())
                    .add("name", customer.getName())
                    .add("email", customer.getEmail())
                    .add("score", score.getScore())
                    .add("band", band.name())
                    .add("tier", JsonViews.tierLabel(band))
                    .add("activeLoans", customer.getActiveLoans().size())
                    .add("maxActiveLoans", band.getMaxActiveLoans())
                    .add("maxSingleLoan", band.getMaxSingleLoan())
                    .add("borrowingLimit", Bank.MAX_TOTAL_BORROWED)
                    .add("availableToBorrow", assessment.getMaxAmount())
                    .add("canApply", !assessment.isDeclined() && !pending)
                    .add("hasPendingApplication", pending)
                    .addRaw("reasons", Json.stringArray(reasons))
                    .addRaw("scoreDetails", JsonViews.score(score).toString())
                    .add("interestPercent", JsonViews.percent(bank.getDefaultInterestRate()))
                    .add("lateFeePercent", JsonViews.percent(Bank.LATE_FEE_RATE))
                    .add("prepaymentFeePercent", JsonViews.percent(Bank.PREPAYMENT_FEE_RATE))
                    .add("monthlyPercent", JsonViews.monthlyPercent(bank.getDefaultInterestRate()))
                    .add("warningAfterDays", Bank.WARNING_AFTER_DAYS)
                    .add("escalateAfterDays", Bank.ESCALATE_AFTER_DAYS)
                    .addRaw("lateExample", JsonViews.lateExample(bank.getDefaultInterestRate()).toString())
                    .addRaw("notice", JsonViews.customerNotice(bank.collectionStatus(customer, today)))
                    .toString();
        });

        req.send(200, json);
    }

    private void myLoans(Request req) throws Exception {
        String customerId = req.session().getCustomerId();

        String json = service.read(bank -> {
            List<String> items = new ArrayList<>();

            for (Loan loan : bank.findLoansByCustomer(customerId)) {
                items.add(JsonViews.loan(loan).toString());
            }
            return Json.array(items);
        });

        req.send(200, json);
    }

    private void loanDetails(Request req, String loanId) throws Exception {
        String customerId = req.session().getCustomerId();
        String filter = req.query().get("installments");

        String json = service.read(bank ->
                JsonViews.loanDetails(bank, ownLoan(bank, loanId, customerId), filter).toString());

        req.send(200, json);
    }

    private void makePayment(Request req, String loanId) throws Exception {
        String customerId = req.session().getCustomerId();
        String username = req.session().getUsername();
        BigDecimal amount = req.form().requireDecimal("amount", "Payment amount");

        String json = service.change(bank -> {
            ownLoan(bank, loanId, customerId); // makes sure it is their loan

            PaymentResult result = bank.makePaymentDetailed(loanId, amount, LocalDate.now(), username, false);

            return JsonViews.loanDetails(bank, bank.findLoanById(loanId), "all")
                    .add("overpayment", result.getOverpayment())
                    .add("prepaymentFee", result.getPrepaymentFee())
                    .toString();
        });

        req.send(200, json);
    }

    private void myApplications(Request req) throws Exception {
        String customerId = req.session().getCustomerId();

        String json = service.read(bank -> {
            List<String> items = new ArrayList<>();
            List<LoanApplication> mine = bank.applicationsForCustomer(customerId);

            // newest first
            for (int i = mine.size() - 1; i >= 0; i--) {
                items.add(JsonViews.applicationForCustomer(bank, mine.get(i)).toString());
            }
            return Json.array(items);
        });

        req.send(200, json);
    }

    // The customer asks for a loan: just the amount and the term. The interest
    // rate is the lender's, and the loan ID is made by the system.
    private void apply(Request req) throws Exception {
        String customerId = req.session().getCustomerId();
        BigDecimal principal = req.form().requireDecimal("principal", "Loan amount");
        int termMonths = req.form().requireInt("termMonths", "Term");

        // the customer must tick the box saying they have read and understood the rules
        if (!"true".equals(req.form().get("acceptRules"))) {
            throw new IllegalArgumentException(
                    "Tick the box to confirm that you have read and understood the rules before you apply.");
        }

        String json = service.change(bank -> {
            LoanApplication application = bank.submitApplication(
                    customerId, principal, null, termMonths, LocalDate.now(), true);

            return JsonViews.applicationForCustomer(bank, application).toString();
        });

        req.send(201, json);
    }

    // ---------- Helpers ----------

    // Finds a loan, but only if it belongs to this customer. Someone else's loan
    // gets the same "not found" as a loan that doesn't exist, so IDs can't be guessed.
    private Loan ownLoan(Bank bank, String loanId, String customerId) throws LoanNotFoundException {
        Loan loan = bank.findLoanById(loanId);

        if (!loan.getCustomer().getCustomerId().equals(customerId)) {
            throw new LoanNotFoundException("Loan with ID " + loanId + " was not found.");
        }
        return loan;
    }

    private boolean hasPendingApplication(Bank bank, String customerId) {
        for (LoanApplication application : bank.applicationsForCustomer(customerId)) {
            if (application.getStatus() == ApplicationStatus.PENDING) {
                return true;
            }
        }
        return false;
    }
}
