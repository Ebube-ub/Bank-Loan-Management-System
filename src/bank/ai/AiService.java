package bank.ai;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// The AI as the rest of the app sees it: the same question asked again on the
// same day gets the answer already received (nothing is sent twice), and two
// people asking at the same moment share one call instead of making two.
//
// A failed call is forgotten straight away, so asking again tries again.
// The answers live in memory only and start fresh each day.
public class AiService {

    private static final int MAX_REMEMBERED = 300;

    private final TextAi ai;
    private final long waitSeconds;
    private final Map<String, CompletableFuture<String>> calls = new ConcurrentHashMap<>();
    private LocalDate day;

    public AiService(TextAi ai, long waitSeconds) {
        this.ai = ai;
        this.waitSeconds = waitSeconds;
    }

    // an AI that is switched off: every question says "unavailable"
    public static AiService disabled() {
        return new AiService(new TextAi() {
            @Override
            public boolean isConfigured() {
                return false;
            }

            @Override
            public String ask(String systemPrompt, String userPrompt) throws AiUnavailableException {
                throw new AiUnavailableException("The AI is not set up. Add NVIDIA_API_KEY to the .env file.");
            }
        }, 1);
    }

    // the real AI, set up from the environment and the .env file
    public static AiService fromEnvironment() {
        AiConfig config = AiConfig.load();
        return new AiService(new NvidiaAi(config), config.getTimeoutSeconds() + 10L);
    }

    public boolean isConfigured() {
        return ai.isConfigured();
    }

    public String ask(String systemPrompt, String userPrompt, LocalDate today) throws AiUnavailableException {
        if (!ai.isConfigured()) {
            throw new AiUnavailableException("The AI is not set up. Add NVIDIA_API_KEY to the .env file.");
        }

        forgetOldAnswers(today);

        String key = today + "|" + systemPrompt + "|" + userPrompt;
        CompletableFuture<String> mine = new CompletableFuture<>();
        CompletableFuture<String> existing = calls.putIfAbsent(key, mine);

        if (existing != null) {
            return waitFor(existing);
        }

        try {
            String reply = ai.ask(systemPrompt, userPrompt);
            mine.complete(reply);
            return reply;
        } catch (AiUnavailableException | RuntimeException e) {
            calls.remove(key);
            mine.completeExceptionally(e);
            if (e instanceof AiUnavailableException unavailable) {
                throw unavailable;
            }
            throw new AiUnavailableException("The AI request failed.", e);
        }
    }

    // Forgets the answer to this question, for when the answer was received but could
    // not be used (so asking again really asks again, instead of repeating the same reply).
    public void forget(String systemPrompt, String userPrompt, LocalDate today) {
        calls.remove(today + "|" + systemPrompt + "|" + userPrompt);
    }

    private String waitFor(CompletableFuture<String> other) throws AiUnavailableException {
        try {
            return other.get(waitSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AiUnavailableException unavailable) {
                throw unavailable;
            }
            throw new AiUnavailableException("The AI request failed.", e);
        } catch (TimeoutException e) {
            throw new AiUnavailableException("The AI took too long to answer.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("The AI request was interrupted.", e);
        }
    }

    // answers are only kept for the day they were asked
    private synchronized void forgetOldAnswers(LocalDate today) {
        if (!today.equals(day) || calls.size() > MAX_REMEMBERED) {
            calls.clear();
            day = today;
        }
    }
}
