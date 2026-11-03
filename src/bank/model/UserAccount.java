package bank.model;

import java.io.Serializable;

// A login. The password is never stored - only a hash of it (see PasswordHasher).
// A USER account belongs to one customer; an ADMIN account belongs to nobody.
public class UserAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String passwordHash;
    private UserRole role;
    private String customerId;
    private boolean mustChangePassword;

    public UserAccount(String username, String passwordHash, UserRole role,
            String customerId, boolean mustChangePassword) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.customerId = customerId;
        this.mustChangePassword = mustChangePassword;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public String getCustomerId() {
        return customerId;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setPassword(String newHash, boolean mustChange) {
        this.passwordHash = newHash;
        this.mustChangePassword = mustChange;
    }
}
