package bank.model;

// Just holds the counts for the "active vs paid off vs defaulted" report.
public class LoanStatusSummary {

    private int activeCount;
    private int paidOffCount;
    private int defaultedCount;

    public LoanStatusSummary(int activeCount, int paidOffCount, int defaultedCount) {
        this.activeCount = activeCount;
        this.paidOffCount = paidOffCount;
        this.defaultedCount = defaultedCount;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public int getPaidOffCount() {
        return paidOffCount;
    }

    public int getDefaultedCount() {
        return defaultedCount;
    }
}
