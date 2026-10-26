package bank.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Slows down password guessing: after 5 wrong passwords in a row for the same
// username, that username is locked for one minute.
public class LoginThrottle {

    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MILLIS = 60_000;

    private static class Record {
        int failures;
        long lockedUntil;
    }

    private final Map<String, Record> records = new ConcurrentHashMap<>();

    // throws a 429 error if this username is currently locked
    public void checkAllowed(String username) {
        Record record = records.get(key(username));

        if (record != null && record.lockedUntil > System.currentTimeMillis()) {
            long seconds = (record.lockedUntil - System.currentTimeMillis()) / 1000 + 1;
            throw new ApiException(429,
                    "Too many wrong attempts. Try again in " + seconds + " seconds.");
        }
    }

    public synchronized void recordFailure(String username) {
        Record record = records.computeIfAbsent(key(username), k -> new Record());

        // a lock that has run out starts counting from zero again
        if (record.lockedUntil != 0 && record.lockedUntil <= System.currentTimeMillis()) {
            record.failures = 0;
            record.lockedUntil = 0;
        }

        record.failures++;

        if (record.failures >= MAX_FAILURES) {
            record.lockedUntil = System.currentTimeMillis() + LOCK_MILLIS;
        }
    }

    public void recordSuccess(String username) {
        records.remove(key(username));
    }

    private String key(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase();
    }
}
