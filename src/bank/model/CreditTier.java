package bank.model;

// A customer can be STANDARD or PREMIUM.
// PREMIUM customers are allowed to have more loans open at once (checked in Bank.java).
public enum CreditTier {
    STANDARD,
    PREMIUM
}
