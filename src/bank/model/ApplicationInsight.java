package bank.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

// A short plain summary of one loan application, in four parts: the facts, what
// looks good, what to check, and the system's advice.
//
// Everything here is worked out by plain code from the customer's record. The AI
// is only ever asked to reword it (see promptFacts and parseAiReply), and the
// advice always comes from the system's own rules (Bank.assess), never from the AI.
// If the AI is not available, the page simply shows this version.
public class ApplicationInsight {

    // The four parts of a summary, whether the system or the AI wrote them.
    public static class Parts {
        private final String facts;
        private final String looksGood;
        private final String check;
        private final String advice;

        public Parts(String facts, String looksGood, String check, String advice) {
            this.facts = facts;
            this.looksGood = looksGood;
            this.check = check;
            this.advice = advice;
        }

        public String getFacts() {
            return facts;
        }

        public String getLooksGood() {
            return looksGood;
        }

        public String getCheck() {
            return check;
        }

        public String getAdvice() {
            return advice;
        }
    }

    private static final int MAX_LINE_LENGTH = 400;

    private final Advice advice;
    private final String headline;
    private final Parts parts;
    private final String rawFacts; // the plain list of facts the AI is given (not prose, so it has to write its own wording)

    private ApplicationInsight(Advice advice, String headline, Parts parts,
            String rawFacts) {
        this.advice = advice;
        this.headline = headline;
        this.parts = parts;
        this.rawFacts = rawFacts;
    }

