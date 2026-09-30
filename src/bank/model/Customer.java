package bank.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Customer implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;
    private String name;
    private String email;
    private CreditTier creditTier;

    // every loan this customer has ever taken (active, paid off, or defaulted)
    private List<Loan> loans;

    public Customer(String customerId, String name, String email, CreditTier creditTier) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.creditTier = creditTier;
        this.loans = new ArrayList<>();
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public CreditTier getCreditTier() {
        return creditTier;
    }

    public List<Loan> getLoans() {
        return loans;
    }

    // "active loans" isn't stored separately - we just go through all the
    // loans and pick the ones that are still ACTIVE. This way it can never
    // get out of sync with the real loan status.
    public List<Loan> getActiveLoans() {
        List<Loan> active = new ArrayList<>();

        for (Loan loan : loans) {
            if (loan.getStatus() == LoanStatus.ACTIVE) {
                active.add(loan);
            }
        }

        return active;
    }

    // called by Bank when a new loan gets disbursed to this customer
    public void addLoan(Loan loan) {
        loans.add(loan);
    }
}
