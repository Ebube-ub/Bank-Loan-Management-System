package bank.web;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

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

// Handles every request that starts with /api.
//
// For each request it: works out which endpoint was asked for, reads the
// form fields, calls one BankService method, and sends the answer back as
// JSON. If anything throws, the exception is turned into an HTTP status
// code plus {"error": "..."} so the page can show the message.
public class ApiHandler implements HttpHandler {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BankService service;

    public ApiHandler(BankService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (CustomerNotFoundException | LoanNotFoundException e) {
            sendError(exchange, 404, e.getMessage());
        } catch (CustomerAlreadyExistsException e) {
            sendError(exchange, 409, e.getMessage());
        } catch (LoanLimitExceededException | OverdueInstallmentsException
                | InsufficientFundsException e) {
            sendError(exchange, 422, e.getMessage());
        } catch (Exception e) {
            // unexpected problem: full details go to the server console only
            e.printStackTrace();
            sendError(exchange, 500, "Something went wrong on the server.");
        }
    }

    // ---------- Routing ----------

    // Splits "/api/loans/L001/payments" into ["loans", "L001", "payments"]
    // and picks the matching endpoint.
    private void route(HttpExchange exchange) throws Exception {

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getRawPath().substring("/api".length());

        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                parts.add(FormData.decode(part));
            }
        }

        FormData query = new FormData(exchange.getRequestURI().getRawQuery());

        if (parts.size() == 1 && parts.get(0).equals("customers")) {
            if (method.equals("GET")) {
                listCustomers(exchange);
                return;
            }
            if (method.equals("POST")) {
                registerCustomer(exchange, readForm(exchange));
                return;
            }
        }

        if (parts.size() == 3 && parts.get(0).equals("customers")
                && parts.get(2).equals("eligibility") && method.equals("GET")) {
            checkEligibility(exchange, parts.get(1));
            return;
        }

        if (parts.size() == 1 && parts.get(0).equals("loans")) {
            if (method.equals("GET")) {
                searchLoans(exchange, query);
                return;
            }
            if (method.equals("POST")) {
                applyForLoan(exchange, readForm(exchange));
                return;
            }
        }

        if (parts.size() == 2 && parts.get(0).equals("loans") && method.equals("GET")) {
            loanDetails(exchange, parts.get(1), query);
            return;
        }

        if (parts.size() == 3 && parts.get(0).equals("loans")
                && parts.get(2).equals("payments") && method.equals("POST")) {
            makePayment(exchange, parts.get(1), readForm(exchange));
            return;
        }

        if (parts.size() == 2 && parts.get(0).equals("reports") && method.equals("GET")) {
            if (parts.get(1).equals("summary")) {
                summaryReport(exchange);
                return;
            }
            if (parts.get(1).equals("top-borrowers")) {
                topBorrowersReport(exchange, query);
                return;
            }
            if (parts.get(1).equals("overdue")) {
                overdueReport(exchange);
                return;
            }
        }

        sendError(exchange, 404, "No such endpoint: " + method + " /api" + path);
    }

    // ---------- Customers ----------

    private void listCustomers(HttpExchange exchange) throws IOException {
        List<String> items = new ArrayList<>();
        for (Customer customer : service.getCustomers()) {
            items.add(customerJson(customer));
        }
        sendJson(exchange, 200, Json.array(items));
    }

    private void registerCustomer(HttpExchange exchange, FormData form) throws Exception {
        String customerId = form.requireText("customerId", "Customer ID");
        String name = form.requireText("name", "Name");
        String email = form.requireText("email", "Email");
        CreditTier tier = readTier(form.requireText("tier", "Credit tier"));

        Customer customer = service.registerCustomer(customerId, name, email, tier);
        sendJson(exchange, 201, customerJson(customer));
    }

    private void checkEligibility(HttpExchange exchange, String customerId) throws Exception {
        service.checkEligibility(customerId);
        sendJson(exchange, 200, new Json().add("eligible", true).toString());
    }

    // ---------- Loans ----------

    // GET /api/loans                   -> every loan
    // GET /api/loans?customerId=C001   -> one customer's loans
    // GET /api/loans?status=ACTIVE     -> loans with that status
    // GET /api/loans?sort=balance      -> every loan, biggest balance first
    private void searchLoans(HttpExchange exchange, FormData query) throws Exception {
        List<Loan> loans;

        if (query.get("customerId") != null) {
            loans = service.findLoansByCustomer(query.get("customerId"));
        } else if (query.get("status") != null) {
            loans = service.findLoansByStatus(readStatus(query.get("status")));
        } else if ("balance".equals(query.get("sort"))) {
            loans = service.loansSortedByBalance();
        } else {
            loans = service.getAllLoans();
        }

        List<String> items = new ArrayList<>();
        for (Loan loan : loans) {
            items.add(loanJson(loan).toString());
        }
        sendJson(exchange, 200, Json.array(items));
    }

    private void applyForLoan(HttpExchange exchange, FormData form) throws Exception {
        String customerId = form.requireText("customerId", "Customer ID");
        String loanId = form.requireText("loanId", "Loan ID");
        BigDecimal principal = form.requireDecimal("principal", "Loan amount");
        BigDecimal interestRate = form.requireDecimal("interestRate", "Interest rate");
        int termMonths = form.requireInt("termMonths", "Term");

        Loan loan = service.applyForLoan(customerId, loanId, principal, interestRate, termMonths);
        sendJson(exchange, 201, loanJson(loan).toString());
    }

    // GET /api/loans/L001?installments=all|paid|unpaid
    private void loanDetails(HttpExchange exchange, String loanId, FormData query) throws Exception {
        Loan loan = service.getLoan(loanId);

        List<Installment> installments;
        String filter = query.get("installments");

        if ("paid".equals(filter)) {
            installments = service.installmentsByPaidStatus(loan, true);
        } else if ("unpaid".equals(filter)) {
            installments = service.installmentsByPaidStatus(loan, false);
        } else {
            installments = loan.getInstallments();
        }

        List<String> items = new ArrayList<>();
        for (Installment installment : installments) {
            items.add(new Json()
                    .add("dueDate", installment.getDueDate().format(DATE_FORMAT))
                    .add("amountDue", installment.getAmountDue())
                    .add("amountPaid", installment.getAmountPaid())
                    .add("status", installment.getStatus().name())
                    .toString());
        }

        Json json = loanJson(loan).addRaw("installments", Json.array(items));
        sendJson(exchange, 200, json.toString());
    }

    private void makePayment(HttpExchange exchange, String loanId, FormData form) throws Exception {
        BigDecimal amount = form.requireDecimal("amount", "Payment amount");

        BigDecimal overpayment = service.makePayment(loanId, amount);
        Loan loan = service.getLoan(loanId);

        Json json = loanJson(loan).add("overpayment", overpayment);
        sendJson(exchange, 200, json.toString());
    }

    // ---------- Reports ----------

    private void summaryReport(HttpExchange exchange) throws IOException {
        LoanStatusSummary summary = service.loanStatusSummary();

        String json = new Json()
                .add("active", summary.getActiveCount())
                .add("paidOff", summary.getPaidOffCount())
                .add("defaulted", summary.getDefaultedCount())
                .add("interestEarned", service.totalInterestEarned())
                .add("availableFunds", service.availableFunds())
                .toString();

        sendJson(exchange, 200, json);
    }

    private void topBorrowersReport(HttpExchange exchange, FormData query) throws IOException {
        int limit = 5;
        if (query.get("limit") != null) {
            limit = query.requireInt("limit", "Limit");
        }

        List<String> items = new ArrayList<>();
        for (Customer customer : service.topBorrowers(limit)) {
            items.add(new Json()
                    .add("customerId", customer.getCustomerId())
                    .add("name", customer.getName())
                    .add("totalBorrowed", service.totalPrincipalBorrowed(customer))
                    .toString());
        }
        sendJson(exchange, 200, Json.array(items));
    }

    private void overdueReport(HttpExchange exchange) throws IOException {
        List<String> items = new ArrayList<>();
        for (OverdueEntry entry : service.overdueLoans()) {
            items.add(new Json()
                    .add("customerName", entry.getCustomerName())
                    .add("loanId", entry.getLoanId())
                    .add("dueDate", entry.getDueDate().format(DATE_FORMAT))
                    .add("daysOverdue", entry.getDaysOverdue())
                    .toString());
        }
        sendJson(exchange, 200, Json.array(items));
    }

    // ---------- Turning model objects into JSON ----------

    private String customerJson(Customer customer) {
        return new Json()
                .add("customerId", customer.getCustomerId())
                .add("name", customer.getName())
                .add("email", customer.getEmail())
                .add("tier", customer.getCreditTier().name())
                .add("activeLoans", customer.getActiveLoans().size())
                .add("maxActiveLoans", Bank.maxActiveLoans(customer.getCreditTier()))
                .toString();
    }

    private Json loanJson(Loan loan) {
        return new Json()
                .add("loanId", loan.getLoanId())
                .add("customerId", loan.getCustomer().getCustomerId())
                .add("customerName", loan.getCustomer().getName())
                .add("principal", loan.getPrincipal())
                .add("interestRate", loan.getInterestRate().toPlainString())
                .add("termMonths", loan.getTermMonths())
                .add("disbursementDate", loan.getDisbursementDate().format(DATE_FORMAT))
                .add("totalRepayable", loan.getTotalRepayable())
                .add("outstanding", loan.getOutstandingBalance())
                .add("status", loan.getStatus().name())
                .add("installmentCount", loan.getInstallments().size());
    }

    // ---------- Small helpers ----------

    private CreditTier readTier(String text) {
        try {
            return CreditTier.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Credit tier must be STANDARD or PREMIUM.");
        }
    }

    private LoanStatus readStatus(String text) {
        try {
            return LoanStatus.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status must be ACTIVE, PAID_OFF or DEFAULTED.");
        }
    }

    private FormData readForm(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        return new FormData(new String(body, StandardCharsets.UTF_8));
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, new Json().add("error", message).toString());
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
