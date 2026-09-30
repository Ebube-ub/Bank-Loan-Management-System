package bank.exception;

// Thrown when a customer tries to get a new loan while they already
// have an overdue installment on one of their current loans.
public class OverdueInstallmentsException extends Exception {

    public OverdueInstallmentsException(String message) {
        super(message);
    }
}
