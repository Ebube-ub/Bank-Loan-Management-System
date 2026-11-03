package bank.ai;

// Anything that can turn a question into text. The real one talks to NVIDIA
// (NvidiaAi); the tests use a fake one so they never need a key or a network.
public interface TextAi {

    // false when there is no key, so the app can skip the AI and say why
    boolean isConfigured();

    // returns the AI's reply text, or throws AiUnavailableException
    String ask(String systemPrompt, String userPrompt) throws AiUnavailableException;
}
