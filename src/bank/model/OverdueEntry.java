package bank.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// Just holds one row of the "currently overdue" report: one late installment,
// with what it is made of and what is owed on it today.
public class OverdueEntry {

    private String customerId;
    private String customerName;
    private String loanId;
    private LocalDate dueDate;
    private long daysOverdue;
    private BigDecimal baseAmount;
    private BigDecimal lateFee;
    private BigDecimal lateInterest;
    private BigDecimal owedToday;
    private BigDecimal paid;
    private boolean dueBecauseDefaulted;

    public OverdueEntry(String customerId, String customerName, String loanId, LocalDate dueDate, long daysOverdue,
            BigDecimal baseAmount, BigDecimal lateFee, BigDecimal lateInterest, BigDecimal paid,
            BigDecimal owedToday, boolean dueBecauseDefaulted) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.loanId = loanId;
        this.dueDate = dueDate;
        this.daysOverdue = daysOverdue;
        this.baseAmount = baseAmount;
        this.lateFee = lateFee;
        this.lateInterest = lateInterest;
        this.owedToday = owedToday;
        this.paid = paid;
        this.dueBecauseDefaulted = dueBecauseDefaulted;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getLoanId() {
        return loanId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public long getDaysOverdue() {
        return daysOverdue;
    }

    public OverdueBand getBand() {
        return OverdueBand.forDays(daysOverdue);
    }

    // the installment before any late fee or interest was added
    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public BigDecimal getLateFee() {
        return lateFee;
    }

    public BigDecimal getLateInterest() {
        return lateInterest;
    }

    // true for an installment that is not late itself, but is due now because its loan defaulted
    public boolean isDueBecauseDefaulted() {
        return dueBecauseDefaulted;
    }

    // what has already been paid towards it
    public BigDecimal getPaid() {
        return paid;
    }

    // what is still unpaid on it today: installment, fee and interest, less any part payment
    public BigDecimal getOwedToday() {
        return owedToday;
    }
}
