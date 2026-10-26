package bank.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import bank.ai.AiService;
import bank.ai.AiUnavailableException;
import bank.demo.DemoDataSeeder;
import bank.model.ApplicationInsight;
import bank.model.ApplicationStatus;
import bank.model.CaseStage;
import bank.model.CollectionStatus;
import bank.model.LawyerFile;
import bank.model.Bank;
import bank.model.PaymentResult;
import bank.model.Customer;
import bank.model.Loan;
import bank.model.LoanApplication;
import bank.model.LoanStatus;
import bank.model.LoanStatusSummary;
import bank.model.OverdueEntry;
import bank.model.UserAccount;
import bank.model.WatchEntry;
import bank.model.WatchList;

// The routes only the admin can use. ApiHandler has already checked that the
// person is signed in as the admin before any of these run.
public class AdminApi {

    private static final Pattern CUSTOMER_ID = Pattern.compile("[A-Za-z0-9_-]{2,20}");

    // how many names on the early-warning list the page shows, and how many the AI rewords
    private static final int WATCHLIST_SHOWN = 20;
    private static final int WATCHLIST_REWORDED = 10;

    // how many times to ask the AI when its answer fails the checks
    private static final int AI_ATTEMPTS = 2;

    private final BankService service;
    private final AiService ai;

    public AdminApi(BankService service, AiService ai) {
        this.service = service;
        this.ai = ai;
    }

    // returns true if the request matched one of the admin routes
    public boolean handle(Request req) throws Exception {

        // ----- customers -----
        if (req.matches("GET", "customers")) {
            listCustomers(req);
        } else if (req.matches("POST", "customers")) {
            createCustomer(req);
        } else if (req.matches("GET", "customers", "*")) {
            customerDetails(req, req.part(1));
        } else if (req.matches("POST", "customers", "*", "reset-password")) {
            resetPassword(req, req.part(1));

        // ----- loans and payments -----
        } else if (req.matches("GET", "loans")) {
            searchLoans(req);
        } else if (req.matches("GET", "loans", "*")) {
            loanDetails(req, req.part(1));
        } else if (req.matches("POST", "loans", "*", "payments")) {
            recordPayment(req, req.part(1));

        // ----- applications -----
        } else if (req.matches("GET", "applications")) {
            listApplications(req);
        } else if (req.matches("GET", "applications", "*")) {
            applicationDetails(req, req.part(1));
        } else if (req.matches("GET", "applications", "*", "ai-summary")) {
            aiSummary(req, req.part(1));
        } else if (req.matches("POST", "applications", "*", "approve")) {
            approve(req, req.part(1));
        } else if (req.matches("POST", "applications", "*", "decline")) {
            decline(req, req.part(1));

        // ----- late customers: warning, escalation, lawyer -----
        } else if (req.matches("GET", "collections")) {
            listCollections(req);
        } else if (req.matches("GET", "collections", "*", "warning-preview")) {
            warningPreview(req, req.part(1));
        } else if (req.matches("POST", "collections", "*", "warning")) {
            sendWarning(req, req.part(1));
        } else if (req.matches("GET", "collections", "*", "lawyer-file")) {
            lawyerFile(req, req.part(1));
        } else if (req.matches("POST", "collections", "*", "refer")) {
            refer(req, req.part(1));

        // ----- early-warning list -----
        } else if (req.matches("GET", "watchlist")) {
            watchlist(req);
        } else if (req.matches("GET", "watchlist", "ai")) {
            watchlistAi(req);

        // ----- reports and finance -----
        } else if (req.matches("GET", "reports", "summary")) {
            summaryReport(req);
        } else if (req.matches("GET", "reports", "top-borrowers")) {
            topBorrowers(req);
        } else if (req.matches("GET", "reports", "overdue")) {
            overdueReport(req);
        } else if (req.matches("GET", "finance")) {
            finance(req);

        // ----- demo data -----
        } else if (req.matches("POST", "demo-data")) {
            loadDemoData(req);

        } else {
            return false;
        }

        return true;
    }

    // ---------- Customers ----------

    private void listCustomers(Request req) throws Exception {
        String json = service.read(bank -> {
            LocalDate today = LocalDate.now();
            List<String> items = new ArrayList<>();

            for (Customer customer : bank.getAllCustomers()) {
                items.add(JsonViews.customer(bank, customer, today).toString());
            }
            return Json.array(items);
        });

        req.send(200, json);
    }

