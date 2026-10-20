package bank.web;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import bank.model.UserAccount;
import bank.model.UserRole;

// Remembers who is signed in. When someone logs in they get a long random
// token, kept in a cookie in their browser. On every request the browser sends
// the token back, and we look it up here to know who is asking.
//
// Sessions live only in memory, so restarting the server signs everyone out.
public class SessionStore {

    // someone who does nothing for this long is signed out
    private static final long IDLE_LIMIT_MILLIS = 8L * 60 * 60 * 1000;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static class Session {
        private final String token;
        private final String username;
        private final UserRole role;
        private final String customerId;
        private volatile long lastUsed;

        Session(String token, UserAccount account) {
            this.token = token;
            this.username = account.getUsername();
            this.role = account.getRole();
            this.customerId = account.getCustomerId();
            this.lastUsed = System.currentTimeMillis();
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }

        public UserRole getRole() {
            return role;
        }

        // the customer this login belongs to (null for the admin)
        public String getCustomerId() {
            return customerId;
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session create(UserAccount account) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);

        Session session = new Session(token, account);
        sessions.put(token, session);
        return session;
    }

    // returns the session for this token, or null if it is unknown or has expired
    public Session find(String token) {
        if (token == null) {
            return null;
        }

        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - session.lastUsed > IDLE_LIMIT_MILLIS) {
            sessions.remove(token);
            return null;
        }

        session.lastUsed = now;
        return session;
    }

    public void remove(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
