package bank.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import bank.model.ApplicationInsight;
import bank.model.ApplicationStatus;
import bank.model.Bank;
import bank.model.CreditBand;
import bank.model.CreditScore;
import bank.model.CaseStage;
import bank.model.CollectionCase;
import bank.model.CollectionMessages;
import bank.model.CollectionStatus;
import bank.model.Customer;
import bank.model.FinanceReport;
import bank.model.Installment;
import bank.model.Loan;
import bank.model.LoanApplication;
import bank.model.MonthlyFinance;
import bank.model.OverdueEntry;
import bank.model.PaymentRecord;
import bank.model.ScoreFactor;
import bank.model.UserAccount;
import bank.model.UserRole;
import bank.model.WatchEntry;

// Turns the bank's objects into the JSON the page needs. Kept in one place so
// the admin and user routes show things the same way.
//
// Money is always sent as text with 2 decimals ("12200.00"). Rates are sent as
// percentages ("20" means 20% a year) because that is how people read them.
public class JsonViews {

    // ---------- Who is signed in ----------

    public static Json me(Bank bank, UserAccount account) {
        String name = "Administrator";

        if (account.getRole() == UserRole.USER) {
            name = account.getUsername();
            try {
                name = bank.findCustomerById(account.getCustomerId()).getName();
            } catch (Exception e) {
                // the login exists but the customer doesn't: keep the username
            }
        }

        return new Json()
                .add("username", account.getUsername())
                .add("role", account.getRole().name())
                .add("name", name)
                .add("customerId", account.getCustomerId())
                .add("mustChangePassword", account.isMustChangePassword());
    }

    // ---------- Credit score ----------

    public static Json score(CreditScore score) {
        List<String> factors = new ArrayList<>();

        for (ScoreFactor factor : score.getFactors()) {
            factors.add(new Json()
                    .add("label", factor.getLabel())
                    .add("points", factor.getPoints())
                    .toString());
        }

        return new Json()
                .add("score", score.getScore())
                .add("band", score.getBand().name())
                .addRaw("factors", Json.array(factors));
    }

    // Premium / Standard, or NONE for a Poor band (who can't borrow at all)
    public static String tierLabel(CreditBand band) {
        if (band == CreditBand.POOR) {
            return "NONE";
        }
        return band.getTier().name();
    }

    // ---------- Customers ----------

    public static Json customer(Bank bank, Customer customer, LocalDate today) {
        CreditScore score = bank.scoreOf(customer, today);
        CreditBand band = score.getBand();

        return new Json()
                .add("customerId", customer.getCustomerId())
                .add("name", customer.getName())
                .add("email", customer.getEmail())
                .add("score", score.getScore())
                .add("band", band.name())
                .add("tier", tierLabel(band))
                .add("activeLoans", customer.getActiveLoans().size())
                .add("maxActiveLoans", band.getMaxActiveLoans())
                .add("maxSingleLoan", band.getMaxSingleLoan())
                .add("hasLogin", bank.findUserForCustomer(customer.getCustomerId()) != null);
    }

    // ---------- Loans ----------

    // What is late on one loan today, split into installment, late fee and interest.
    // The total is zero when nothing is late.
    public static Json owedOnLoan(Loan loan, LocalDate today) {
        BigDecimal installments = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        List<String> lines = new ArrayList<>();

        for (OverdueEntry entry : loan.lateEntries(today)) {
            installments = installments.add(entry.getBaseAmount());
            fees = fees.add(entry.getLateFee());
            interest = interest.add(entry.getLateInterest());
            paid = paid.add(entry.getPaid());
            total = total.add(entry.getOwedToday());
            lines.add(CollectionMessages.line(entry));
        }

        return new Json()
                .add("installments", installments)
                .add("lateFees", fees)
                .add("lateInterest", interest)
                .add("paid", paid)
                .add("total", total)
                .addRaw("lines", Json.stringArray(lines));
    }

    public static Json loan(Loan loan) {
        return new Json()
                .add("loanId", loan.getLoanId())
                .add("customerId", loan.getCustomer().getCustomerId())
                .add("customerName", loan.getCustomer().getName())
                .add("principal", loan.getPrincipal())
                .add("interestPercent", percent(loan.getInterestRate()))
                .add("termMonths", loan.getTermMonths())
                .add("disbursementDate", loan.getDisbursementDate().toString())
                .add("totalRepayable", loan.getTotalRepayable())
                .add("outstanding", loan.getOutstandingBalance())
                .add("lateFees", loan.getTotalLateFees())
                .add("lateInterest", loan.getTotalLateInterest())
                .addRaw("owedToday", owedOnLoan(loan, LocalDate.now()).toString())
                .add("status", loan.getStatus().name())
                .add("installmentCount", loan.getInstallments().size());
    }