    // creates the customer and their login; the temporary password is shown once
    private void createCustomer(Request req) throws Exception {
        FormData form = req.form();

        String customerId = form.requireText("customerId", "Customer ID");
        String name = form.requireText("name", "Name");
        String email = form.requireText("email", "Email");

        if (!CUSTOMER_ID.matcher(customerId).matches()) {
            throw new IllegalArgumentException(
                    "The customer ID can use letters, digits, - and _ only, 2 to 20 characters.");
        }

        if (name.length() > 80) {
            throw new IllegalArgumentException("The name is too long.");
        }

        if (!email.contains("@") || email.length() > 120) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }

        String json = service.change(bank -> {
            Customer customer = new Customer(customerId, name, email);
            String temporaryPassword = bank.registerCustomerWithLogin(customer);

            return JsonViews.customer(bank, customer, LocalDate.now())
                    .add("username", customerId.toLowerCase())
                    .add("temporaryPassword", temporaryPassword)
                    .toString();
        });

        req.send(201, json);
    }

    private void customerDetails(Request req, String customerId) throws Exception {
        String json = service.read(bank -> {
            LocalDate today = LocalDate.now();
            Customer customer = bank.findCustomerById(customerId);

            List<String> loans = new ArrayList<>();
            for (Loan loan : customer.getLoans()) {
                loans.add(JsonViews.loan(loan).toString());
            }

            List<String> applications = new ArrayList<>();
            for (LoanApplication application : bank.applicationsForCustomer(customerId)) {
                applications.add(JsonViews.applicationForAdmin(bank, application).toString());
            }

            return JsonViews.customer(bank, customer, today)
                    .addRaw("scoreDetails", JsonViews.score(bank.scoreOf(customer, today)).toString())
                    .addRaw("loans", Json.array(loans))
                    .addRaw("applications", Json.array(applications))
                    .toString();
        });

        req.send(200, json);
    }

    private void resetPassword(Request req, String customerId) throws Exception {
        String json = service.change(bank -> {
            String temporaryPassword = bank.resetPassword(customerId);
            UserAccount account = bank.findUserForCustomer(customerId);

            return new Json()
                    .add("username", account.getUsername())
                    .add("temporaryPassword", temporaryPassword)
                    .toString();
        });

        req.send(200, json);
    }

    // ---------- Loans and payments ----------

    // GET /api/loans                   -> every loan
    // GET /api/loans?customerId=C001   -> one customer's loans
    // GET /api/loans?status=ACTIVE     -> loans with that status
    // GET /api/loans?sort=balance      -> every loan, biggest balance first
    private void searchLoans(Request req) throws Exception {
        FormData query = req.query();

        String json = service.read(bank -> {
            List<Loan> loans;

            if (query.get("customerId") != null) {
                loans = bank.findLoansByCustomer(query.get("customerId"));
            } else if (query.get("status") != null) {
                loans = bank.findLoansByStatus(readStatus(query.get("status")));
            } else if ("balance".equals(query.get("sort"))) {
                loans = bank.loansSortedByOutstandingBalanceDesc();
            } else {
                loans = bank.getAllLoans();
            }

            List<String> items = new ArrayList<>();
            for (Loan loan : loans) {
                items.add(JsonViews.loan(loan).toString());
            }
            return Json.array(items);
        });

        req.send(200, json);
    }

    private void loanDetails(Request req, String loanId) throws Exception {
        String filter = req.query().get("installments");

        String json = service.read(bank ->
                JsonViews.loanDetails(bank, bank.findLoanById(loanId), filter).toString());

        req.send(200, json);
    }

    // the admin records a payment on a customer's behalf (e.g. cash at the counter)
    private void recordPayment(Request req, String loanId) throws Exception {
        BigDecimal amount = req.form().requireDecimal("amount", "Payment amount");
        String adminName = req.session().getUsername();

        String json = service.change(bank -> {
            PaymentResult result = bank.makePaymentDetailed(loanId, amount, LocalDate.now(), adminName, true);

            return JsonViews.loanDetails(bank, bank.findLoanById(loanId), "all")
                    .add("overpayment", result.getOverpayment())
                    .add("prepaymentFee", result.getPrepaymentFee())
                    .toString();
        });

        req.send(200, json);
    }

    // ---------- Applications ----------

    // GET /api/applications?status=PENDING|APPROVED|DECLINED  (no status = all), newest first
    private void listApplications(Request req) throws Exception {
        String status = req.query().get("status");

        String json = service.read(bank -> {
            List<String> items = new ArrayList<>();
            List<LoanApplication> all = bank.getApplications();

            for (int i = all.size() - 1; i >= 0; i--) {
                LoanApplication application = all.get(i);

                if (status == null || application.getStatus() == readApplicationStatus(status)) {
                    items.add(JsonViews.applicationForAdmin(bank, application).toString());
                }
            }
            return Json.array(items);
        });

        req.send(200, json);
    }

    // one application, plus the customer's score as it is now and the plain summary
    // the system wrote (it comes with the application, so the pop-up has it at once;
    // the AI's version is asked for separately, see aiSummary)
    private void applicationDetails(Request req, String applicationId) throws Exception {
        String json = service.read(bank -> {
            LocalDate today = LocalDate.now();
            LoanApplication application = bank.findApplication(applicationId);
            Customer customer = bank.findCustomerById(application.getCustomerId());
            ApplicationInsight insight = ApplicationInsight.of(bank, application, today);

            return JsonViews.applicationForAdmin(bank, application)
                    .addRaw("scoreNow", JsonViews.score(bank.scoreOf(customer, today)).toString())
                    .addRaw("summary", JsonViews.summaryParts(insight.getParts(), "system").toString())
                    .add("aiAvailable", ai.isConfigured())
                    .toString();
        });

        req.send(200, json);
    }

    // The AI's wording of the same summary. The facts are worked out inside the lock
    // (fast), then the lock is let go BEFORE the slow call, so one slow answer can
    // never hold up the rest of the app. If the AI cannot help, this answers with an
    // error and the page keeps the system's summary.
    private void aiSummary(Request req, String applicationId) throws Exception {
        LocalDate today = LocalDate.now();

        ApplicationInsight insight = service.read(bank ->
                ApplicationInsight.of(bank, bank.findApplication(applicationId), today));

        String question = insight.promptFacts();

        // The AI's wording varies from one try to the next, and now and then a reply fails
        // the checks (a different advice word, an invented number). Try once more before
        // giving up, and never keep a rejected reply: it is forgotten so the next try is fresh.
        for (int attempt = 1; attempt <= AI_ATTEMPTS; attempt++) {
            String reply = askAi(ApplicationInsight.AI_INSTRUCTIONS, question, today);

            var parts = ApplicationInsight.parseAiReply(reply, insight.getAdvice(), question);
            if (parts.isPresent()) {
                req.send(200, JsonViews.summaryParts(parts.get(), "ai").toString());
                return;
            }

            System.out.println("AI reply rejected (checks failed), attempt " + attempt + ":\n" + reply);
            ai.forget(ApplicationInsight.AI_INSTRUCTIONS, question, today);
        }

        throw new ApiException(502, "The AI's answer could not be used, so the system's summary is shown.");
    }

    // asks the AI, and turns "the AI is not available" into a clear error for the page
    private String askAi(String instructions, String question, LocalDate today) {
        try {
            return ai.ask(instructions, question, today);
        } catch (AiUnavailableException e) {
            System.out.println("AI could not help: " + e.getMessage()); // never includes the key
            throw new ApiException(503, e.getMessage());
        }
    }

    // The admin approves; the application becomes a loan. interestRate (a
    // decimal such as 0.2) is optional and replaces the application's rate.
    private void approve(Request req, String applicationId) throws Exception {
        String rateText = req.form().get("interestRate");
        String adminName = req.session().getUsername();

        BigDecimal rate = null;
        if (rateText != null) {
            rate = req.form().requireDecimal("interestRate", "Interest rate");
        }

        BigDecimal finalRate = rate;

        String json = service.change(bank -> {
            Loan loan = bank.approveApplication(applicationId, finalRate, adminName, LocalDate.now());
            return JsonViews.loan(loan).toString();
        });

        req.send(200, json);
    }

    private void decline(Request req, String applicationId) throws Exception {
        String note = req.form().get("note");
        String adminName = req.session().getUsername();

        String json = service.change(bank -> {
            bank.declineApplication(applicationId, note, adminName, LocalDate.now());

            return JsonViews.applicationForAdmin(bank, bank.findApplication(applicationId)).toString();
        });

        req.send(200, json);
    }

    // ---------- Late customers ----------

    // every late customer with their stage, plus the two counts for the menu and the Dashboard
    private void listCollections(Request req) throws Exception {
        String json = service.read(bank -> {
            LocalDate today = LocalDate.now();
            List<String> items = new ArrayList<>();

            for (CollectionStatus status : bank.collectionStatuses(today)) {
                items.add(JsonViews.collection(status).toString());
            }

            return new Json()
                    .add("warningsToSend", bank.countInStage(CaseStage.SEND_WARNING, today))
                    .add("needsEscalation", bank.countInStage(CaseStage.NEEDS_ESCALATION, today))
                    .addRaw("customers", Json.array(items))
                    .toString();
        });

        req.send(200, json);
    }

    // the exact message that will go to the customer's page, so the lender can check it first
    private void warningPreview(Request req, String customerId) throws Exception {
        String json = service.read(bank -> {
            LocalDate today = LocalDate.now();

            return new Json()
                    .add("customerId", customerId)
                    .add("message", bank.warningPreview(customerId, today))
                    .add("deadline", today.plusDays(Bank.ESCALATE_AFTER_DAYS).toString())
                    .toString();
        });

        req.send(200, json);
    }

    private void sendWarning(Request req, String customerId) throws Exception {
        String adminName = req.session().getUsername();

        String json = service.change(bank -> {
            LocalDate today = LocalDate.now();
            bank.sendWarning(customerId, adminName, today);

            return JsonViews.collection(bank.collectionStatus(bank.findCustomerById(customerId), today)).toString();
        });

        req.send(200, json);
    }

    // the lawyer file: plain text the lender can copy, plus whether it can be marked as referred
    private void lawyerFile(Request req, String customerId) throws Exception {
        String json = service.read(bank -> {
            LocalDate today = LocalDate.now();
            CollectionStatus status = bank.collectionStatus(bank.findCustomerById(customerId), today);

            if (status.getStage() == CaseStage.NONE) {
                throw new IllegalArgumentException("This customer has nothing overdue.");
            }

            return JsonViews.collection(status)
                    .add("text", LawyerFile.build(bank, status, today))
                    .add("canRefer", status.getStage() == CaseStage.NEEDS_ESCALATION)
                    .toString();
        });

        req.send(200, json);
    }

    private void refer(Request req, String customerId) throws Exception {
        String adminName = req.session().getUsername();
        String note = req.form().get("note");

        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("The note is too long (500 characters at most).");
        }

        String json = service.change(bank -> {
            LocalDate today = LocalDate.now();
            bank.markReferred(customerId, note, adminName, today);

            return JsonViews.collection(bank.collectionStatus(bank.findCustomerById(customerId), today)).toString();
        });

        req.send(200, json);
    }

    // ---------- Early-warning list ----------

    // the customers who may miss their next payment, each with the system's sentence
    private void watchlist(Request req) throws Exception {
        String json = service.read(bank -> {
            List<WatchEntry> entries = WatchList.build(bank, LocalDate.now());

            List<String> items = new ArrayList<>();
            for (WatchEntry entry : entries.subList(0, Math.min(entries.size(), WATCHLIST_SHOWN))) {
                items.add(JsonViews.watchEntry(entry).toString());
            }

            return new Json()
                    .addRaw("entries", Json.array(items))
                    .add("total", entries.size())
                    .add("aiAvailable", ai.isConfigured())
                    .toString();
        });

        req.send(200, json);
    }

    // The AI's wording of the first names on the list, in one call. As with the
    // application summary, the list is built inside the lock and the call is made after.
    private void watchlistAi(Request req) throws Exception {
        LocalDate today = LocalDate.now();

        List<WatchEntry> top = service.read(bank -> {
            List<WatchEntry> entries = WatchList.build(bank, today);
            return new ArrayList<>(entries.subList(0, Math.min(entries.size(), WATCHLIST_REWORDED)));
        });

        Json sentences = new Json();

        if (!top.isEmpty()) {
            String question = WatchList.promptLines(top, today);

            // as with the application summary: try again once if nothing usable came back
            Map<String, String> reworded = Map.of();
            for (int attempt = 1; attempt <= AI_ATTEMPTS && reworded.isEmpty(); attempt++) {
                String reply = askAi(WatchList.AI_INSTRUCTIONS, question, today);
                reworded = WatchList.parseAiReply(reply, top, today);

                if (reworded.isEmpty()) {
                    System.out.println("AI reply rejected (checks failed), attempt " + attempt + ":\n" + reply);
                    ai.forget(WatchList.AI_INSTRUCTIONS, question, today);
                }
            }

            if (reworded.isEmpty()) {
                throw new ApiException(502, "The AI's answer could not be used, so the system's sentences are shown.");
            }

            for (Map.Entry<String, String> entry : reworded.entrySet()) {
                sentences.add(entry.getKey(), entry.getValue());
            }
        }

        req.send(200, new Json().addRaw("sentences", sentences.toString()).toString());
    }

    // ---------- Reports ----------

    private void summaryReport(Request req) throws Exception {
        String json = service.read(bank -> {
            LoanStatusSummary summary = bank.loanStatusSummary();

            int pending = 0;
            for (LoanApplication application : bank.getApplications()) {
                if (application.getStatus() == ApplicationStatus.PENDING) {
                    pending++;
                }
            }

            return new Json()
                    .add("active", summary.getActiveCount())
                    .add("paidOff", summary.getPaidOffCount())
                    .add("defaulted", summary.getDefaultedCount())
                    .add("pendingApplications", pending)
                    .add("warningsToSend", bank.countInStage(CaseStage.SEND_WARNING, LocalDate.now()))
                    .add("needsEscalation", bank.countInStage(CaseStage.NEEDS_ESCALATION, LocalDate.now()))
                    .add("interestEarned", bank.totalInterestEarned())
                    .add("availableFunds", bank.getAvailableFunds())
                    .toString();
        });

        req.send(200, json);
    }

    private void topBorrowers(Request req) throws Exception {
        int limit = 5;
        if (req.query().get("limit") != null) {
            limit = req.query().requireInt("limit", "Limit");
        }
        int finalLimit = limit;

        String json = service.read(bank -> {
            List<String> items = new ArrayList<>();

            for (Customer customer : bank.topBorrowers(finalLimit)) {
                items.add(new Json()
                        .add("customerId", customer.getCustomerId())
                        .add("name", customer.getName())
                        .add("totalBorrowed", bank.totalPrincipalBorrowed(customer))
                        .toString());
            }
            return Json.array(items);
        });

        req.send(200, json);
    }

    private void overdueReport(Request req) throws Exception {
        String json = service.read(bank -> {
            List<String> items = new ArrayList<>();

            for (OverdueEntry entry : bank.currentlyOverdueLoans(LocalDate.now())) {
                items.add(new Json()
                        .add("customerId", entry.getCustomerId())
                        .add("customerName", entry.getCustomerName())
                        .add("loanId", entry.getLoanId())
                        .add("dueDate", entry.getDueDate().toString())
                        .add("daysOverdue", entry.getDaysOverdue())
                        .add("band", entry.getBand().name())
                        .add("bandLabel", entry.getBand().getLabel())
                        .add("baseAmount", entry.getBaseAmount())
                        .add("lateFee", entry.getLateFee())
                        .add("lateInterest", entry.getLateInterest())
                        .add("owedToday", entry.getOwedToday())
                        .toString());
            }
            return Json.array(items);
        });

        req.send(200, json);
    }

    // ---------- Finance ----------

    private void finance(Request req) throws Exception {
        String json = service.read(bank -> JsonViews.finance(bank, LocalDate.now()).toString());
        req.send(200, json);
    }

    // ---------- Demo data ----------

    private void loadDemoData(Request req) throws Exception {
        String json;
        try {
            json = service.change(bank -> {
                LocalDate today = LocalDate.now();
                int added = DemoDataSeeder.seed(bank, today) + DemoDataSeeder.seedWatchListExamples(bank, today);
                return new Json().add("customersAdded", added).toString();
            });
        } catch (IllegalStateException e) {
            // "already loaded" or "not enough funds": a clear conflict, not a crash
            throw new ApiException(409, e.getMessage());
        }

        req.send(201, json);
    }

    // ---------- Helpers ----------

    private LoanStatus readStatus(String text) {
        try {
            return LoanStatus.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status must be ACTIVE, PAID_OFF or DEFAULTED.");
        }
    }

    private ApplicationStatus readApplicationStatus(String text) {
        try {
            return ApplicationStatus.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status must be PENDING, APPROVED or DECLINED.");
        }
    }
}