    public static ApplicationInsight of(Bank bank, LoanApplication application, LocalDate today)
            throws bank.exception.CustomerNotFoundException {

        Customer customer = bank.findCustomerById(application.getCustomerId());
        PaymentHistory history = PaymentHistory.of(customer, today);
        CreditScore scoreNow = bank.scoreOf(customer, today);
        CreditBand band = scoreNow.getBand();
        Advice advice = application.getAdvice();

        // ----- the facts -----
        StringBuilder facts = new StringBuilder();
        facts.append(customer.getName()).append(" asks for ").append(Bank.naira(application.getPrincipal()))
                .append(" over ").append(application.getTermMonths()).append(" months at ")
                .append(percent(application.getInterestRate())).append(" a year. ");

        facts.append("The credit score is ").append(scoreNow.getScore()).append(" (")
                .append(label(band)).append(" band)");
        if (scoreNow.getScore() != application.getScoreAtSubmission()) {
            facts.append(", it was ").append(application.getScoreAtSubmission()).append(" when they applied");
        }
        facts.append(". ");

        if (history.hasNoRecord()) {
            facts.append("This is a new customer with no loan history.");
        } else {
            facts.append("So far ").append(plural(history.getOnTime(), "installment")).append(" paid on time, ")
                    .append(history.getLate()).append(" paid late and ")
                    .append(history.getOverdueNow()).append(" overdue now. ");
            facts.append(plural(history.getPaidOffLoans(), "loan")).append(" paid off, ")
                    .append(history.getDefaultedLoans()).append(" defaulted, ")
                    .append(history.getActiveLoans()).append(" active");
            if (history.getActiveLoans() > 0) {
                facts.append(" (").append(history.getPercentOfLimitInUse()).append("% of the ")
                        .append(Bank.naira(Bank.MAX_TOTAL_BORROWED)).append(" borrowing limit in use)");
            }
            facts.append(".");
        }

        // ----- what looks good -----
        List<String> good = new ArrayList<>();
        if (!history.hasNoRecord()) {
            if (history.getOnTime() > 0 && history.getLate() == 0 && history.getOverdueNow() == 0) {
                good.add("Every installment so far (" + history.getOnTime() + ") was paid on time.");
            } else if (history.getOverdueNow() == 0) {
                good.add("Nothing is overdue right now.");
            }
        }
        if (history.getPaidOffLoans() > 0) {
            good.add(plural(history.getPaidOffLoans(), "loan") + " fully paid off.");
        }
        if (history.getActiveLoans() > 0 && history.getPercentOfLimitInUse() < 25) {
            good.add("Only " + history.getPercentOfLimitInUse() + "% of the borrowing limit is in use.");
        }
        if (band == CreditBand.EXCELLENT || band == CreditBand.GOOD) {
            good.add("The score is in the " + label(band) + " band.");
        }

        // ----- what to check -----
        List<String> check = new ArrayList<>();
        if (history.getOverdueNow() > 0) {
            check.add(plural(history.getOverdueNow(), "installment") + " overdue right now.");
        }
        if (history.getDefaultedLoans() > 0) {
            check.add(plural(history.getDefaultedLoans(), "loan") + " defaulted.");
        }
        if (history.getLate() > 0) {
            check.add(plural(history.getLate(), "installment") + " paid late.");
        }
        if (history.getActiveLoans() > 0 && history.getPercentOfLimitInUse() >= 50) {
            check.add(history.getPercentOfLimitInUse() + "% of the borrowing limit is already in use.");
        }
        if (band != CreditBand.POOR && application.getPrincipal().compareTo(band.getMaxSingleLoan()) >= 0) {
            check.add(Bank.naira(application.getPrincipal()) + " is the most the " + label(band)
                    + " band allows for one loan.");
        }
        if (band == CreditBand.POOR) {
            check.add("The score is in the Poor band.");
        }
        if (history.hasNoRecord()) {
            check.add("No loan history yet, so there is little to judge by.");
        }
        if (scoreNow.getScore() < application.getScoreAtSubmission()) {
            check.add("The score has dropped from " + application.getScoreAtSubmission() + " to "
                    + scoreNow.getScore() + " since they applied.");
        }
        if (application.getRulesAcceptedOn() == null) {
            check.add("The day they accepted the rules is not recorded.");
        }

        // ----- the advice -----
        // the reasons that repeat the score or the new-customer fact are already above
        List<String> reasons = new ArrayList<>();
        for (String reason : application.getAdviceReasons()) {
            if (!reason.startsWith("Credit score ") && !reason.startsWith("New customer")) {
                reasons.add(reason);
            }
        }
        String adviceText = label(advice) + ". " + String.join(" ", reasons);

        Parts parts = new Parts(
                facts.toString(),
                good.isEmpty() ? "Nothing stands out yet." : String.join(" ", good),
                check.isEmpty() ? "Nothing worrying in the record." : String.join(" ", check),
                adviceText.trim());

        // ----- the plain list of facts for the AI -----
        // A list, not sentences: if it is handed finished sentences it just copies them.
        StringBuilder raw = new StringBuilder();
        raw.append("Customer: ").append(customer.getName()).append("\n");
        raw.append("Asking for: ").append(Bank.naira(application.getPrincipal())).append(" over ")
                .append(application.getTermMonths()).append(" months at ")
                .append(percent(application.getInterestRate())).append(" a year\n");
        raw.append("Credit score now: ").append(scoreNow.getScore()).append(", band ").append(label(band));
        if (scoreNow.getScore() != application.getScoreAtSubmission()) {
            raw.append(" (it was ").append(application.getScoreAtSubmission()).append(" when they applied)");
        }
        raw.append("\n");
        if (history.hasNoRecord()) {
            raw.append("History: new customer, no loans yet\n");
        } else {
            raw.append("Installments paid on time: ").append(history.getOnTime())
                    .append(", paid late: ").append(history.getLate())
                    .append(", overdue now: ").append(history.getOverdueNow()).append("\n");
            raw.append("Loans paid off: ").append(history.getPaidOffLoans())
                    .append(", defaulted: ").append(history.getDefaultedLoans())
                    .append(", active: ").append(history.getActiveLoans());
            if (history.getActiveLoans() > 0) {
                raw.append(", using ").append(history.getPercentOfLimitInUse()).append("% of the ")
                        .append(Bank.naira(Bank.MAX_TOTAL_BORROWED)).append(" borrowing limit");
            }
            raw.append("\n");
        }
        raw.append("Good points: ").append(good.isEmpty() ? "none" : String.join(" ", good)).append("\n");
        raw.append("Points to check: ").append(check.isEmpty() ? "none" : String.join(" ", check)).append("\n");
        raw.append("System's reasons for its advice: ").append(String.join(" ", application.getAdviceReasons()))
                .append("\n");

        // ----- the one-line headline for the table -----
        String record;
        if (history.hasNoRecord()) {
            record = "new customer";
        } else {
            record = history.getOnTime() + " on time, " + history.getLate() + " late";
            if (history.getOverdueNow() > 0) {
                record += ", " + history.getOverdueNow() + " overdue now";
            }
        }
        String headline = label(band) + " band, " + record + ", advice: " + label(advice);

        return new ApplicationInsight(advice, headline, parts, raw.toString());
    }

