package bank.model;

import java.io.Serializable;
import java.time.LocalDate;

// The record of one warning sent to a late customer, and what came after it.
// A case starts when the lender clicks Send warning, and ends (is "resolved") when
// the customer has paid everything that was overdue.
public class CollectionCase implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;
    private LocalDate warningSentOn;
    private String warningSentBy;
    private String warningMessage;   // the words that were sent
    private LocalDate deadline;      // the last day the customer has to pay
    private LocalDate referredOn;    // set when the lender passes it to a lawyer
    private String referredBy;
    private String referredNote;
    private LocalDate resolvedOn;    // set when nothing is overdue any more

    public CollectionCase(String customerId, LocalDate warningSentOn, String warningSentBy,
            String warningMessage, LocalDate deadline) {
        this.customerId = customerId;
        this.warningSentOn = warningSentOn;
        this.warningSentBy = warningSentBy;
        this.warningMessage = warningMessage;
        this.deadline = deadline;
    }

    public String getCustomerId() {
        return customerId;
    }

    public LocalDate getWarningSentOn() {
        return warningSentOn;
    }

    public String getWarningSentBy() {
        return warningSentBy;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    // the first day the case counts as needing escalation: the day after the deadline
    public LocalDate getEscalatedOn() {
        return deadline.plusDays(1);
    }

    public LocalDate getReferredOn() {
        return referredOn;
    }

    public String getReferredBy() {
        return referredBy;
    }

    public String getReferredNote() {
        return referredNote;
    }

    public boolean isReferred() {
        return referredOn != null;
    }

    public void refer(LocalDate date, String by, String note) {
        this.referredOn = date;
        this.referredBy = by;
        this.referredNote = note;
    }

    public LocalDate getResolvedOn() {
        return resolvedOn;
    }

    public boolean isResolved() {
        return resolvedOn != null;
    }

    public void resolve(LocalDate date) {
        this.resolvedOn = date;
    }
}
