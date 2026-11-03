package bank.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The AI settings: the key, the address and the model name.
//
// They come from the computer's environment variables first, then from a file
// called .env in the project folder (one NAME=value per line). The .env file is
// ignored by git, so the key is never committed. Copy .env.example to .env and
// put your key in it.
//
//   NVIDIA_API_KEY   the key (without it, the AI features switch themselves off)
//   AI_BASE_URL      default https://integrate.api.nvidia.com/v1
//   AI_MODEL         default nvidia/nemotron-3.5-lightning-30b-a3b (answers in a few seconds)
//   AI_THINKING      "on" lets the model think before answering (much slower); default off
//   AI_TIMEOUT_SECONDS  how long to wait for one answer, default 90
public class AiConfig {

    public static final String DEFAULT_BASE_URL = "https://integrate.api.nvidia.com/v1";
    public static final String DEFAULT_MODEL = "nvidia/nemotron-3.5-lightning-30b-a3b";
    public static final int DEFAULT_TIMEOUT_SECONDS = 90;

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;

    private final boolean thinking;

    public AiConfig(String apiKey, String baseUrl, String model, int timeoutSeconds) {
        this(apiKey, baseUrl, model, timeoutSeconds, false);
    }

    public AiConfig(String apiKey, String baseUrl, String model, int timeoutSeconds, boolean thinking) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.thinking = thinking;
    }

    // reads the settings from the environment and from .env in the current folder
    public static AiConfig load() {
        return load(Path.of(".env"));
    }

    public static AiConfig load(Path envFile) {
        Map<String, String> fromFile = readEnvFile(envFile);

        String key = setting("NVIDIA_API_KEY", fromFile, "");
        String baseUrl = setting("AI_BASE_URL", fromFile, DEFAULT_BASE_URL);
        String model = setting("AI_MODEL", fromFile, DEFAULT_MODEL);

        int timeout = DEFAULT_TIMEOUT_SECONDS;
        try {
            timeout = Integer.parseInt(setting("AI_TIMEOUT_SECONDS", fromFile, String.valueOf(timeout)));
        } catch (NumberFormatException e) {
            // keep the default
        }

        boolean thinking = setting("AI_THINKING", fromFile, "off").equalsIgnoreCase("on");

        return new AiConfig(key, baseUrl, model, Math.max(5, timeout), thinking);
    }

    // true = let the model think before it answers (much slower). Off by default.
    public boolean isThinking() {
        return thinking;
    }

    public boolean hasKey() {
        return !apiKey.isEmpty();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    // never print the key: only say whether there is one
    @Override
    public String toString() {
        return "AiConfig{model=" + model + ", baseUrl=" + baseUrl + ", key=" + (hasKey() ? "set" : "missing") + "}";
    }

    private static String setting(String name, Map<String, String> fromFile, String fallback) {
        String fromEnvironment = System.getenv(name);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }

        String fromDotEnv = fromFile.get(name);
        if (fromDotEnv != null && !fromDotEnv.isBlank()) {
            return fromDotEnv.trim();
        }
        return fallback;
    }

    // Reads NAME=value lines. Blank lines and lines starting with # are skipped,
    // and quotes around a value are removed. A missing file just means "no settings".
    static Map<String, String> readEnvFile(Path file) {
        Map<String, String> values = new HashMap<>();

        List<String> lines;
        try {
            if (!Files.isRegularFile(file)) {
                return values;
            }
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            return values;
        }

        for (String line : lines) {
            String text = line.trim();
            if (text.isEmpty() || text.startsWith("#") || !text.contains("=")) {
                continue;
            }

            int equals = text.indexOf('=');
            String name = text.substring(0, equals).trim();
            String value = text.substring(equals + 1).trim();

            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }

            values.put(name, value);
        }
        return values;
    }
}