    // a loan plus its installments (filtered) and every payment made on it
    public static Json loanDetails(Bank bank, Loan loan, String filter) {

        List<Installment> installments;
        if ("paid".equals(filter)) {
            installments = bank.installmentsByPaidStatus(loan, true);
        } else if ("unpaid".equals(filter)) {
            installments = bank.installmentsByPaidStatus(loan, false);
        } else {
            installments = loan.getInstallments();
        }

        List<String> installmentItems = new ArrayList<>();
        for (Installment installment : installments) {
            installmentItems.add(new Json()
                    .add("dueDate", installment.getDueDate().toString())
                    .add("amountDue", installment.getAmountDue())
                    .add("amountPaid", installment.getAmountPaid())
                    .add("baseAmount", installment.getBaseAmount())
                    .add("lateFee", installment.getLateFee())
                    .add("lateInterest", installment.getLateInterest())
                    .add("status", installment.getStatus().name())
                    .toString());
        }

        List<String> paymentItems = new ArrayList<>();
        for (PaymentRecord payment : bank.paymentsForLoan(loan.getLoanId())) {
            paymentItems.add(new Json()
                    .add("date", payment.getDate().toString())
                    .add("amount", payment.getTotalPaid())
                    .add("prepaymentFee", payment.getPrepaymentFee())
                    .add("recordedBy", payment.getRecordedBy())
                    .add("byAdmin", payment.isRecordedByAdmin())
                    .toString());
        }

        // what it would cost to pay the whole loan off today, with the prepayment charge
        LocalDate today = LocalDate.now();

        return loan(loan)
                .add("payoffFee", loan.payoffFee(today, Bank.PREPAYMENT_FEE_RATE))
                .add("payoffAmount", loan.payoffAmount(today, Bank.PREPAYMENT_FEE_RATE))
                .addRaw("installments", Json.array(installmentItems))
                .addRaw("payments", Json.array(paymentItems));
    }

    // ---------- Applications ----------

    private static String customerName(Bank bank, String customerId) {
        try {
            return bank.findCustomerById(customerId).getName();
        } catch (Exception e) {
            return customerId;
        }
    }

    // What the admin sees: the advice, the reasons, and the score at the time.
    public static Json applicationForAdmin(Bank bank, LoanApplication application) {
        return applicationCommon(bank, application)
                .add("headline", headline(bank, application))
                .add("advice", application.getAdvice().name())
                .addRaw("reasons", Json.stringArray(application.getAdviceReasons()))
                .add("scoreAtSubmission", application.getScoreAtSubmission())
                .add("bandAtSubmission", application.getBandAtSubmission())
                .add("decidedBy", application.getDecidedBy())
                .add("decisionNote", application.getDecisionNote())
                .add("rulesAcceptedOn", application.getRulesAcceptedOn() == null
                        ? "" : application.getRulesAcceptedOn().toString());
    }

    // the one-line summary shown beside each application in the tables (written by the
    // system, not the AI, so a list of applications loads at once)
    private static String headline(Bank bank, LoanApplication application) {
        try {
            return ApplicationInsight.of(bank, application, LocalDate.now()).getHeadline();
        } catch (Exception e) {
            return "";
        }
    }

    // The four-part summary of an application. source says who wrote it:
    // "system" (plain code) or "ai" (the AI reworded the system's facts).
    public static Json summaryParts(ApplicationInsight.Parts parts, String source) {
        return new Json()
                .add("source", source)
                .add("facts", parts.getFacts())
                .add("looksGood", parts.getLooksGood())
                .add("check", parts.getCheck())
                .add("advice", parts.getAdvice());
    }

    // ---------- Early-warning list ----------

    public static Json watchEntry(WatchEntry entry) {
        List<String> signals = new ArrayList<>();
        for (WatchEntry.Reason signal : entry.getSignals()) {
            signals.add(signal.getLabel());
        }

        return new Json()
                .add("customerId", entry.getCustomerId())
                .add("name", entry.getName())
                .add("reason", entry.getReason().name())
                .add("reasonLabel", entry.getReason().getLabel())
                .add("sentence", entry.getSentence())
                .add("nextDue", entry.getNextDue().toString())
                .add("nextAmount", entry.getNextAmount())
                .addRaw("signals", Json.stringArray(signals));
    }

    // What the customer sees: the outcome in plain words, not the advice.
    public static Json applicationForCustomer(Bank bank, LoanApplication application) {
        String message;

        if (application.getStatus() == ApplicationStatus.PENDING) {
            message = "Waiting for approval.";
        } else if (application.getStatus() == ApplicationStatus.APPROVED) {
            message = "Approved. Your loan is " + application.getLoanId() + ".";
        } else {
            message = application.getDecisionNote();
        }

        return applicationCommon(bank, application).add("message", message);
    }

    private static Json applicationCommon(Bank bank, LoanApplication application) {
        return new Json()
                .add("applicationId", application.getApplicationId())
                .add("customerId", application.getCustomerId())
                .add("customerName", customerName(bank, application.getCustomerId()))
                .add("principal", application.getPrincipal())
                .add("interestPercent", percent(application.getInterestRate()))
                .add("termMonths", application.getTermMonths())
                .add("submittedOn", application.getSubmittedOn().toString())
                .add("status", application.getStatus().name())
                .add("decidedOn", application.getDecidedOn() == null ? null : application.getDecidedOn().toString())
                .add("loanId", application.getLoanId());
    }

