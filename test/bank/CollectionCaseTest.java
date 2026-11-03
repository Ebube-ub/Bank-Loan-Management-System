package bank;

import bank.model.Bank;
import bank.model.CaseStage;
import bank.model.CollectionCase;
import bank.model.CollectionMessages;
import bank.model.CollectionStatus;
import bank.model.Customer;
import bank.model.Loan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The collections timing: a Send warning button after more than 10 days late, and
// "needs escalation" when 5 days pass after the warning with the customer still late.
class CollectionCaseTest {

    private final BigDecimal rate = new BigDecimal("0.20");
    private Bank bank;
    private Customer customer;
    private Loan loan;
    private LocalDate due1;

    @BeforeEach
    void setUp() throws Exception {
        bank = new Bank(new BigDecimal("5000000"));
        customer = new Customer("C1", "Segun Alade", "c1@example.com");
        bank.registerCustomer(customer);

        // 1,000,000 at 20% for 12 months: 100,000 an installment
        loan = bank.applyForLoan("C1", "L1", new BigDecimal("1000000"), rate, 12, LocalDate.now());
        due1 = loan.getInstallments().get(0).getDueDate();
    }

    private CollectionStatus statusOn(LocalDate day) {
        bank.refreshDelinquencyStatus(day);
        return bank.collectionStatus(customer, day);
    }

    @Test
    void nothingOverdue_meansNoStage() {
        assertEquals(CaseStage.NONE, statusOn(due1).getStage());
    }

    @Test
    void upTo10DaysLate_isOnlyWatched() {
        assertEquals(CaseStage.WATCHING, statusOn(due1.plusDays(1)).getStage());
        assertEquals(CaseStage.WATCHING, statusOn(due1.plusDays(10)).getStage());
    }

    @Test
    void moreThan10DaysLate_showsTheSendWarningButton() {
        CollectionStatus status = statusOn(due1.plusDays(11));

        assertEquals(CaseStage.SEND_WARNING, status.getStage());
        assertEquals(11, status.getOldestDaysOverdue());
    }

    @Test
    void sendingTheWarning_startsAFiveDayClock() throws Exception {
        LocalDate sentDay = due1.plusDays(11);
        bank.refreshDelinquencyStatus(sentDay);

        CollectionCase sent = bank.sendWarning("C1", "admin", sentDay);

        assertEquals(sentDay, sent.getWarningSentOn());
        assertEquals(sentDay.plusDays(5), sent.getDeadline());
        assertEquals("admin", sent.getWarningSentBy());

        // still time to pay, up to and including the deadline
        assertEquals(CaseStage.WARNED, statusOn(sentDay).getStage());
        assertEquals(CaseStage.WARNED, statusOn(sentDay.plusDays(5)).getStage());

        // the day after the deadline, the case needs escalation
        assertEquals(CaseStage.NEEDS_ESCALATION, statusOn(sentDay.plusDays(6)).getStage());
    }

    @Test
    void theClockStartsWhenTheWarningIsSent_notWhenTheButtonAppeared() throws Exception {
        // the lender is slow: the button has been showing since day 11, the warning goes on day 14
        LocalDate sentDay = due1.plusDays(14);
        bank.refreshDelinquencyStatus(sentDay);
        bank.sendWarning("C1", "admin", sentDay);

        // the customer was warned on day 14, so they have until day 19
        assertEquals(CaseStage.WARNED, statusOn(due1.plusDays(19)).getStage());
        assertEquals(CaseStage.NEEDS_ESCALATION, statusOn(due1.plusDays(20)).getStage());
    }

    @Test
    void aWarningCannotBeSentTooEarly_orTwice() throws Exception {
        LocalDate early = due1.plusDays(10);
        bank.refreshDelinquencyStatus(early);
        assertThrows(IllegalArgumentException.class, () -> bank.sendWarning("C1", "admin", early));

        LocalDate day = due1.plusDays(11);
        bank.refreshDelinquencyStatus(day);
        bank.sendWarning("C1", "admin", day);
        assertThrows(IllegalArgumentException.class, () -> bank.sendWarning("C1", "admin", day));
    }

    @Test
    void payingEverythingThatIsLate_endsTheCase_andAnotherLateInstallmentStartsAFreshOne() throws Exception {
        LocalDate day = due1.plusDays(11);
        bank.refreshDelinquencyStatus(day);
        bank.sendWarning("C1", "admin", day);

        // the customer clears the late installment
        BigDecimal due = loan.getInstallments().get(0).getAmountDue();
        bank.makePayment("L1", due, day, "test", false);

        assertEquals(CaseStage.NONE, bank.collectionStatus(customer, day).getStage());
        assertTrue(bank.getCases().get(0).isResolved());

        // the next installment goes more than 10 days late: a new warning is possible
        LocalDate later = loan.getInstallments().get(1).getDueDate().plusDays(11);
        assertEquals(CaseStage.SEND_WARNING, statusOn(later).getStage());
        assertNull(bank.collectionStatus(customer, later).getCollectionCase());
    }

