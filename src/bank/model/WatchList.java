package bank.model;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// The early-warning list: customers who may miss their next payment.
//
// There is no trained model here. With only a few customers a model would be
// guessing, so the list uses five plain signals that anyone can check by hand:
//
//   1. paid late before                    at least one installment paid after its due date
//   2. usually pays on the last day        at least 3 paid, and two thirds of them on the due date itself
//   3. an installment due within 7 days    still unpaid
//   4. the credit score dropped            20 or more points below the score saved about 30 days ago
//   5. another loan is already overdue     and this customer has another active loan with an installment coming
//
// A customer is listed when signal 3 or signal 5 is true. The other signals add
// to the reason. When several fit, the strongest is the one reason shown
// (5, then 4, then 2, then 1, then 3). The list is sorted by how many signals
// fit, then by the nearest due date.
public class WatchList {

    public static final int DUE_SOON_DAYS = 7;
    public static final int LAST_DAY_MIN_PAID = 3;
    public static final int SCORE_DROP_POINTS = 20;
    public static final int SCORE_DROP_DAYS = 30;

    public static List<WatchEntry> build(Bank bank, LocalDate today) {
        List<WatchEntry> entries = new ArrayList<>();

        for (Customer customer : bank.getAllCustomers()) {
            Optional<WatchEntry> entry = check(bank, customer, today);
            entry.ifPresent(entries::add);
        }

        entries.sort(Comparator
                .comparingInt((WatchEntry e) -> e.getSignals().size()).reversed()
                .thenComparing(WatchEntry::getNextDue)
                .thenComparing(WatchEntry::getCustomerId));
        return entries;
    }

