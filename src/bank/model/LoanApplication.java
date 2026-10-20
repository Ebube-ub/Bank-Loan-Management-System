package bank.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// A customer's request for a loan. It is not a loan yet: it becomes one only
// when the admin approves it. The system's advice is stored with it, so the
// admin sees the score and reasons as they were when the customer applied.
public class LoanApplication implements Serializable {

    private static final long serialVersionUID = 1L;

    private String applicationId;
    private String customerId;
    private BigDecimal principal;
    private BigDecimal interestRate; // yearly, e.g. 0.20 means 20%
    private int termMonths;
    private LocalDate submittedOn;

    private ApplicationStatus status;
    private Advice advice;
    private List<String> adviceReasons;
    private int scoreAtSubmission;
    private String bandAtSubmission;

    private LocalDate decidedOn;
    private String decidedBy;     // "system" or the admin's username
    private String decisionNote;  // why it was declined, if it was
    private String loanId;        // set once approved

    // the day the customer ticked "I have read and understood the rules" (null if they never did)
    private LocalDate rulesAcceptedOn;

    public LoanApplication(String applicationId, String customerId, BigDecimal principal,
            BigDecimal interestRate, int termMonths, LocalDate submittedOn,
            Advice advice, List<String> adviceReasons, int scoreAtSubmission, String bandAtSubmission) {
        this.applicationId = applicationId;
        this.customerId = customerId;
        this.principal = principal;
        this.interestRate = interestRate;
        this.termMonths = termMonths;
        this.submittedOn = submittedOn;
        this.advice = advice;
        this.adviceReasons = new ArrayList<>(adviceReasons);
        this.scoreAtSubmission = scoreAtSubmission;
        this.bandAtSubmission = bandAtSubmission;
        this.status = ApplicationStatus.PENDING;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public LocalDate getSubmittedOn() {
        return submittedOn;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Advice getAdvice() {
        return advice;
    }

    public List<String> getAdviceReasons() {
        return adviceReasons;
    }

    public int getScoreAtSubmission() {
        return scoreAtSubmission;
    }

    public String getBandAtSubmission() {
        return bandAtSubmission;
    }

    public LocalDate getDecidedOn() {
        return decidedOn;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public LocalDate getRulesAcceptedOn() {
        return rulesAcceptedOn;
    }

    public void acceptRules(LocalDate date) {
        this.rulesAcceptedOn = date;
    }

    public String getLoanId() {
        return loanId;
    }

    public void approve(String loanId, BigDecimal finalInterestRate, String decidedBy, LocalDate date) {
        this.status = ApplicationStatus.APPROVED;
        this.loanId = loanId;
        this.interestRate = finalInterestRate;
        this.decidedBy = decidedBy;
        this.decidedOn = date;
    }

    public void decline(String note, String decidedBy, LocalDate date) {
        this.status = ApplicationStatus.DECLINED;
        this.decisionNote = note;
        this.decidedBy = decidedBy;
        this.decidedOn = date;
    }
}