    // ---------- Finance ----------

    // money earned (interest, late fees and prepayment charges), money lost (loans never repaid), and the profit
    public static Json finance(Bank bank, LocalDate today) {
        FinanceReport report = bank.financeReport(today);

        List<String> monthItems = new ArrayList<>();
        for (MonthlyFinance month : report.getMonths()) {
            monthItems.add(new Json()
                    .add("month", month.getMonth().toString())
                    .add("earned", month.getEarned())
                    .add("badDebt", month.getBadDebt())
                    .add("profit", month.getProfit())
                    .toString());
        }

        return new Json()
                .add("revenue", report.getRevenue())
                .add("interest", report.getInterest())
                .add("lateFees", report.getLateFees())
                .add("lateInterest", report.getLateInterest())
                .add("prepaymentFees", report.getPrepaymentFees())
                .add("badDebt", report.getBadDebt())
                .add("profit", report.getProfit())
                .addRaw("months", Json.array(monthItems));
    }

    // ---------- Late customers ----------

    // the three lines of money, as the customer is shown them
    public static Json owed(CollectionStatus status) {
        return new Json()
                .add("installments", status.getInstallments())
                .add("lateFees", status.getLateFees())
                .add("lateInterest", status.getLateInterest())
                .add("paid", status.getPaid())
                .add("total", status.getTotalOwed())
                .addRaw("lines", Json.stringArray(CollectionMessages.lines(status)));
    }

    // one late customer, for the lender's Overdue page
    public static Json collection(CollectionStatus status) {
        Customer customer = status.getCustomer();
        CollectionCase collectionCase = status.getCollectionCase();

        Json json = new Json()
                .add("customerId", customer.getCustomerId())
                .add("name", customer.getName())
                .add("stage", status.getStage().name())
                .add("stageLabel", status.getStage().getLabel())
                .add("oldestDaysOverdue", status.getOldestDaysOverdue())
                .add("band", status.getBand().name())
                .add("bandLabel", status.getBand().getLabel())
                .add("lateAmounts", status.getLines().size())
                .addRaw("owed", owed(status).toString());

        if (collectionCase != null) {
            json.add("warningSentOn", collectionCase.getWarningSentOn().toString())
                    .add("deadline", collectionCase.getDeadline().toString())
                    .add("escalatedOn", collectionCase.getEscalatedOn().toString());

            if (collectionCase.isReferred()) {
                json.add("referredOn", collectionCase.getReferredOn().toString())
                        .add("referredNote", collectionCase.getReferredNote() == null ? "" : collectionCase.getReferredNote());
            }
        }

        return json;
    }

    // what the customer sees at the top of their pages: the warning, or later the escalation notice.
    // The words use today's figures, so they grow with the interest.
    public static String customerNotice(CollectionStatus status) {
        CollectionCase collectionCase = status.getCollectionCase();

        if (collectionCase == null
                || (status.getStage() != CaseStage.WARNED && status.getStage() != CaseStage.NEEDS_ESCALATION
                        && status.getStage() != CaseStage.REFERRED)) {
            return "null";
        }

        boolean escalated = status.getStage() != CaseStage.WARNED;

        String message = escalated
                ? CollectionMessages.escalation(status, collectionCase.getDeadline())
                : CollectionMessages.warning(status, collectionCase.getDeadline());

        return new Json()
                .add("kind", escalated ? "ESCALATED" : "WARNING")
                .add("title", escalated ? "Your account has been escalated" : "Warning: you have late installments")
                .add("message", message)
                .add("deadline", collectionCase.getDeadline().toString())
                .add("warningSentOn", collectionCase.getWarningSentOn().toString())
                .addRaw("owed", owed(status).toString())
                .toString();
    }

    // ---------- Small helpers ----------

    // 0.2 -> "20", 0.125 -> "12.5"
    // the yearly rate split into 12 months, as a percent: 0.20 gives "1.67"
    public static String monthlyPercent(BigDecimal yearlyRate) {
        return yearlyRate.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    // The worked example shown to customers before they apply: a 100,000 installment that
    // is 75 days late. It uses the same formula as the real charge, so it can never differ.
    public static Json lateExample(BigDecimal yearlyRate) {
        BigDecimal installment = new BigDecimal("100000.00");
        BigDecimal fee = installment.multiply(Bank.LATE_FEE_RATE).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal lateAmount = installment.add(fee);
        BigDecimal interest = Installment.lateInterestFor(lateAmount, yearlyRate, 75);

        return new Json()
                .add("days", 75)
                .add("installment", installment)
                .add("lateFee", fee)
                .add("interest", interest)
                .add("total", lateAmount.add(interest));
    }

    public static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
    }
}
