package bank.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;

// One request from the browser, with the bits the route handlers need:
// the method (GET/POST), the path split into parts, the query string, the
// form fields in the body, and who is signed in.
public class Request {

    private final HttpExchange exchange;
    private final String method;
    private final List<String> parts;
    private final FormData query;
    private final SessionStore.Session session;
    private FormData body;

    public Request(HttpExchange exchange, String method, List<String> parts, FormData query,
            SessionStore.Session session) {
        this.exchange = exchange;
        this.method = method;
        this.parts = parts;
        this.query = query;
        this.session = session;
    }

    public String getMethod() {
        return method;
    }

    public List<String> getParts() {
        return parts;
    }

    public FormData query() {
        return query;
    }

    public SessionStore.Session session() {
        return session;
    }

    // the most a form is ever allowed to send (real forms are far smaller)
    private static final int MAX_BODY_BYTES = 64 * 1024;

    // the form fields sent in the request body (read once, then remembered)
    public FormData form() throws IOException {
        if (body == null) {
            byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);

            if (bytes.length > MAX_BODY_BYTES) {
                throw new ApiException(413, "That request is too large.");
            }

            body = new FormData(new String(bytes, StandardCharsets.UTF_8));
        }
        return body;
    }

    // The part of the path at this position. For /api/loans/L001/payments,
    // part(1) is "L001" (part(0) is "loans").
    public String part(int index) {
        return parts.get(index);
    }

    // Does this request match a method and path shape? "*" matches any one part.
    //   matches("GET", "loans", "*")  matches  GET /api/loans/L001
    public boolean matches(String wantedMethod, String... shape) {
        if (!method.equals(wantedMethod) || parts.size() != shape.length) {
            return false;
        }

        for (int i = 0; i < shape.length; i++) {
            if (!shape[i].equals("*") && !shape[i].equals(parts.get(i))) {
                return false;
            }
        }
        return true;
    }

    public void send(int status, String json) throws IOException {
        sendJson(exchange, status, json);
    }

    static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        // answers hold private data, so browsers and proxies must not keep copies
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
