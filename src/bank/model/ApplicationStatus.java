package bank.model;

// PENDING = waiting for the admin, APPROVED = became a loan, DECLINED = refused
// (either automatically because a rule was broken, or by the admin).
public enum ApplicationStatus {
    PENDING,
    APPROVED,
    DECLINED
}
