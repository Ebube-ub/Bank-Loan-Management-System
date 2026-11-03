package bank.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import bank.web.Json;

// Talks to NVIDIA's chat API (it uses the same format as OpenAI's, so the same
// request works). Java's built-in web client does the sending: no library needed.
//
// It never throws anything but AiUnavailableException, and the key is only ever
// put in the request header: it is never printed or put in a message.
public class NvidiaAi implements TextAi {

    // Some models "think" first, and the thinking counts towards this limit.
    // Too small and the answer comes back empty, so it is generous.
    private static final int MAX_TOKENS = 2048;

    private final AiConfig config;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public NvidiaAi(AiConfig config) {
        this.config = config;
    }

    @Override
    public boolean isConfigured() {
        return config.hasKey();
    }

    @Override
    public String ask(String systemPrompt, String userPrompt) throws AiUnavailableException {
        if (!config.hasKey()) {
            throw new AiUnavailableException("The AI is not set up. Add NVIDIA_API_KEY to the .env file.");
        }

        Json json = new Json()
                .add("model", config.getModel())
                .addRaw("messages", "[" + message("system", systemPrompt) + "," + message("user", userPrompt) + "]")
                .addRaw("temperature", "0.3")
                .addRaw("top_p", "1")
                .add("max_tokens", MAX_TOKENS)
                .add("stream", false);

        // Rewording needs no deep thought, and thinking turns a 3-second answer into a
        // 15 to 60 second one. Models that do not think simply ignore this.
        if (!config.isThinking()) {
            json.addRaw("chat_template_kwargs", "{\"enable_thinking\":false}");
        }

        String body = json.toString();

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AiUnavailableException("The AI took too long to answer.", e);
        } catch (IOException e) {
            throw new AiUnavailableException("Could not reach the AI service.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("The AI request was interrupted.", e);
        }

        if (response.statusCode() != 200) {
            throw new AiUnavailableException("The AI service answered with an error (HTTP "
                    + response.statusCode() + ").");
        }

        String text = extractReply(response.body());
        if (text == null || text.isBlank()) {
            throw new AiUnavailableException("The AI sent back an empty answer.");
        }
        return text.trim();
    }

    private static String message(String role, String content) {
        return new Json().add("role", role).add("content", content).toString();
    }

    // ---------- Reading the answer ----------
    //
    // The answer looks like {"choices":[{"message":{"role":"assistant","content":"..."}}]}.
    // There is no JSON reader in this project, so this finds "content" after
    // "message" and reads that one text value. (A model that is still "thinking"
    // when it runs out of room sends "content":null, which gives null here.)

    static String extractReply(String json) {
        if (json == null) {
            return null;
        }

        int message = json.indexOf("\"message\"");
        if (message < 0) {
            return null;
        }

        int key = json.indexOf("\"content\"", message);
        if (key < 0) {
            return null;
        }

        int colon = json.indexOf(':', key + "\"content\"".length());
        if (colon < 0) {
            return null;
        }

        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }

        if (i >= json.length() || json.charAt(i) != '"') {
            return null; // null, or something that is not text
        }

        return cleanEntities(readJsonString(json, i + 1));
    }

    // reads a JSON text value starting just after its opening quote
    private static String readJsonString(String json, int start) {
        StringBuilder text = new StringBuilder();

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '"') {
                return text.toString();
            }

            if (c != '\\' || i + 1 >= json.length()) {
                text.append(c);
                continue;
            }

            char next = json.charAt(++i);
            switch (next) {
                case 'n' -> text.append('\n');
                case 'r' -> text.append('\r');
                case 't' -> text.append('\t');
                case 'b' -> text.append('\b');
                case 'f' -> text.append('\f');
                case 'u' -> {
                    if (i + 4 < json.length()) {
                        try {
                            text.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            text.append("\\u");
                        }
                    }
                }
                default -> text.append(next); // \" \\ \/
            }
        }
        return text.toString();
    }

    // Some answers come back with HTML codes, e.g. "8 &gt; 1". The page escapes all
    // text itself, so turn the codes back into plain characters first.
    static String cleanEntities(String text) {
        return text
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }
}
