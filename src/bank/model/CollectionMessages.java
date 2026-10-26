package bank.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// The words of the warning and the escalation notice, with the customer's own
// numbers filled in. Each late amount is split into the same three lines
// (installment, late fee, interest) so the customer can see what they owe.
// Have your lawyer approve this wording; it lives only here.
public final class CollectionMessages {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private CollectionMessages() {
    }

    public static String date(LocalDate date) {
        return date.format(DATE);
    }

    // one line per late installment, for the customer and for the lawyer file
    public static List<String> lines(CollectionStatus status) {
        List<String> result = new ArrayList<>();

        for (OverdueEntry entry : status.getLines()) {
            result.add(line(entry));
        }

        return result;
    }

    // e.g. "Loan D011-A, due 9 Aug 2026, 71 days late: installment ₦1,833.33 + late fee ₦91.67 + interest ₦76.80 = ₦2,001.80"
    public static String line(OverdueEntry entry) {
        String when;
        if (!entry.isDueBecauseDefaulted()) {
            when = "due " + date(entry.getDueDate()) + ", " + entry.getDaysOverdue()
                    + (entry.getDaysOverdue() == 1 ? " day late" : " days late");
        } else {
            when = "due " + date(entry.getDueDate()) + ", due now because the loan defaulted";
        }

        String text = "Loan " + entry.getLoanId() + ", " + when + ": installment "
                + Bank.naira(entry.getBaseAmount())
                + " + late fee " + Bank.naira(entry.getLateFee())
                + " + interest " + Bank.naira(entry.getLateInterest());

        if (entry.getPaid().signum() > 0) {
            text += " - paid so far " + Bank.naira(entry.getPaid());
        }

        return text + " = " + Bank.naira(entry.getOwedToday());
    }

    private static String amounts(CollectionStatus status) {
        StringBuilder text = new StringBuilder();

        text.append("Today you owe ").append(Bank.naira(status.getTotalOwed())).append(" in all:");
        for (String line : lines(status)) {
            text.append("\n- ").append(line);
        }
        text.append("\n\nThe interest grows every day until you pay.");

        return text.toString();
    }

    // sent when the lender clicks Send warning
    public static String warning(CollectionStatus status, LocalDate deadline) {
        int count = status.getLines().size();

        return "Dear " + status.getCustomer().getName() + ",\n\n"
                + "You have " + count + (count == 1 ? " late installment. " : " late installments. ")
                + amounts(status) + "\n\n"
                + "Please pay by " + date(deadline) + ". If it is not paid by then, the lender may take further "
                + "action, which can include legal action. If you have already paid, or need to talk to us, "
                + "please contact the lender.";
    }

    // shown to the customer once the deadline has passed
    public static String escalation(CollectionStatus status, LocalDate deadline) {
        return "Dear " + status.getCustomer().getName() + ",\n\n"
                + "The overdue amount on your loan was not paid by " + date(deadline) + ". Your account has been "
                + "escalated, and the lender may now take further action, which can include legal action. "
                + "You can still stop this by paying the overdue amount now.\n\n"
                + amounts(status);
    }
}
