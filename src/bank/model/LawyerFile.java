package bank.model;

import java.time.LocalDate;

// Everything a lawyer needs about one late customer, as plain text the lender can copy:
// who they are, what they owe and how it adds up, the warning and its deadline, every loan
// with its full schedule and payments, and the day the customer accepted the rules.
// The system never sends this anywhere; the lender decides what to do with it.
public final class LawyerFile {

    private LawyerFile() {
    }

    public static String build(Bank bank, CollectionStatus status, LocalDate today) {
        Customer customer = status.getCustomer();
        CollectionCase collectionCase = status.getCollectionCase();
        CreditScore score = bank.scoreOf(customer, today);

        StringBuilder t = new StringBuilder();

        t.append("LAWYER FILE\n");
        t.append("Prepared on ").append(CollectionMessages.date(today)).append("\n\n");

        t.append("CUSTOMER\n");
        t.append("Name: ").append(customer.getName()).append("\n");
        t.append("Customer ID: ").append(customer.getCustomerId()).append("\n");
        t.append("Email: ").append(customer.getEmail()).append("\n");
        t.append("Credit score today: ").append(score.getScore())
                .append(" (").append(score.getBand().name().toLowerCase()).append(" band)\n\n");

        t.append("WHAT IS OWED TODAY\n");
        t.append("Installments: ").append(Bank.naira(status.getInstallments())).append("\n");
        t.append("Late fees: ").append(Bank.naira(status.getLateFees())).append("\n");
        t.append("Interest on late amounts: ").append(Bank.naira(status.getLateInterest())).append("\n");
        t.append("Paid so far on these: ").append(Bank.naira(status.getPaid())).append("\n");
        t.append("Total owed today: ").append(Bank.naira(status.getTotalOwed())).append("\n");
        t.append("Interest on late amounts is each loan's yearly rate divided by 12, added every month ")
                .append("(a month counts as 30 days), and it grows every day until paid.\n");
        t.append("Late amounts:\n");
        for (String line : CollectionMessages.lines(status)) {
            t.append("- ").append(line).append("\n");
        }
        t.append("\n");

        t.append("WARNING AND ESCALATION\n");
        if (collectionCase == null) {
            t.append("No warning has been sent.\n");
        } else {
            t.append("Warning sent on: ").append(CollectionMessages.date(collectionCase.getWarningSentOn()))
                    .append(" by ").append(collectionCase.getWarningSentBy()).append("\n");
            t.append("Deadline given to the customer: ").append(CollectionMessages.date(collectionCase.getDeadline())).append("\n");
            if (today.isAfter(collectionCase.getDeadline())) {
                t.append("Not paid by the deadline. Escalated on: ")
                        .append(CollectionMessages.date(collectionCase.getEscalatedOn())).append("\n");
            }
            if (collectionCase.isReferred()) {
                t.append("Referred to a lawyer on: ").append(CollectionMessages.date(collectionCase.getReferredOn()));
                if (collectionCase.getReferredNote() != null) {
                    t.append(" (").append(collectionCase.getReferredNote()).append(")");
                }
                t.append("\n");
            }
            t.append("The message the customer was sent:\n\n").append(collectionCase.getWarningMessage()).append("\n");
        }
        t.append("\n");

        t.append("RULES THE CUSTOMER ACCEPTED WHEN APPLYING\n");
        boolean anyApplication = false;
        for (LoanApplication application : bank.applicationsForCustomer(customer.getCustomerId())) {
            anyApplication = true;
            t.append("Application ").append(application.getApplicationId()).append(" for ")
                    .append(Bank.naira(application.getPrincipal())).append(" on ")
                    .append(CollectionMessages.date(application.getSubmittedOn())).append(": ");
            if (application.getRulesAcceptedOn() == null) {
                t.append("acceptance of the rules was not recorded\n");
            } else {
                t.append("rules accepted on ").append(CollectionMessages.date(application.getRulesAcceptedOn())).append("\n");
            }
        }
        if (!anyApplication) {
            t.append("No applications are recorded for this customer.\n");
        }
        t.append("\n");

        t.append("LOANS\n");
        for (Loan loan : customer.getLoans()) {
            t.append("\nLoan ").append(loan.getLoanId()).append(": borrowed ").append(Bank.naira(loan.getPrincipal()))
                    .append(" at ").append(loan.getInterestRate().movePointRight(2).stripTrailingZeros().toPlainString())
                    .append("% a year over ").append(loan.getTermMonths()).append(" months, started ")
                    .append(CollectionMessages.date(loan.getDisbursementDate()))
                    .append(", total to repay ").append(Bank.naira(loan.getTotalRepayable()))
                    .append(", status ").append(loan.getStatus().name()).append("\n");

            int number = 0;
            for (Installment installment : loan.getInstallments()) {
                number++;
                t.append("  ").append(number).append(". due ").append(CollectionMessages.date(installment.getDueDate()))
                        .append(": ").append(Bank.naira(installment.getAmountDue()))
                        .append(" (installment ").append(Bank.naira(installment.getBaseAmount()))
                        .append(" + late fee ").append(Bank.naira(installment.getLateFee()))
                        .append(" + interest ").append(Bank.naira(installment.getLateInterest()))
                        .append("), paid ").append(Bank.naira(installment.getAmountPaid()))
                        .append(", ").append(installment.getStatus().name()).append("\n");
            }

            t.append("  Payments:\n");
            boolean anyPayment = false;
            for (PaymentRecord payment : bank.paymentsForLoan(loan.getLoanId())) {
                anyPayment = true;
                t.append("  - ").append(CollectionMessages.date(payment.getDate())).append(": ")
                        .append(Bank.naira(payment.getTotalPaid())).append("\n");
            }
            if (!anyPayment) {
                t.append("  - none\n");
            }
        }

        return t.toString();
    }
}
