package bank.ai;

// The AI could not give a usable answer (no key, no connection, too slow, an
// error from the service, or a reply we cannot use). The page then keeps the
// summary the system wrote itself, so this is never a crash.
public class AiUnavailableException extends Exception {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
