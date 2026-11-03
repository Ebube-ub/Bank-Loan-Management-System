package bank.model;

// The two kinds of login.
// USER = a customer (applies for loans, makes payments on their own loans)
// ADMIN = the lender (views everything, approves loans, sees the finances)
public enum UserRole {
    USER,
    ADMIN
}
