package bank.exception;

// Thrown when someone tries to register a customer ID that is already taken.
public class CustomerAlreadyExistsException extends Exception {

    public CustomerAlreadyExistsException(String message) {
        super(message);
    }
}