    @Test
    void aPartPayment_doesNotEndTheCase() throws Exception {
        LocalDate day = due1.plusDays(11);
        bank.refreshDelinquencyStatus(day);
        bank.sendWarning("C1", "admin", day);

        bank.makePayment("L1", new BigDecimal("1000"), day, "test", false);

        assertEquals(CaseStage.WARNED, bank.collectionStatus(customer, day).getStage());
        assertFalse(bank.getCases().get(0).isResolved());
    }

    @Test
    void aCaseThatNeedsEscalation_canBePassedToTheLawyer() throws Exception {
        LocalDate sentDay = due1.plusDays(11);
        bank.refreshDelinquencyStatus(sentDay);
        bank.sendWarning("C1", "admin", sentDay);

        // not yet: still inside the 5 days
        LocalDate inside = sentDay.plusDays(3);
        bank.refreshDelinquencyStatus(inside);
        assertThrows(IllegalArgumentException.class, () -> bank.markReferred("C1", "note", "admin", inside));

        LocalDate escalated = sentDay.plusDays(6);
        bank.refreshDelinquencyStatus(escalated);
        bank.markReferred("C1", "Handed to Mr Okoye", "admin", escalated);

        CollectionStatus status = bank.collectionStatus(customer, escalated);
        assertEquals(CaseStage.REFERRED, status.getStage());
        assertEquals(escalated, status.getCollectionCase().getReferredOn());
        assertEquals("Handed to Mr Okoye", status.getCollectionCase().getReferredNote());
    }

    @Test
    void oneWarningCoversEveryLateInstallment() {
        // at 45 days after the first due date, installments 1 and 2 are both late
        CollectionStatus status = statusOn(due1.plusDays(45));

        assertEquals(2, status.getLines().size());
        assertEquals(CaseStage.SEND_WARNING, status.getStage());

        // the total is the three lines added up, less anything paid
        BigDecimal sum = status.getInstallments().add(status.getLateFees()).add(status.getLateInterest())
                .subtract(status.getPaid());
        assertEquals(0, sum.compareTo(status.getTotalOwed()));
    }

    @Test
    void theWarningMessage_namesTheCustomer_theDeadline_andEachLine() {
        LocalDate day = due1.plusDays(45);
        CollectionStatus status = statusOn(day);
        LocalDate deadline = day.plusDays(5);

        String message = CollectionMessages.warning(status, deadline);

        assertTrue(message.startsWith("Dear Segun Alade,"));
        assertTrue(message.contains("You have 2 late installments."));
        assertTrue(message.contains("Please pay by " + CollectionMessages.date(deadline)));
        assertTrue(message.contains("which can include legal action"));
        assertTrue(message.contains("Today you owe " + Bank.naira(status.getTotalOwed())));
        assertTrue(message.contains("Loan L1, due " + CollectionMessages.date(due1)));
        assertTrue(message.contains("installment " + Bank.naira(new BigDecimal("100000.00"))));
        assertTrue(message.contains("late fee " + Bank.naira(new BigDecimal("5000.00"))));
    }

    @Test
    void theEscalationNotice_saysTheDeadlinePassed() {
        LocalDate day = due1.plusDays(30);
        CollectionStatus status = statusOn(day);
        LocalDate deadline = day.minusDays(2);

        String message = CollectionMessages.escalation(status, deadline);

        assertTrue(message.contains("was not paid by " + CollectionMessages.date(deadline)));
        assertTrue(message.contains("has been escalated"));
        assertTrue(message.contains("Today you owe " + Bank.naira(status.getTotalOwed())));
    }

    @Test
    void aDefaultedCustomer_owesEverythingStillUnpaid() {
        CollectionStatus status = statusOn(due1.plusDays(75));

        // the loan defaulted, so all 12 installments count: 3 late, 9 due because of the default
        assertEquals(12, status.getLines().size());
        assertEquals(0, loan.getOutstandingBalance().compareTo(status.getTotalOwed()));
        assertEquals(CaseStage.SEND_WARNING, status.getStage());
    }

    @Test
    void theCounts_followTheStages() throws Exception {
        LocalDate day = due1.plusDays(11);
        bank.refreshDelinquencyStatus(day);

        assertEquals(1, bank.countInStage(CaseStage.SEND_WARNING, day));
        assertEquals(0, bank.countInStage(CaseStage.NEEDS_ESCALATION, day));

        bank.sendWarning("C1", "admin", day);
        assertEquals(0, bank.countInStage(CaseStage.SEND_WARNING, day));

        LocalDate later = day.plusDays(6);
        bank.refreshDelinquencyStatus(later);
        assertEquals(1, bank.countInStage(CaseStage.NEEDS_ESCALATION, later));
    }
}