    static Optional<WatchEntry> check(Bank bank, Customer customer, LocalDate today) {

        PaymentHistory history = PaymentHistory.of(customer, today);

        // ----- signal 3: an unpaid installment due within 7 days -----
        Installment dueSoon = null;
        for (Loan loan : customer.getActiveLoans()) {
            Installment next = nextUnpaid(loan, today);
            if (next != null && !next.getDueDate().isAfter(today.plusDays(DUE_SOON_DAYS))
                    && (dueSoon == null || next.getDueDate().isBefore(dueSoon.getDueDate()))) {
                dueSoon = next;
            }
        }

        // ----- signal 5: another loan is already overdue -----
        Loan overdueLoan = null;
        Installment otherNext = null;
        Loan otherLoan = null;

        for (Loan loan : customer.getActiveLoans()) {
            if (overdueLoan == null && hasOverdue(loan, today)) {
                overdueLoan = loan;
            }
        }
        if (overdueLoan != null) {
            for (Loan loan : customer.getActiveLoans()) {
                if (loan == overdueLoan) {
                    continue;
                }
                Installment next = nextUnpaid(loan, today);
                if (next != null && (otherNext == null || next.getDueDate().isBefore(otherNext.getDueDate()))) {
                    otherNext = next;
                    otherLoan = loan;
                }
            }
        }
        boolean overdueElsewhere = overdueLoan != null && otherNext != null;

        if (dueSoon == null && !overdueElsewhere) {
            return Optional.empty();
        }

        // ----- signal 4: the score dropped -----
        int scoreNow = bank.scoreOf(customer, today).getScore();
        Integer scoreBefore = bank.oldestScoreBetween(customer.getCustomerId(),
                today.minusDays(SCORE_DROP_DAYS), today.minusDays(1));
        boolean scoreDropped = scoreBefore != null && scoreBefore - scoreNow >= SCORE_DROP_POINTS;

        // ----- signal 2: usually pays on the last day -----
        boolean lastDay = history.getPaidCount() >= LAST_DAY_MIN_PAID
                && history.getPaidOnDueDate() * 3 >= history.getPaidCount() * 2;

        // ----- signal 1: paid late before -----
        boolean paidLate = history.getLate() > 0;

        // which installment the sentence talks about
        Installment next = dueSoon != null ? dueSoon : otherNext;

        List<WatchEntry.Reason> signals = new ArrayList<>();
        if (overdueElsewhere) {
            signals.add(WatchEntry.Reason.OVERDUE_ELSEWHERE);
        }
        if (scoreDropped) {
            signals.add(WatchEntry.Reason.SCORE_DROPPED);
        }
        if (lastDay) {
            signals.add(WatchEntry.Reason.PAYS_ON_LAST_DAY);
        }
        if (paidLate) {
            signals.add(WatchEntry.Reason.PAID_LATE_BEFORE);
        }
        if (dueSoon != null) {
            signals.add(WatchEntry.Reason.DUE_SOON);
        }

        WatchEntry.Reason reason = signals.get(0);
        String first = firstName(customer);
        String when = dueWords(next.getDueDate(), today);

        String detail;
        String sentence;

        switch (reason) {
            case OVERDUE_ELSEWHERE -> {
                detail = "loan " + overdueLoan.getLoanId() + " is overdue; next installment on "
                        + otherLoan.getLoanId();
                sentence = first + " is already overdue on loan " + overdueLoan.getLoanId()
                        + ", and the next installment on loan " + otherLoan.getLoanId() + " is due " + when + ".";
            }
            case SCORE_DROPPED -> {
                detail = "score fell from " + scoreBefore + " to " + scoreNow + " in the last "
                        + SCORE_DROP_DAYS + " days";
                sentence = first + "'s credit score fell from " + scoreBefore + " to " + scoreNow
                        + " in the last " + SCORE_DROP_DAYS + " days, and the next installment is due " + when + ".";
            }
            case PAYS_ON_LAST_DAY -> {
                detail = "paid on the due date " + history.getPaidOnDueDate() + " of "
                        + history.getPaidCount() + " times";
                sentence = first + " usually pays on the last day (" + history.getPaidOnDueDate() + " of "
                        + history.getPaidCount() + " installments), and the next installment is due " + when + ".";
            }
            case PAID_LATE_BEFORE -> {
                detail = history.getLate() + (history.getLate() == 1 ? " installment" : " installments")
                        + " paid late so far";
                sentence = first + " has paid late before (" + history.getLate()
                        + " " + (history.getLate() == 1 ? "time" : "times") + "), and the next installment is due " + when + ".";
            }
            default -> {
                detail = "nothing paid yet on an installment that is due soon";
                sentence = first + " has an installment of " + Bank.naira(next.getRemainingAmount())
                        + " due " + when + " that is still unpaid.";
            }
        }

        // the other signals that fit, so the AI can mention them without inventing anything
        StringBuilder also = new StringBuilder();
        for (int i = 1; i < signals.size(); i++) {
            if (also.length() > 0) {
                also.append("; ");
            }
            also.append(signals.get(i).getLabel());
        }
        if (also.length() > 0) {
            detail += " (also: " + also + ")";
        }

        return Optional.of(new WatchEntry(customer.getCustomerId(), customer.getName(), reason, detail,
                sentence, signals, next.getDueDate(), next.getRemainingAmount()));
    }

    // ---------- Asking the AI to reword the sentences ----------

    public static final String AI_INSTRUCTIONS =
            "You help a loan officer see which customers may miss their next payment. "
            + "You get one line per customer. Write ONE plain sentence for each, in this pattern: "
            + "the customer's first name, the main reason, then when the next installment is due. "
            + "Use only the facts in that line and never invent anything. "
            + "Do not use he, she, his or her. Do not write the ID in the sentence. "
            + "Reply with one line per customer, in the same order, in the form ID: sentence, "
            + "using the ID exactly as given. Nothing else, no markdown.\n"
            + "Example input:\n"
            + "X1 | first name: Sam | main reason: Usually pays on the last day (paid on the due date 5 of 6 times)"
            + " | next installment: \u20a610,000 due on Friday\n"
            + "X2 | first name: Jo | main reason: Another loan is already overdue (loan L7 is overdue)"
            + " | next installment: \u20a64,000 due on 3 Nov\n"
            + "Example output:\n"
            + "X1: Sam usually pays on the last day, and the next installment is due on Friday.\n"
            + "X2: Jo already has another loan overdue, and the next installment is due on 3 Nov.";

