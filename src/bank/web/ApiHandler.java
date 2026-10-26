package bank.web;

import java.io.IOException;
import java.net.URI;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import bank.ai.AiService;
import bank.exception.ApplicationNotFoundException;
import bank.exception.CustomerAlreadyExistsException;
import bank.exception.CustomerNotFoundException;
import bank.exception.InsufficientFundsException;
import bank.exception.LoanLimitExceededException;
import bank.exception.LoanNotFoundException;
import bank.exception.OverdueInstallmentsException;
import bank.model.UserAccount;
import bank.model.UserRole;

// Handles every request that starts with /api.
//
// For each request it: checks the request is from our own page, works out who
// is signed in (from the cookie), checks they are allowed to use that route,
// runs it, and turns any exception into an HTTP status code plus
// {"error": "..."} so the page can show the message.
//
//   POST /api/login, /api/logout, /api/change-password and GET /api/me are here.
//   /api/my/...  is for customers (UserApi).
//   everything else is for the admin (AdminApi).
public class ApiHandler implements HttpHandler {

    private static final String COOKIE_NAME = "nepolm_session";

    private final BankService service;
    private final SessionStore sessions = new SessionStore();
    private final LoginThrottle throttle = new LoginThrottle();
    private final AdminApi adminApi;
    private final UserApi userApi;

    // without an AI: the summaries are written by the system alone
    public ApiHandler(BankService service) {
        this(service, AiService.disabled());
    }

    public ApiHandler(BankService service, AiService ai) {
        this.service = service;
        this.adminApi = new AdminApi(service, ai);
        this.userApi = new UserApi(service);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (ApiException e) {
            sendError(exchange, e.getStatus(), e.getMessage());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            sendError(exchange, 400, friendly(e));
        } catch (CustomerNotFoundException | LoanNotFoundException | ApplicationNotFoundException e) {
            sendError(exchange, 404, e.getMessage());
        } catch (CustomerAlreadyExistsException e) {
            sendError(exchange, 409, e.getMessage());
        } catch (LoanLimitExceededException | OverdueInstallmentsException
                | InsufficientFundsException e) {
            sendError(exchange, 422, e.getMessage());
        } catch (Exception e) {
            // unexpected problem: full details go to the server console only
            e.printStackTrace();
            sendError(exchange, 500, "Something went wrong on the server.");
        }
    }

    // ---------- Routing ----------

    private void route(HttpExchange exchange) throws Exception {

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getRawPath().substring("/api".length());

        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                parts.add(FormData.decode(part));
            }
        }

        FormData query = new FormData(exchange.getRequestURI().getRawQuery());

        if (method.equals("POST")) {
            checkSameOrigin(exchange);
        }

        // logging in is the one thing that doesn't need a session
        if (method.equals("POST") && parts.size() == 1 && parts.get(0).equals("login")) {
            login(exchange, method, parts, query);
            return;
        }

        SessionStore.Session session = sessions.find(readSessionToken(exchange));
        if (session == null) {
            throw new ApiException(401, "Please sign in.");
        }

        Request req = new Request(exchange, method, parts, query, session);

        // these work for both roles, even before a temporary password is replaced
        if (req.matches("POST", "logout")) {
            sessions.remove(session.getToken());
            exchange.getResponseHeaders().add("Set-Cookie", cookie("", 0));
            req.send(200, new Json().add("ok", true).toString());
            return;
        }

        if (req.matches("GET", "me")) {
            String json = service.read(bank -> {
                UserAccount me = bank.findUser(session.getUsername());
                if (me == null) {
                    throw new ApiException(401, "Please sign in.");
                }
                return JsonViews.me(bank, me).toString();
            });
            req.send(200, json);
            return;
        }

        if (req.matches("POST", "change-password")) {
            changePassword(req);
            return;
        }

        UserAccount account = service.read(bank -> bank.findUser(session.getUsername()));
        if (account == null) {
            throw new ApiException(401, "Please sign in.");
        }

        // a temporary password must be replaced before anything else can be used
        if (account.isMustChangePassword()) {
            throw new ApiException(403, "Choose your own password first.");
        }

        boolean handled;
        if (!parts.isEmpty() && parts.get(0).equals("my")) {
            requireRole(session, UserRole.USER);
            handled = userApi.handle(req);
        } else {
            requireRole(session, UserRole.ADMIN);
            handled = adminApi.handle(req);
        }

        if (!handled) {
            throw new ApiException(404, "No such endpoint: " + method + " /api" + path);
        }
    }

    private void requireRole(SessionStore.Session session, UserRole role) {
        if (session.getRole() != role) {
            throw new ApiException(403, "You are not allowed to do that.");
        }
    }

    // ---------- Signing in and out ----------

    private void login(HttpExchange exchange, String method, List<String> parts, FormData query)
            throws Exception {

        Request req = new Request(exchange, method, parts, query, null);

        String username = req.form().requireText("username", "Username");
        String password = req.form().requireExact("password", "Password");

        throttle.checkAllowed(username);

        UserAccount account = service.read(bank -> bank.authenticate(username, password));

        if (account == null) {
            throttle.recordFailure(username);
            throw new ApiException(401, "Wrong username or password.");
        }

        throttle.recordSuccess(username);
        SessionStore.Session session = sessions.create(account);

        exchange.getResponseHeaders().add("Set-Cookie", cookie(session.getToken(), 8 * 60 * 60));
        req.send(200, service.read(bank -> JsonViews.me(bank, account).toString()));
    }

    private void changePassword(Request req) throws Exception {
        String username = req.session().getUsername();
        String current = req.form().requireExact("currentPassword", "Current password");
        String replacement = req.form().requireExact("newPassword", "New password");

        service.change(bank -> {
            bank.changePassword(username, current, replacement);
            return null;
        });

        req.send(200, new Json().add("ok", true).toString());
    }

    // HttpOnly: page scripts can't read the cookie. SameSite=Strict: the browser
    // won't send it with requests that start on another website.
    private String cookie(String token, int maxAgeSeconds) {
        return COOKIE_NAME + "=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + maxAgeSeconds;
    }

    private String readSessionToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null) {
            return null;
        }

        for (String pair : header.split(";")) {
            String trimmed = pair.trim();
            if (trimmed.startsWith(COOKIE_NAME + "=")) {
                return trimmed.substring(COOKIE_NAME.length() + 1);
            }
        }
        return null;
    }

    // Blocks requests that were sent from some other website's page: if the
    // browser says where the request came from (Origin), it must be this server.
    private void checkSameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String host = exchange.getRequestHeaders().getFirst("Host");

        if (origin == null || host == null) {
            return;
        }

        String originHost;
        try {
            URI uri = URI.create(origin);
            originHost = uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
        } catch (IllegalArgumentException e) {
            originHost = "";
        }

        if (!host.equalsIgnoreCase(originHost)) {
            throw new ApiException(403, "That request came from another website and was blocked.");
        }
    }

    // ---------- Errors ----------

    // e.g. a date that isn't a date: the raw Java message means little to a person
    private String friendly(Exception e) {
        if (e instanceof DateTimeParseException) {
            return "Enter the date as year-month-day, for example 2026-10-15.";
        }
        return e.getMessage();
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Request.sendJson(exchange, status, new Json().add("error", message).toString());
    }
}
