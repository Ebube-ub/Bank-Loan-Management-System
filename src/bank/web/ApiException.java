package bank.web;

// An error that should be sent to the browser with a specific HTTP status,
// e.g. 401 (not signed in), 403 (not allowed) or 429 (too many attempts).
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
