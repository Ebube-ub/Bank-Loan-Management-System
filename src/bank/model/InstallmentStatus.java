package bank.model;

// PENDING = not due yet (or due but not overdue-checked), PAID = fully paid,
// OVERDUE = due date has passed and it's still not fully paid.
public enum InstallmentStatus {
    PENDING,
    PAID,
    OVERDUE
}
