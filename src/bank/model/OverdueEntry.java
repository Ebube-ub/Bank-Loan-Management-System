package bank.model;

import java.time.LocalDate;

// Just holds one row of the "currently overdue loans" report so Main can print it.
public class OverdueEntry {

    private String customerName;
    private String loanId;
    private LocalDate dueDate;
    private long daysOverdue;

    public OverdueEntry(String customerName, String loanId, LocalDate dueDate, long daysOverdue) {
        this.customerName = customerName;
        this.loanId = loanId;
        this.dueDate = dueDate;
        this.daysOverdue = daysOverdue;
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
}
