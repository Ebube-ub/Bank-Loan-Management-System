package bank;

import bank.model.Bank;
import bank.model.Customer;
import bank.model.Installment;
import bank.model.Loan;
import bank.model.WatchEntry;
import bank.model.WatchList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The early-warning list: five plain signals, one main reason per name, and a
// sentence the system writes itself (the AI may only reword it).
//
// "today" is a fixed Thursday so the day names in the sentences can be checked.
class WatchListTest {

    private final LocalDate today = LocalDate.of(2026, 10, 22); // a Thursday
    private final BigDecimal rate = new BigDecimal("0.20");
    private Bank bank;

    @BeforeEach
    void setUp() {
        bank = new Bank(new BigDecimal("5000000"));
    }

    private Customer customer(String id, String name) throws Exception {
        Customer customer = new Customer(id, name, id.toLowerCase() + "@example.com");
        bank.registerCustomer(customer);
        return customer;
    }

    // a loan whose installment number `dueNumber` falls `daysFromToday` days from today
    // (earlier ones are in the past, later ones in the future)
    private Loan loan(String customerId, String loanId, int term, int dueNumber, int daysFromToday) throws Exception {
        LocalDate disbursed = today.plusDays(daysFromToday).minusMonths(dueNumber);
        return bank.applyForLoan(customerId, loanId, new BigDecimal("12000"), rate, term, disbursed);
    }

    private void pay(Loan loan, int count, int... lateOnes) throws Exception {
        for (int i = 0; i < count; i++) {
            Installment installment = loan.getInstallments().get(i);

            LocalDate paymentDate = installment.getDueDate();
            for (int late : lateOnes) {
                if (late == i + 1) {
                    paymentDate = paymentDate.plusDays(5);
                }
            }

            bank.refreshDelinquencyStatus(paymentDate);
            bank.makePayment(loan.getLoanId(), installment.getAmountDue(), paymentDate, "test", false);
        }
    }

    private List<WatchEntry> list() {
        bank.refreshDelinquencyStatus(today);
        return WatchList.build(bank, today);
    }

    // ---------- Who is listed ----------

    @Test
    void anInstallmentDueWithinSevenDays_isEnoughToBeListed() throws Exception {
        customer("C1", "Obinna Chukwu");
        loan("C1", "L1", 6, 1, 3); // the first installment is due in 3 days, nothing paid

        List<WatchEntry> entries = list();

        assertEquals(1, entries.size());
        assertEquals(WatchEntry.Reason.DUE_SOON, entries.get(0).getReason());
        assertEquals(today.plusDays(3), entries.get(0).getNextDue());
        assertTrue(entries.get(0).getSentence().contains("Obinna has an installment of"));
        assertTrue(entries.get(0).getSentence().contains("due on Sunday that is still unpaid"),
                entries.get(0).getSentence());
    }

    @Test
    void theSevenDayLimit_isIncludedButNotEight() throws Exception {
        customer("C1", "Ada Eze");
        customer("C2", "Bayo Ade");
        loan("C1", "L1", 6, 1, 7);
        loan("C2", "L2", 6, 1, 8);

        List<WatchEntry> entries = list();

        assertEquals(1, entries.size());
        assertEquals("C1", entries.get(0).getCustomerId());
    }

    @Test
    void nothingDueSoon_meansNobodyIsListed() throws Exception {
        customer("C1", "Ada Eze");
        loan("C1", "L1", 6, 1, 20);

        assertTrue(list().isEmpty());
    }

    @Test
    void aCustomerWithNoLoans_isNotListed() throws Exception {
        customer("C1", "Ada Eze");

        assertTrue(list().isEmpty());
    }

    @Test
    void anInstallmentAlreadyPaid_isNotListed() throws Exception {
        customer("C1", "Ada Eze");
        Loan loan = loan("C1", "L1", 6, 2, 3);
        pay(loan, 1); // installment 1 (past) paid; installment 2 is due in 3 days and unpaid

        List<WatchEntry> entries = list();
        assertEquals(1, entries.size());

        // now installment 2 is paid too, so nothing is coming up within 7 days
        bank.makePayment("L1", loan.getInstallments().get(1).getAmountDue(), today, "test", false);
        assertTrue(list().isEmpty());
    }

    @Test
    void whenItIsDueWithinAWeek_theSentenceUsesTodayTomorrowOrTheDay() throws Exception {
        customer("C1", "Ada Eze");
        customer("C2", "Bayo Ade");
        customer("C3", "Chi Obi");
        loan("C1", "L1", 6, 1, 0);
        loan("C2", "L2", 6, 1, 1);
        loan("C3", "L3", 6, 1, 6);

        List<WatchEntry> entries = list();
        String all = entries.get(0).getSentence() + entries.get(1).getSentence() + entries.get(2).getSentence();

        assertTrue(all.contains("due today"), all);
        assertTrue(all.contains("due tomorrow"), all);
        assertTrue(all.contains("due on Wednesday"), all); // 22 Oct + 6 days
    }