    public Advice getAdvice() {
        return advice;
    }

    public String getHeadline() {
        return headline;
    }

    public Parts getParts() {
        return parts;
    }

    // ---------- Asking the AI to reword it ----------

    // Kept short and plain on purpose: long or tangled instructions make some models
    // slow or confused. The advice rule is added to each question (see promptFacts)
    // because it depends on what the system advised.
    public static final String AI_INSTRUCTIONS =
            "You rewrite loan application notes for a loan officer who is not a finance expert. "
            + "Use very simple everyday words and short sentences, as if explaining to a friend. "
            + "No jargon: say \"borrowing limit\", never \"utilization\", \"capacity\", \"placement\" or "
            + "\"red flags\"; say \"paid late\", never \"delinquent\". "
            + "Write it fresh in your own simple words; do not copy the wording of the notes. "
            + "Use only the facts given and never invent anything. Never approve or decline. "
            + "Reply with exactly four lines starting with Facts:, Looks good:, Check:, Advice: "
            + "and nothing else. Max 25 words per line. No markdown.\n"
            + "Example of the style (the facts here are made up):\n"
            + "Facts: Sam wants ₦20,000 for 6 months. Sam's score is 700, which is good. "
            + "Sam has paid 4 installments on time and none late.\n"
            + "Looks good: Sam always pays on time and uses very little of the borrowing limit.\n"
            + "Check: Nothing to worry about.\n"
            + "Advice: [the advice word], then one short reason.";

    // the information sent to the AI: the system's own facts and advice
    public String promptFacts() {
        StringBuilder others = new StringBuilder();
        for (Advice other : Advice.values()) {
            if (other != advice) {
                if (others.length() > 0) {
                    others.append(" or ");
                }
                others.append(label(other));
            }
        }

        return rawFacts
                + "The Advice line must start with the word " + label(advice)
                + ", then a short reason. Do not use the words " + others + ".";
    }

    // Reads the AI's four lines. Returns nothing if any line is missing or too long,
    // if a line contains a number that is not in knownFacts (what the AI was given),
    // or if the Advice line gives a different advice word than the system's:
    // then the page keeps the system's version.
    public static Optional<Parts> parseAiReply(String reply, Advice systemAdvice, String knownFacts) {
        String facts = null;
        String good = null;
        String check = null;
        String adviceLine = null;

        for (String rawLine : reply.split("\\R")) {
            String line = rawLine.replace("**", "").replace("*", "").trim();
            if (line.startsWith("- ")) {
                line = line.substring(2).trim();
            }

            String lower = line.toLowerCase();
            if (lower.startsWith("facts:")) {
                facts = line.substring("facts:".length()).trim();
            } else if (lower.startsWith("looks good:")) {
                good = line.substring("looks good:".length()).trim();
            } else if (lower.startsWith("check:")) {
                check = line.substring("check:".length()).trim();
            } else if (lower.startsWith("advice:")) {
                adviceLine = line.substring("advice:".length()).trim();
            }
        }

        for (String line : new String[] { facts, good, check, adviceLine }) {
            if (line == null || line.isEmpty() || line.length() > MAX_LINE_LENGTH) {
                return Optional.empty();
            }

            // no figure may appear that was not in the facts it was given
            if (!FactCheck.numbersAreKnown(line, knownFacts)) {
                return Optional.empty();
            }
        }

        // the AI may not give a different advice from the system's
        for (Advice other : Advice.values()) {
            boolean mentioned = Pattern.compile("\\b" + other.name() + "\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(adviceLine).find();

            if (other == systemAdvice && !mentioned) {
                return Optional.empty();
            }
            if (other != systemAdvice && mentioned) {
                return Optional.empty();
            }
        }

        return Optional.of(new Parts(facts, good, check, adviceLine));
    }

    // ---------- Small helpers ----------

    // 1 -> "1 loan", 2 -> "2 loans"
    private static String plural(int count, String word) {
        return count + " " + word + (count == 1 ? "" : "s");
    }

    private static String label(Enum<?> value) {
        String name = value.name().toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    // 0.2 -> "20%"
    private static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }
}
