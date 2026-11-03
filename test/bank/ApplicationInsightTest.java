package bank;

import bank.demo.DemoDataSeeder;
import bank.model.Advice;
import bank.model.ApplicationInsight;
import bank.model.Bank;
import bank.model.Customer;
import bank.model.LoanApplication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The plain summary beside each application: it is written by the system from the
// customer's record, and the AI's reply is only accepted if it keeps the system's advice.
class ApplicationInsightTest {

    private final LocalDate today = LocalDate.now();
    private Bank bank;

    @BeforeEach
    void setUp() {
        bank = new Bank(new BigDecimal("5000000"));
        DemoDataSeeder.seed(bank, today);
    }

    private ApplicationInsight insightFor(String customerId, String amount) throws Exception {
        LoanApplication application = bank.submitApplication(customerId, new BigDecimal(amount), null, 6, today, true);
        return ApplicationInsight.of(bank, application, today);
    }

    // ---------- The system's own summary ----------

    @Test
    void aReliableCustomer_getsAGoodSummaryAndLend() throws Exception {
        ApplicationInsight insight = insightFor("D001", "100000");

        assertEquals(Advice.LEND, insight.getAdvice());
        assertTrue(insight.getHeadline().startsWith("Excellent band, "), insight.getHeadline());
        assertTrue(insight.getHeadline().endsWith("0 late, advice: Lend"), insight.getHeadline());

        assertTrue(insight.getParts().getFacts().contains("Chinedu Okafor asks for ₦100,000 over 6 months"));
        assertTrue(insight.getParts().getLooksGood().contains("paid on time"), insight.getParts().getLooksGood());
        assertTrue(insight.getParts().getLooksGood().contains("fully paid off"));
        assertEquals("Nothing worrying in the record.", insight.getParts().getCheck());
        assertTrue(insight.getParts().getAdvice().startsWith("Lend."));
    }

    @Test
    void aNewCustomer_isToldTheresNoHistory_andGetsReview() throws Exception {
        ApplicationInsight insight = insightFor("D006", "100000");

        assertEquals(Advice.REVIEW, insight.getAdvice());
        assertEquals("Fair band, new customer, advice: Review", insight.getHeadline());
        assertTrue(insight.getParts().getFacts().contains("new customer with no loan history"));
        assertTrue(insight.getParts().getCheck().contains("No loan history yet"));
        assertTrue(insight.getParts().getAdvice().startsWith("Review."));
    }

    @Test
    void aCustomerWithADefault_isDeclinedWithTheReasonsShown() throws Exception {
        ApplicationInsight insight = insightFor("D011", "5000");

        assertEquals(Advice.DECLINE, insight.getAdvice());
        assertTrue(insight.getParts().getCheck().contains("defaulted"), insight.getParts().getCheck());
        assertTrue(insight.getParts().getAdvice().startsWith("Decline."));
        assertTrue(insight.getParts().getAdvice().contains("defaulted loan"));
    }

    @Test
    void lateAndOverduePayments_showUnderCheck() throws Exception {
        // D009 has paid late and has an installment overdue right now
        Customer customer = bank.findCustomerById("D009");
        LoanApplication application = new LoanApplication("A900", "D009", new BigDecimal("5000"),
                new BigDecimal("0.20"), 6, today, Advice.DECLINE,
                java.util.List.of("There is an overdue installment on an active loan. It must be paid first."),
                bank.scoreOf(customer, today).getScore(), "POOR");

        ApplicationInsight insight = ApplicationInsight.of(bank, application, today);

        assertTrue(insight.getParts().getCheck().contains("overdue right now"), insight.getParts().getCheck());
        assertTrue(insight.getParts().getCheck().contains("paid late"), insight.getParts().getCheck());
        assertTrue(insight.getHeadline().contains("overdue now"), insight.getHeadline());
    }

