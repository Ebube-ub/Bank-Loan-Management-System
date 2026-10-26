package bank;

import bank.demo.DemoDataSeeder;
import bank.model.Bank;
import bank.model.WatchEntry;
import bank.model.WatchList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The demo customers D012 to D016 exist so the Dashboard's watch list has something to
// show. This checks that each one lands on the list for the reason it was made for.
class DemoWatchListTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    private static Bank bank;
    private static Map<String, WatchEntry> listed = new HashMap<>();

    @BeforeAll
    static void seed() {
        bank = new Bank(new BigDecimal("5000000"));
        DemoDataSeeder.seed(bank, TODAY);
        assertEquals(5, DemoDataSeeder.seedWatchListExamples(bank, TODAY));

        bank.refreshDelinquencyStatus(TODAY);
        for (WatchEntry entry : WatchList.build(bank, TODAY)) {
            listed.put(entry.getCustomerId(), entry);
        }
    }

    @Test
    void exactlyTheFiveExamplesAreListed_andNoneOfTheOriginalElevenCustomers() {
        assertEquals(List.of("D012", "D013", "D014", "D015", "D016"),
                listed.keySet().stream().sorted().toList());
    }

    @Test
    void d012_paysOnTheLastDay() {
        assertEquals(WatchEntry.Reason.PAYS_ON_LAST_DAY, listed.get("D012").getReason());
        assertTrue(listed.get("D012").getSentence().contains("usually pays on the last day (3 of 3 installments)"));
        assertEquals(TODAY.plusDays(2), listed.get("D012").getNextDue());
    }

    @Test
    void d013_hasPaidLateBefore() {
        assertEquals(WatchEntry.Reason.PAID_LATE_BEFORE, listed.get("D013").getReason());
        assertEquals(TODAY.plusDays(3), listed.get("D013").getNextDue());
    }

    @Test
    void d014_isJustDueSoon() {
        assertEquals(WatchEntry.Reason.DUE_SOON, listed.get("D014").getReason());
        assertEquals(1, listed.get("D014").getSignals().size());
        assertEquals(TODAY.plusDays(5), listed.get("D014").getNextDue());
    }

    @Test
    void d015_hasAnotherLoanOverdue() {
        WatchEntry entry = listed.get("D015");

        assertEquals(WatchEntry.Reason.OVERDUE_ELSEWHERE, entry.getReason());
        assertTrue(entry.getSentence().contains("already overdue on loan D015-A"), entry.getSentence());
        assertTrue(entry.getSentence().contains("loan D015-B"), entry.getSentence());
        assertEquals(TODAY.plusDays(12), entry.getNextDue());
    }

    @Test
    void d016_scoreDropped() {
        assertEquals(WatchEntry.Reason.SCORE_DROPPED, listed.get("D016").getReason());
        assertTrue(listed.get("D016").getSentence().contains("fell from"), listed.get("D016").getSentence());
    }

    @Test
    void addingTheExamplesTwice_orWithoutTheDemoCustomers_doesNothing() {
        assertEquals(0, DemoDataSeeder.seedWatchListExamples(bank, TODAY)); // already there

        Bank empty = new Bank(new BigDecimal("5000000"));
        assertEquals(0, DemoDataSeeder.seedWatchListExamples(empty, TODAY)); // no demo customers
        assertFalse(DemoDataSeeder.watchListExamplesLoaded(empty));
    }
}
