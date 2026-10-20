package bank.model;

import java.math.BigDecimal;
import java.util.List;

// Everything about one customer's late money on one day: the stage they are at,
// each late amount split into installment, late fee and interest, and the totals.
// Worked out fresh each time, never saved.
public class CollectionStatus {

    private final Customer customer;
    private final CaseStage stage;
    private final long oldestDaysOverdue;
    private final List<OverdueEntry> lines;
    private final BigDecimal installments;
    private final BigDecimal lateFees;
    private final BigDecimal lateInterest;
    private final BigDecimal paid;
    private final BigDecimal totalOwed;
    private final CollectionCase collectionCase; // the open case, or null

    public CollectionStatus(Customer customer, CaseStage stage, long oldestDaysOverdue, List<OverdueEntry> lines,
            BigDecimal installments, BigDecimal lateFees, BigDecimal lateInterest, BigDecimal paid,
            BigDecimal totalOwed, CollectionCase collectionCase) {
        this.customer = customer;
        this.stage = stage;
        this.oldestDaysOverdue = oldestDaysOverdue;
        this.lines = lines;
        this.installments = installments;
        this.lateFees = lateFees;
        this.lateInterest = lateInterest;
        this.paid = paid;
        this.totalOwed = totalOwed;
        this.collectionCase = collectionCase;
    }

    public Customer getCustomer() {
        return customer;
    }

    public CaseStage getStage() {
        return stage;
    }

    // days since the oldest overdue installment was due
    public long getOldestDaysOverdue() {
        return oldestDaysOverdue;
    }

    public OverdueBand getBand() {
        return OverdueBand.forDays(oldestDaysOverdue);
    }

    // one line per late installment (plus, for a defaulted loan, the installments that fell due with it)
    public List<OverdueEntry> getLines() {
        return lines;
    }

    // the installments themselves, before any fee or interest
    public BigDecimal getInstallments() {
        return installments;
    }

    public BigDecimal getLateFees() {
        return lateFees;
    }

    public BigDecimal getLateInterest() {
        return lateInterest;
    }

    // what has already been paid towards these installments
    public BigDecimal getPaid() {
        return paid;
    }

    // what it takes to clear everything that is late, today
    public BigDecimal getTotalOwed() {
        return totalOwed;
    }

    public CollectionCase getCollectionCase() {
        return collectionCase;
    }
}