    // one line per customer for the AI: the system's reason and facts
    public static String promptLines(List<WatchEntry> entries, LocalDate today) {
        StringBuilder text = new StringBuilder();
        for (WatchEntry entry : entries) {
            text.append(promptLine(entry, today)).append("\n");
        }
        return text.toString();
    }

    private static String promptLine(WatchEntry entry, LocalDate today) {
        return entry.getCustomerId() + " | first name: " + entry.getFirstName()
                + " | main reason: " + entry.getReason().getLabel()
                + " (" + entry.getDetail() + ")"
                + " | next installment: " + Bank.naira(entry.getNextAmount())
                + " due " + dueWords(entry.getNextDue(), today);
    }

    // Reads the AI's "ID: sentence" lines. A sentence is kept only if its ID is really on
    // the list, it is not empty or far too long, it names the customer, and every number
    // in it is one of the facts it was given. Anything else is ignored, so the system's
    // own sentence stays for that customer.
    public static Map<String, String> parseAiReply(String reply, List<WatchEntry> entries, LocalDate today) {
        Map<String, String> sentences = new HashMap<>();

        for (String rawLine : reply.split("\\R")) {
            String line = rawLine.replace("**", "").trim();
            if (line.startsWith("- ")) {
                line = line.substring(2).trim();
            }

            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }

            String id = line.substring(0, colon).trim();
            String sentence = line.substring(colon + 1).trim();

            WatchEntry entry = null;
            for (WatchEntry candidate : entries) {
                if (candidate.getCustomerId().equalsIgnoreCase(id)) {
                    entry = candidate;
                }
            }

            if (entry == null || sentence.isEmpty() || sentence.length() > 300) {
                continue;
            }

            boolean namesTheCustomer = sentence.toLowerCase().contains(entry.getFirstName().toLowerCase());
            boolean onlyKnownNumbers = FactCheck.numbersAreKnown(sentence, promptLine(entry, today));

            // it must still say when the installment is due ("Friday", "3 Nov", "tomorrow"...)
            String due = dueWords(entry.getNextDue(), today);
            boolean saysWhenDue = sentence.toLowerCase().contains(due.replaceFirst("^on ", "").toLowerCase());

            if (namesTheCustomer && onlyKnownNumbers && saysWhenDue) {
                sentences.put(entry.getCustomerId(), sentence);
            }
        }
        return sentences;
    }

    // ---------- Small helpers ----------

    // the earliest installment on this loan that is unpaid and not late yet (null if none)
    private static Installment nextUnpaid(Loan loan, LocalDate today) {
        for (Installment installment : loan.getInstallments()) {
            if (installment.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0
                    && !installment.getDueDate().isBefore(today)) {
                return installment;
            }
        }
        return null;
    }

    private static boolean hasOverdue(Loan loan, LocalDate today) {
        for (Installment installment : loan.getInstallments()) {
            if (!installment.isPaid() && (installment.getStatus() == InstallmentStatus.OVERDUE
                    || installment.getDueDate().isBefore(today))) {
                return true;
            }
        }
        return false;
    }

    private static String firstName(Customer customer) {
        return customer.getName().trim().split("\\s+")[0];
    }

    // "today", "tomorrow", "on Friday" (within a week) or "on 29 Oct"
    static String dueWords(LocalDate due, LocalDate today) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, due);

        if (days <= 0) {
            return "today";
        }
        if (days == 1) {
            return "tomorrow";
        }
        if (days <= 6) {
            DayOfWeek day = due.getDayOfWeek();
            return "on " + day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        }
        return "on " + due.getDayOfMonth() + " "
                + due.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }
}
