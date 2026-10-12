package bank.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

// Sends the browser the files in the web/ folder (index.html, css, js).
// "/" means index.html. Anything outside web/ is refused.
public class StaticFileHandler implements HttpHandler {

    private final Path root;

    public StaticFileHandler(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        String path = FormData.decode(exchange.getRequestURI().getRawPath());
        if (path.equals("/")) {
            path = "/index.html";
        }

        // normalize() removes things like "/../" so we can check the file
        // really is inside web/ and someone can't ask for /../bank.dat
        Path file = root.resolve(path.substring(1)).normalize();

        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            byte[] message = "404 - file not found".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(404, message.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(message);
            }
            return;
        }

        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file.toString()));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private String contentType(String fileName) {
        if (fileName.endsWith(".html")) {
            return "text/html; charset=utf-8";
        } else if (fileName.endsWith(".css")) {
            return "text/css; charset=utf-8";
        } else if (fileName.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        } else if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }
}
