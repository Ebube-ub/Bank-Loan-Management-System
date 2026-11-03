package bank.model;

// The 3 states a loan can be in.
// ACTIVE = still being paid off, PAID_OFF = fully paid, DEFAULTED = missed too many payments.
public enum LoanStatus {
    ACTIVE,
    PAID_OFF,
    DEFAULTED
}
