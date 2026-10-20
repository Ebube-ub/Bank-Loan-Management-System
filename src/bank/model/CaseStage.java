package bank.model;

// Where a late customer is in the collections process.
//
//   NONE              nothing is overdue
//   WATCHING          late, but not yet more than 10 days (only the colour shows)
//   SEND_WARNING      more than 10 days late and no warning sent yet: the Send warning button shows
//   WARNED            the warning was sent and the customer still has time to pay
//   NEEDS_ESCALATION  5 days passed since the warning and the customer still has not paid
//   REFERRED          the lender has passed it to a lawyer
public enum CaseStage {

    NONE("Nothing overdue"),
    WATCHING("Watching"),
    SEND_WARNING("Send warning"),
    WARNED("Warned"),
    NEEDS_ESCALATION("Needs escalation"),
    REFERRED("Referred to lawyer");

    private final String label;

    CaseStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
