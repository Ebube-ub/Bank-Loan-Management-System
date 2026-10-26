package bank.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// One name on the early-warning list: a customer who may miss their next
// payment, the one main reason, and the sentence that says it in plain words.
// WatchList builds these; the AI may reword the sentence, never the reason.
public class WatchEntry {

    // the signals, strongest first: when several fit, the first one is the main reason
    public enum Reason {
        OVERDUE_ELSEWHERE("Another loan is already overdue"),
        SCORE_DROPPED("Credit score dropped"),
        PAYS_ON_LAST_DAY("Usually pays on the last day"),
        PAID_LATE_BEFORE("Has paid late before"),
        DUE_SOON("Installment due within 7 days");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String customerId;
    private final String name;
    private final Reason reason;
    private final String detail;          // the fact behind the reason, e.g. "paid on the due date 5 of 6 times"
    private final String sentence;        // the plain sentence written by the system
    private final List<Reason> signals;   // every signal that applies, strongest first
    private final LocalDate nextDue;
    private final BigDecimal nextAmount;

    public WatchEntry(String customerId, String name, Reason reason, String detail, String sentence,
            List<Reason> signals, LocalDate nextDue, BigDecimal nextAmount) {
        this.customerId = customerId;
        this.name = name;
        this.reason = reason;
        this.detail = detail;
        this.sentence = sentence;
        this.signals = signals;
        this.nextDue = nextDue;
        this.nextAmount = nextAmount;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public String getFirstName() {
        String[] words = name.trim().split("\\s+");
        return words[0];
    }

    public Reason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public String getSentence() {
        return sentence;
    }

    public List<Reason> getSignals() {
        return signals;
    }

    public LocalDate getNextDue() {
        return nextDue;
    }

    public BigDecimal getNextAmount() {
        return nextAmount;
    }
}
