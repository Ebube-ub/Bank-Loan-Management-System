package bank.exception;

// Thrown when we look up a customer ID that doesn't exist.
public class CustomerNotFoundException extends Exception {

    public CustomerNotFoundException(String message) {
        super(message);
    }
}
