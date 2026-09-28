package src.exception;

public class LoanLimitExceededException extends Exception {

    public LoanLimitExceededException(String message) {
        super(message);
    }
}