    // ---------- The signals ----------

    @Test
    void paidLateBefore_becomesTheReason() throws Exception {
        customer("C1", "Ngozi Eze");
        Loan loan = loan("C1", "L1", 6, 2, 3);
        pay(loan, 1, 1); // installment 1 paid 5 days late

        WatchEntry entry = list().get(0);

        assertEquals(WatchEntry.Reason.PAID_LATE_BEFORE, entry.getReason());
        assertTrue(entry.getSentence().contains("Ngozi has paid late before (1 time)"), entry.getSentence());
        assertEquals(2, entry.getSignals().size()); // paid late before + due soon
    }

    @Test
    void usuallyPaysOnTheLastDay_needsThreePaymentsAndTwoThirdsOnTheDueDate() throws Exception {
        customer("C1", "Obinna Chukwu");
        Loan loan = loan("C1", "L1", 6, 4, 3);
        pay(loan, 3); // three installments, all paid on the due date itself

        WatchEntry entry = list().get(0);

        assertEquals(WatchEntry.Reason.PAYS_ON_LAST_DAY, entry.getReason());
        assertTrue(entry.getSentence().contains("Obinna usually pays on the last day (3 of 3 installments)"),
                entry.getSentence());
    }

    @Test
    void twoPaymentsOnTheDueDate_isNotEnoughToCallItAHabit() throws Exception {
        customer("C1", "Obinna Chukwu");
        Loan loan = loan("C1", "L1", 6, 3, 3);
        pay(loan, 2);

        assertEquals(WatchEntry.Reason.DUE_SOON, list().get(0).getReason());
    }

    @Test
    void paymentsMadeEarly_arentCountedAsTheLastDay() throws Exception {
        customer("C1", "Ada Eze");
        Loan loan = loan("C1", "L1", 6, 4, 3);
        for (int i = 0; i < 3; i++) { // each paid 2 days before it was due
            Installment installment = loan.getInstallments().get(i);
            LocalDate early = installment.getDueDate().minusDays(2);
            bank.makePayment("L1", installment.getAmountDue(), early, "test", false);
        }

        assertEquals(WatchEntry.Reason.DUE_SOON, list().get(0).getReason());
    }

    @Test
    void aScoreThatDropped20OrMore_isTheReason() throws Exception {
        Customer customer = customer("C1", "Tunde Bello");
        loan("C1", "L1", 6, 1, 3);
        int scoreNow = bank.scoreOf(customer, today).getScore();

        bank.recordScore("C1", today.minusDays(10), scoreNow + 25);

        WatchEntry entry = list().get(0);

        assertEquals(WatchEntry.Reason.SCORE_DROPPED, entry.getReason());
        assertTrue(entry.getSentence().contains("Tunde's credit score fell from " + (scoreNow + 25)
                + " to " + scoreNow), entry.getSentence());
    }

    @Test
    void aSmallDrop_oraDropOlderThan30Days_isIgnored() throws Exception {
        Customer customer = customer("C1", "Tunde Bello");
        loan("C1", "L1", 6, 1, 3);
        int scoreNow = bank.scoreOf(customer, today).getScore();

        bank.recordScore("C1", today.minusDays(10), scoreNow + 19); // only 19 points
        assertEquals(WatchEntry.Reason.DUE_SOON, list().get(0).getReason());

        bank.recordScore("C1", today.minusDays(10), scoreNow);
        bank.recordScore("C1", today.minusDays(45), scoreNow + 100); // too long ago
        assertEquals(WatchEntry.Reason.DUE_SOON, list().get(0).getReason());
    }

    @Test
    void withNoSavedScores_thereIsNoScoreSignal() throws Exception {
        customer("C1", "Tunde Bello");
        loan("C1", "L1", 6, 1, 3);

        assertEquals(WatchEntry.Reason.DUE_SOON, list().get(0).getReason());
    }

    @Test
    void anotherLoanAlreadyOverdue_listsTheCustomer_evenWithNothingDueSoon() throws Exception {
        customer("C1", "Kelechi Anyanwu");
        loan("C1", "L-NEW", 6, 1, 20);          // next installment in 20 days
        loan("C1", "L-OLD", 6, 1, -5);          // its first installment was due 5 days ago, unpaid

        bank.refreshDelinquencyStatus(today);
        List<WatchEntry> entries = WatchList.build(bank, today);

        WatchEntry entry = entries.get(0);
        assertEquals(WatchEntry.Reason.OVERDUE_ELSEWHERE, entry.getReason());
        assertEquals("Kelechi is already overdue on loan L-OLD, and the next installment on loan L-NEW is due on 11 Nov.",
                entry.getSentence());
    }

    @Test
    void anOverdueLoanAlone_isNotThisLists_job() throws Exception {
        customer("C1", "Kelechi Anyanwu");
        loan("C1", "L-OLD", 6, 1, -5); // overdue, and no other loan with something coming up

        bank.refreshDelinquencyStatus(today);

        assertTrue(WatchList.build(bank, today).isEmpty());
    }

