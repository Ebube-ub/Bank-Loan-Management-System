package bank.exception;

// Thrown when a customer has too many active loans, or would borrow more
// than the total limit allowed.
public class LoanLimitExceededException extends Exception {

    public LoanLimitExceededException(String message) {
        super(message);
    }
}
