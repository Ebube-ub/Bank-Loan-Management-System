package bank.exception;

// Thrown when the bank doesn't have enough money left to give out a loan.
public class InsufficientFundsException extends Exception {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
