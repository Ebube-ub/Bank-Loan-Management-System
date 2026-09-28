package src.model;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Loan implements java.io.Serializable {

    private String loanId;
    private Customer customer;
    private double principal;
    private double interestRate;
    private int termMonths;
    private LocalDate disbursementDate;
    private LoanStatus status;
    private List<Installment> installments;

    public Loan(
            String loanId,
            Customer customer,
            double principal,
            double interestRate,
            int termMonths,
            LocalDate disbursementDate
    ) {
        this.loanId = loanId;
        this.customer = customer;
        this.principal = principal;
        this.interestRate = interestRate;
        this.termMonths = termMonths;
        this.disbursementDate = disbursementDate;
        this.status = LoanStatus.ACTIVE;
        this.installments = new ArrayList<>();
    }

    public double getPrincipal() {
    return principal;
}

public double getInterestRate() {
    return interestRate;
}

public int getTermMonths() {
    return termMonths;
}

public LocalDate getDisbursementDate() {
    return disbursementDate;
}

public List<Installment> getInstallments() {
    return installments;
}

public String getLoanId() {
    return loanId;
}

    public Customer getCustomer() {
    return customer;
}

public LoanStatus getStatus() {
    return status;
}

public void setStatus(LoanStatus status) {
    this.status = status;
}

    }