    // ---------- Order and wording ----------

    @Test
    void moreSignals_comesFirst_thenTheNearestDueDate() throws Exception {
        customer("C1", "Ada Eze");
        customer("C2", "Bayo Ade");
        customer("C3", "Chi Obi");

        loan("C1", "L1", 6, 1, 2);                 // one signal, due in 2 days
        Loan b = loan("C2", "L2", 6, 2, 6);        // two signals (paid late + due soon), due in 6 days
        pay(b, 1, 1);
        loan("C3", "L3", 6, 1, 5);                 // one signal, due in 5 days

        List<WatchEntry> entries = list();

        assertEquals("C2", entries.get(0).getCustomerId());
        assertEquals("C1", entries.get(1).getCustomerId());
        assertEquals("C3", entries.get(2).getCustomerId());
    }

    @Test
    void theAiIsGivenTheFactsOnly_oneLinePerCustomer() throws Exception {
        customer("C1", "Obinna Chukwu");
        loan("C1", "L1", 6, 1, 3);

        String lines = WatchList.promptLines(list(), today);

        assertTrue(lines.startsWith("C1 | first name: Obinna | main reason: Installment due within 7 days"), lines);
        assertTrue(lines.contains("due on Sunday"), lines);
        assertFalse(lines.contains("@")); // no email goes to the AI
    }

    @Test
    void theAisSentences_areKeptOnlyForCustomersOnTheList() throws Exception {
        customer("C1", "Obinna Chukwu");
        customer("C2", "Ada Eze");
        loan("C1", "L1", 6, 1, 3);
        loan("C2", "L2", 6, 1, 4);
        List<WatchEntry> entries = list();

        String reply = """
                c1: Obinna's next installment is due on Sunday and nothing has been paid yet.
                C2: **Ada** has an installment coming up on Monday.
                C9: This customer is not on the list.
                C2 without a colon
                """;

        Map<String, String> sentences = WatchList.parseAiReply(reply, entries, today);

        assertEquals(2, sentences.size());
        assertEquals("Obinna's next installment is due on Sunday and nothing has been paid yet.", sentences.get("C1"));
        assertEquals("Ada has an installment coming up on Monday.", sentences.get("C2"));
        assertNull(sentences.get("C9"));
    }

    @Test
    void aSentenceWithAnInventedNumber_orNoName_isIgnored() throws Exception {
        customer("C1", "Obinna Chukwu");
        customer("C2", "Ada Eze");
        loan("C1", "L1", 6, 1, 3);
        loan("C2", "L2", 6, 1, 4);
        List<WatchEntry> entries = list();

        String reply = """
                C1: Obinna (ID 123456789) has an installment due on Sunday.
                C2: Their installment is due on Monday.
                """;

        // the first has an invented number, the second never says who it is about
        assertTrue(WatchList.parseAiReply(reply, entries, today).isEmpty());
    }

    @Test
    void numbersThatAreInTheFacts_areFine() throws Exception {
        customer("C1", "Obinna Chukwu");
        loan("C1", "L1", 6, 1, 3);
        List<WatchEntry> entries = list();
        String amount = Bank.naira(entries.get(0).getNextAmount()); // e.g. ₦2,200

        Map<String, String> sentences = WatchList.parseAiReply(
                "C1: Obinna has " + amount + " due on Sunday and nothing is paid yet.", entries, today);

        assertEquals(1, sentences.size());
    }

    // ---------- Daily scores ----------

    @Test
    void oneScorePerCustomerPerDay_isSaved_andNeverOverwritten() throws Exception {
        Customer customer = customer("C1", "Ada Eze");

        assertTrue(bank.recordScoreSnapshots(today));
        assertFalse(bank.recordScoreSnapshots(today)); // nothing new to save the same day

        // a customer who joins later the same day gets a score too
        customer("C2", "Bayo Ade");
        assertTrue(bank.recordScoreSnapshots(today));

        // the saved score is the one from the day, even if the customer's record changes later
        int saved = bank.oldestScoreBetween("C1", today, today);
        loan("C1", "L1", 6, 1, 3);
        bank.recordScoreSnapshots(today);
        assertEquals(saved, bank.oldestScoreBetween("C1", today, today));
        assertNotEquals(saved, bank.scoreOf(customer, today).getScore()); // the live score did change
    }

    @Test
    void oldestScoreBetween_findsTheFirstSavedDayInTheRange() {
        bank.recordScore("C1", today.minusDays(25), 700);
        bank.recordScore("C1", today.minusDays(10), 650);

        assertEquals(700, bank.oldestScoreBetween("C1", today.minusDays(30), today.minusDays(1)));
        assertEquals(650, bank.oldestScoreBetween("C1", today.minusDays(20), today.minusDays(1)));
        assertNull(bank.oldestScoreBetween("C1", today.minusDays(9), today.minusDays(1)));
        assertNull(bank.oldestScoreBetween("nobody", today.minusDays(30), today.minusDays(1)));
    }
}
