package bank.model;

// What the system recommends for a loan application.
// LEND = recommend approving, REVIEW = look closer before deciding,
// DECLINE = a rule is broken so the application is refused.
// Only the admin actually approves a loan - this is just advice.
public enum Advice {
    LEND,
    REVIEW,
    DECLINE
}
