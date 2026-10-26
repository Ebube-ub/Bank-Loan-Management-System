package bank.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The AI connector: reading its answer, reading the .env file, and the service
// that remembers answers. None of these tests need a key or a network: they use
// a fake AI.
class AiTest {

    // ---------- Reading the answer ----------

    @Test
    void readsTheTextOfTheReply() {
        String json = "{\"id\":\"x\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                + "\"content\":\"9.8 is larger.\"},\"finish_reason\":\"stop\"}]}";

        assertEquals("9.8 is larger.", NvidiaAi.extractReply(json));
    }

    @Test
    void understandsEscapesInTheReply() {
        String json = "{\"choices\":[{\"message\":{\"content\":\"Line one\\nSays \\\"hi\\\" \\u20a6500 C:\\\\temp\"}}]}";

        assertEquals("Line one\nSays \"hi\" \u20a6500 C:\\temp", NvidiaAi.extractReply(json));
    }

    @Test
    void turnsHtmlCodesBackIntoCharacters() {
        String json = "{\"choices\":[{\"message\":{\"content\":\"Since 8 &gt; 1, 9.80 &gt; 9.11 &amp; more\"}}]}";

        assertEquals("Since 8 > 1, 9.80 > 9.11 & more", NvidiaAi.extractReply(json));
    }

    @Test
    void aModelThatRanOutOfRoomWhileThinking_givesNothing() {
        // content is null and only the "thinking" came back
        String json = "{\"choices\":[{\"message\":{\"content\":null,\"role\":\"assistant\","
                + "\"reasoning_content\":\"Here is my thinking\"},\"finish_reason\":\"length\"}]}";

        assertNull(NvidiaAi.extractReply(json));
    }

    @Test
    void doesNotMistakeThinkingForTheAnswer() {
        String json = "{\"choices\":[{\"message\":{\"reasoning_content\":\"thinking...\","
                + "\"content\":\"The answer.\"}}]}";

        assertEquals("The answer.", NvidiaAi.extractReply(json));
    }

    @Test
    void garbageGivesNothing() {
        assertNull(NvidiaAi.extractReply(null));
        assertNull(NvidiaAi.extractReply("not json at all"));
        assertNull(NvidiaAi.extractReply("{\"error\":\"nope\"}"));
    }

    // ---------- The .env file ----------

    @Test
    void readsNameValueLines(@TempDir Path folder) throws Exception {
        Path file = folder.resolve(".env");
        Files.writeString(file, "# a comment\n\nNVIDIA_API_KEY=abc123\nAI_MODEL = \"some/model\"\nnot a setting\n");

        Map<String, String> values = AiConfig.readEnvFile(file);

        assertEquals("abc123", values.get("NVIDIA_API_KEY"));
        assertEquals("some/model", values.get("AI_MODEL"));
        assertEquals(2, values.size());
    }

    @Test
    void aMissingEnvFileJustMeansNoSettings(@TempDir Path folder) {
        assertTrue(AiConfig.readEnvFile(folder.resolve("nothing-here")).isEmpty());
    }

    @Test
    void theConfigNeverPrintsTheKey() {
        AiConfig config = new AiConfig("super-secret-key", "https://example.com", "m", 30);

        assertTrue(config.hasKey());
        assertFalse(config.toString().contains("super-secret-key"));
    }

    @Test
    void withoutAKey_theRealAiSaysItIsNotSetUp() {
        NvidiaAi ai = new NvidiaAi(new AiConfig("", AiConfig.DEFAULT_BASE_URL, AiConfig.DEFAULT_MODEL, 30));

        assertFalse(ai.isConfigured());
        assertThrows(AiUnavailableException.class, () -> ai.ask("system", "user"));
    }

    // ---------- The service that remembers answers ----------

    // a fake AI that counts how often it is asked
    private static class FakeAi implements TextAi {
        final AtomicInteger asked = new AtomicInteger();
        boolean failing = false;

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String ask(String systemPrompt, String userPrompt) throws AiUnavailableException {
            asked.incrementAndGet();
            if (failing) {
                throw new AiUnavailableException("down");
            }
            return "reply to " + userPrompt;
        }
    }

    private final LocalDate today = LocalDate.of(2026, 10, 22);

    @Test
    void theSameQuestionOnTheSameDay_isOnlySentOnce() throws Exception {
        FakeAi fake = new FakeAi();
        AiService service = new AiService(fake, 5);

        assertEquals("reply to q1", service.ask("sys", "q1", today));
        assertEquals("reply to q1", service.ask("sys", "q1", today));
        assertEquals("reply to q2", service.ask("sys", "q2", today));

        assertEquals(2, fake.asked.get());
    }

    @Test
    void aNewDay_asksAgain() throws Exception {
        FakeAi fake = new FakeAi();
        AiService service = new AiService(fake, 5);

        service.ask("sys", "q1", today);
        service.ask("sys", "q1", today.plusDays(1));

        assertEquals(2, fake.asked.get());
    }

    @Test
    void aFailedCall_isNotRemembered() throws Exception {
        FakeAi fake = new FakeAi();
        AiService service = new AiService(fake, 5);

        fake.failing = true;
        assertThrows(AiUnavailableException.class, () -> service.ask("sys", "q1", today));

        fake.failing = false;
        assertEquals("reply to q1", service.ask("sys", "q1", today));
        assertEquals(2, fake.asked.get());
    }

    @Test
    void aSwitchedOffAi_saysSo() {
        AiService service = AiService.disabled();

        assertFalse(service.isConfigured());
        assertThrows(AiUnavailableException.class, () -> service.ask("sys", "q", today));
    }
}
