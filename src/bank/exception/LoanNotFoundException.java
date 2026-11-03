package bank.exception;

// Thrown when we look up a loan ID that doesn't exist.
public class LoanNotFoundException extends Exception {

    public LoanNotFoundException(String message) {
        super(message);
    }
}