    @Test
    void whatTheAiIsToldMatchesTheSystemsFactsAndAdvice() throws Exception {
        ApplicationInsight insight = insightFor("D006", "100000");
        String prompt = insight.promptFacts();

        assertTrue(prompt.contains("Customer: Folake Adeyemi"), prompt);
        assertTrue(prompt.contains("Asking for: ₦100,000 over 6 months at 20% a year"), prompt);
        assertTrue(prompt.contains("History: new customer"), prompt);
        assertTrue(prompt.contains("must start with the word Review"));
        assertTrue(prompt.contains("Do not use the words Lend or Decline."));
    }

    // ---------- Accepting or refusing the AI's wording ----------

    // the facts the AI would have been given for GOOD_REPLY
    private static final String KNOWN = "Ada asks for ₦100,000 over 6 months. New customer.";

    private static final String GOOD_REPLY = """
            Facts: Ada wants ₦100,000 for 6 months and has no loan history.
            Looks good: Nothing stands out yet.
            Check: There is little to judge by.
            Advice: Review. New customers always need a closer look.
            """;

    @Test
    void aReplyWithTheFourLinesAndTheSameAdvice_isAccepted() {
        Optional<ApplicationInsight.Parts> parts = ApplicationInsight.parseAiReply(GOOD_REPLY, Advice.REVIEW, KNOWN);

        assertTrue(parts.isPresent());
        assertEquals("Ada wants ₦100,000 for 6 months and has no loan history.", parts.get().getFacts());
        assertEquals("Nothing stands out yet.", parts.get().getLooksGood());
        assertEquals("There is little to judge by.", parts.get().getCheck());
        assertTrue(parts.get().getAdvice().startsWith("Review."));
    }

    @Test
    void markdownAndBulletsAreCleanedOff() {
        String reply = "- **Facts:** One.\n- **Looks good:** Two.\n- **Check:** Three.\n- **Advice:** Review it.";

        Optional<ApplicationInsight.Parts> parts = ApplicationInsight.parseAiReply(reply, Advice.REVIEW, KNOWN);

        assertTrue(parts.isPresent());
        assertEquals("One.", parts.get().getFacts());
    }

    @Test
    void aReplyThatGivesADifferentAdvice_isRefused() {
        // the system said REVIEW; the AI says Lend
        String reply = GOOD_REPLY.replace("Advice: Review. New customers always need a closer look.",
                "Advice: Lend. This customer looks fine.");

        assertFalse(ApplicationInsight.parseAiReply(reply, Advice.REVIEW, KNOWN).isPresent());
    }

    @Test
    void aReplyThatMentionsTwoAdviceWords_isRefused() {
        String reply = GOOD_REPLY.replace("Advice: Review. New customers always need a closer look.",
                "Advice: Review, do not decline.");

        assertFalse(ApplicationInsight.parseAiReply(reply, Advice.REVIEW, KNOWN).isPresent());
    }

    @Test
    void aReplyThatLeavesOutTheAdviceWord_isRefused() {
        String reply = GOOD_REPLY.replace("Advice: Review. New customers always need a closer look.",
                "Advice: Take a closer look.");

        assertFalse(ApplicationInsight.parseAiReply(reply, Advice.REVIEW, KNOWN).isPresent());
    }

    @Test
    void aReplyWithALineMissing_isRefused() {
        String reply = GOOD_REPLY.replace("Check: There is little to judge by.\n", "");

        assertFalse(ApplicationInsight.parseAiReply(reply, Advice.REVIEW, KNOWN).isPresent());
    }

    @Test
    void aReplyWithANumberThatWasNotInTheFacts_isRefused() {
        String reply = GOOD_REPLY.replace("Nothing stands out yet.", "Likely to repay within 90 days.");

        assertFalse(ApplicationInsight.parseAiReply(reply, Advice.REVIEW, KNOWN).isPresent());
    }

    @Test
    void aRamblingReply_isRefused() {
        String reply = GOOD_REPLY.replace("Nothing stands out yet.", "word ".repeat(200));

        assertFalse(ApplicationInsight.parseAiReply(reply, Advice.REVIEW, KNOWN).isPresent());
    }
}
