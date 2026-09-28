package src.model;
import java.util.ArrayList;
import java.util.List;

public class Customer implements java.io.Serializable {

    private String customerId;
    private String name;
    private String email;
    private CreditTier creditTier;
    private List<Loan> activeLoans;

    public Customer(String customerId, String name, String email, CreditTier creditTier) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.creditTier = creditTier;
        this.activeLoans = new ArrayList<>();
    }

    public String getCustomerId() {
    return customerId;
    }

    public CreditTier getCreditTier() {
    return creditTier;
}

public List<Loan> getActiveLoans() {
    return activeLoans;
    }
}