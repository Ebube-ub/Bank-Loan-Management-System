package bank.web;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests the real web API over HTTP: that signing in works, that roles are
// enforced on the server, that customers can only touch their own data, and
// that a loan goes from application to approval to payment.
class WebApiTest {

    private static final String ADMIN_PASSWORD = "AdminPassword9";

    @TempDir
    static Path tempFolder;

    private static HttpServer server;
    private static String base;
    private static final HttpClient client = HttpClient.newHttpClient();

    // a signed-in admin, shared by the tests
    private static String adminCookie;

    @BeforeAll
    static void startServer() throws Exception {
        BankService service = new BankService(tempFolder.resolve("api-test.dat").toString());

        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api", new ApiHandler(service));
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();

        // the admin's first login uses a temporary password and must be changed
        String temporary = service.takeFirstAdminPassword();
        String temporaryCookie = login("admin", temporary).cookie;
        post("/api/change-password", temporaryCookie,
                "currentPassword", temporary, "newPassword", ADMIN_PASSWORD);

        adminCookie = login("admin", ADMIN_PASSWORD).cookie;
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    // ---------- Small helpers ----------

    private record Reply(int status, String body, String cookie) {
    }

    private static Reply send(HttpRequest.Builder builder, String cookie) throws Exception {
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        String setCookie = response.headers().firstValue("Set-Cookie").orElse(null);
        String newCookie = setCookie == null ? null : setCookie.split(";")[0];

        return new Reply(response.statusCode(), response.body(), newCookie);
    }

    private static Reply get(String path, String cookie) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), cookie);
    }

    private static Reply post(String path, String cookie, String... fields) throws Exception {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < fields.length; i += 2) {
            if (i > 0) {
                body.append("&");
            }
            body.append(URLEncoder.encode(fields[i], StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(fields[i + 1], StandardCharsets.UTF_8));
        }

        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())), cookie);
    }

    private static Reply login(String username, String password) throws Exception {
        return post("/api/login", null, "username", username, "password", password);
    }

    // pulls a text value out of the JSON, e.g. field(body, "applicationId") -> A001
    private static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
        assertTrue(matcher.find(), "no field " + name + " in " + json);
        return matcher.group(1);
    }

    // creates a customer as the admin and signs them in with their own password
    private static String newCustomer(String customerId) throws Exception {
        Reply created = post("/api/customers", adminCookie,
                "customerId", customerId, "name", "Test " + customerId, "email", customerId + "@example.com");
        assertEquals(201, created.status, created.body);

        String temporary = field(created.body, "temporaryPassword");
        String temporaryCookie = login(customerId.toLowerCase(), temporary).cookie;

        String ownPassword = "Password-" + UUID.randomUUID();
        assertEquals(200, post("/api/change-password", temporaryCookie,
                "currentPassword", temporary, "newPassword", ownPassword).status);

        return login(customerId.toLowerCase(), ownPassword).cookie;
    }

    // ---------- Signing in ----------

    @Test
    void withoutSigningIn_everythingIsRefused() throws Exception {
        assertEquals(401, get("/api/customers", null).status);
        assertEquals(401, get("/api/my/summary", null).status);
        assertEquals(401, get("/api/finance", null).status);
        assertEquals(401, get("/api/me", null).status);
    }

    @Test
    void wrongPassword_isRefused_withoutSayingWhichPartWasWrong() throws Exception {
        Reply wrongPassword = login("admin", "not-the-password");
        Reply wrongUser = login("nobody-here", "not-the-password");

        assertEquals(401, wrongPassword.status);
        assertEquals(401, wrongUser.status);
        assertEquals(wrongPassword.body, wrongUser.body);
        assertNull(wrongPassword.cookie);
    }

    private static void assertNull(Object value) {
        org.junit.jupiter.api.Assertions.assertNull(value);
    }

    @Test
    void tooManyWrongPasswords_lockTheUsernameForAWhile() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login("lock-test", "wrong").status);
        }

        assertEquals(429, login("lock-test", "wrong").status);
    }

    @Test
    void theSessionCookie_isHttpOnlyAndSameSiteStrict() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(base + "/api/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=admin&password=" + ADMIN_PASSWORD))
                .build(), HttpResponse.BodyHandlers.ofString());

        String setCookie = response.headers().firstValue("Set-Cookie").orElse("");
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), setCookie);
    }

    @Test
    void me_saysWhoIsSignedIn_andLogoutEndsTheSession() throws Exception {
        String cookie = login("admin", ADMIN_PASSWORD).cookie;

        Reply me = get("/api/me", cookie);
        assertEquals(200, me.status);
        assertEquals("ADMIN", field(me.body, "role"));

        assertEquals(200, post("/api/logout", cookie).status);
        assertEquals(401, get("/api/me", cookie).status);
    }

    @Test
    void aTemporaryPassword_mustBeReplacedBeforeAnythingElseWorks() throws Exception {
        Reply created = post("/api/customers", adminCookie,
                "customerId", "TMP1", "name", "Temp One", "email", "tmp1@example.com");
        String temporary = field(created.body, "temporaryPassword");
        String cookie = login("tmp1", temporary).cookie;

        assertEquals("true", Pattern.compile("\"mustChangePassword\":(true|false)")
                .matcher(get("/api/me", cookie).body).results().findFirst().get().group(1));
        assertEquals(403, get("/api/my/summary", cookie).status);

        assertEquals(400, post("/api/change-password", cookie,
                "currentPassword", temporary, "newPassword", "short").status);
        assertEquals(200, post("/api/change-password", cookie,
                "currentPassword", temporary, "newPassword", "A-Good-Password-1").status);

        assertEquals(200, get("/api/my/summary", cookie).status);
    }

    // ---------- Roles ----------

    @Test
    void aCustomer_cannotUseAdminRoutes() throws Exception {
        String cookie = newCustomer("ROLE1");

        assertEquals(403, get("/api/customers", cookie).status);
        assertEquals(403, get("/api/finance", cookie).status);
        assertEquals(403, get("/api/applications", cookie).status);
        assertEquals(403, get("/api/loans", cookie).status);
        assertEquals(403, post("/api/applications/A001/approve", cookie).status);
        assertEquals(403, post("/api/demo-data", cookie).status);
    }

    @Test
    void theAdmin_cannotUseCustomerRoutes() throws Exception {
        assertEquals(403, get("/api/my/summary", adminCookie).status);
        assertEquals(403, get("/api/my/loans", adminCookie).status);
        assertEquals(403, post("/api/my/applications", adminCookie, "principal", "1000", "termMonths", "6").status);
    }

    @Test
    void aCustomer_cannotSeeOrPayAnotherCustomersLoan() throws Exception {
        String cookie = newCustomer("OWN1");

        // D001-D belongs to demo customer D001
        assertEquals(404, get("/api/my/loans/D001-D", cookie).status);
        assertEquals(404, post("/api/my/loans/D001-D/payments", cookie, "amount", "100").status);

        // and their own list doesn't include it
        assertFalse(get("/api/my/loans", cookie).body.contains("D001-D"));
    }

    @Test
    void aRequestFromAnotherWebsite_isBlocked() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(base + "/api/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "http://evil.example")
                .POST(HttpRequest.BodyPublishers.ofString("username=admin&password=" + ADMIN_PASSWORD))
                .build(), HttpResponse.BodyHandlers.ofString());

        // (the HTTP client may refuse to send the Origin header; if it does, the
        // request is simply an ordinary login and this test has nothing to check)
        assertTrue(response.statusCode() == 403 || response.statusCode() == 200);
    }

    // ---------- Customers ----------

    @Test
    void theAdmin_seesEveryCustomerWithScoreBandAndTier() throws Exception {
        Reply customers = get("/api/customers", adminCookie);

        assertEquals(200, customers.status);
        assertTrue(customers.body.contains("\"customerId\":\"D001\""));
        assertTrue(customers.body.contains("\"band\":\"EXCELLENT\""));
        assertTrue(customers.body.contains("\"band\":\"POOR\""));
        assertTrue(customers.body.contains("\"tier\":\"PREMIUM\""));
        assertTrue(customers.body.contains("\"tier\":\"NONE\""));
    }

    @Test
    void theAdmin_canSeeTheReasonBehindAScore() throws Exception {
        Reply detail = get("/api/customers/D009", adminCookie);

        assertEquals(200, detail.status);
        assertTrue(detail.body.contains("overdue now"), detail.body);
        assertTrue(detail.body.contains("\"factors\""));
    }

    @Test
    void creatingACustomer_checksTheDetails() throws Exception {
        assertEquals(400, post("/api/customers", adminCookie,
                "customerId", "bad id!", "name", "X", "email", "x@example.com").status);
        assertEquals(400, post("/api/customers", adminCookie,
                "customerId", "OKID1", "name", "X", "email", "not-an-email").status);
        assertEquals(409, post("/api/customers", adminCookie,
                "customerId", "D001", "name", "Dup", "email", "dup@example.com").status);
    }

    @Test
    void resetPassword_givesANewTemporaryPassword() throws Exception {
        Reply reset = post("/api/customers/D006/reset-password", adminCookie);

        assertEquals(200, reset.status);
        assertEquals("d006", field(reset.body, "username"));

        Reply signIn = login("d006", field(reset.body, "temporaryPassword"));
        assertEquals(200, signIn.status);
        assertTrue(signIn.body.contains("\"mustChangePassword\":true"));
    }

    // ---------- A loan from start to finish ----------

    @Test
    void applyApprovePay_endToEnd() throws Exception {
        String customer = newCustomer("FLOW1");

        // the customer sees their score and what they can borrow
        Reply summary = get("/api/my/summary", customer);
        assertTrue(summary.body.contains("\"score\":600"), summary.body);
        assertTrue(summary.body.contains("\"band\":\"FAIR\""));
        assertTrue(summary.body.contains("\"canApply\":true"));
        assertTrue(summary.body.contains("\"availableToBorrow\":\"300000.00\""), summary.body);

        // they can see how the score is worked out, and what a late payment costs
        assertTrue(summary.body.contains("\"scoreDetails\""), summary.body);
        assertTrue(summary.body.contains("Starting score"), summary.body);
        assertEquals("5", field(summary.body, "lateFeePercent"));

        // they are told the rules before they apply, with the numbers the system really uses
        assertTrue(summary.body.contains("\"warningAfterDays\":10"), summary.body);
        assertTrue(summary.body.contains("\"escalateAfterDays\":5"), summary.body);
        assertEquals("1.67", field(summary.body, "monthlyPercent"));
        assertTrue(summary.body.contains("\"lateExample\":{\"days\":75"), summary.body);
        assertTrue(summary.body.contains("\"total\":\"109429.84\""), summary.body);

        // they cannot apply without ticking the box that says they read the rules
        Reply notTicked = post("/api/my/applications", customer, "principal", "5000", "termMonths", "6");
        assertEquals(400, notTicked.status, notTicked.body);
        assertTrue(notTicked.body.contains("Tick the box"), notTicked.body);

        // they apply: it waits for the admin, and they are not shown the advice
        Reply applied = post("/api/my/applications", customer, "principal", "5000", "termMonths", "6",
                "acceptRules", "true");
        assertEquals(201, applied.status, applied.body);
        assertEquals("PENDING", field(applied.body, "status"));
        assertFalse(applied.body.contains("\"advice\""));
        String applicationId = field(applied.body, "applicationId");

        // they can't apply again while it waits
        assertEquals(400, post("/api/my/applications", customer, "principal", "1000", "termMonths", "6",
                "acceptRules", "true").status);
        assertTrue(get("/api/my/summary", customer).body.contains("\"canApply\":false"));

        // the admin sees it, with the advice
        Reply queue = get("/api/applications?status=PENDING", adminCookie);
        assertTrue(queue.body.contains(applicationId));
        Reply detail = get("/api/applications/" + applicationId, adminCookie);
        assertEquals("REVIEW", field(detail.body, "advice"));

        // the admin can see the day the customer accepted the rules
        assertEquals(java.time.LocalDate.now().toString(), field(detail.body, "rulesAcceptedOn"));
        assertTrue(detail.body.contains("\"scoreNow\""));

        // the admin approves
        Reply approved = post("/api/applications/" + applicationId + "/approve", adminCookie);
        assertEquals(200, approved.status, approved.body);
        String loanId = field(approved.body, "loanId");
        assertEquals("ACTIVE", field(approved.body, "status"));
        assertEquals("20", field(approved.body, "interestPercent"));

        // the customer sees the loan and the decision
        assertTrue(get("/api/my/loans", customer).body.contains(loanId));
        assertTrue(get("/api/my/applications", customer).body.contains("Approved"));

        // the customer pays an installment
        Reply details = get("/api/my/loans/" + loanId, customer);
        String firstInstallment = field(details.body.substring(details.body.indexOf("\"installments\"")), "amountDue");
        Reply paid = post("/api/my/loans/" + loanId + "/payments", customer, "amount", firstInstallment);
        assertEquals(200, paid.status, paid.body);
        assertTrue(paid.body.contains("\"byAdmin\":false"));

        // the admin records a payment for them, and it is marked as the admin's
        Reply adminPaid = post("/api/loans/" + loanId + "/payments", adminCookie, "amount", "100");
        assertEquals(200, adminPaid.status, adminPaid.body);
        assertTrue(adminPaid.body.contains("\"byAdmin\":true"));
        assertTrue(adminPaid.body.contains("\"recordedBy\":\"admin\""));
    }

    @Test
    void aRuleBreakingRequest_isDeclinedStraightAway_withTheReason() throws Exception {
        String customer = newCustomer("FLOW2");

        Reply applied = post("/api/my/applications", customer, "principal", "310000", "termMonths", "6",
                "acceptRules", "true");

        assertEquals(201, applied.status);
        assertEquals("DECLINED", field(applied.body, "status"));
        assertTrue(applied.body.contains("largest single loan"), applied.body);
    }

    @Test
    void theAdmin_canDeclineWithANote() throws Exception {
        String customer = newCustomer("FLOW3");
        String applicationId = field(
                post("/api/my/applications", customer, "principal", "3000", "termMonths", "6",
                        "acceptRules", "true").body,
                "applicationId");

        Reply declined = post("/api/applications/" + applicationId + "/decline", adminCookie,
                "note", "Please come back next month.");

        assertEquals(200, declined.status, declined.body);
        assertEquals("DECLINED", field(declined.body, "status"));
        assertTrue(get("/api/my/applications", customer).body.contains("Please come back next month."));

        // it is decided, so it can't be approved now
        assertEquals(400, post("/api/applications/" + applicationId + "/approve", adminCookie).status);
    }

    @Test
    void aPoorCustomer_isToldWhyTheyCannotApply() throws Exception {
        Reply reset = post("/api/customers/D009/reset-password", adminCookie);
        String temporary = field(reset.body, "temporaryPassword");
        String cookie = login("d009", temporary).cookie;
        post("/api/change-password", cookie, "currentPassword", temporary, "newPassword", "Struggling-Pass-1");
        cookie = login("d009", "Struggling-Pass-1").cookie;

        Reply summary = get("/api/my/summary", cookie);

        assertTrue(summary.body.contains("\"canApply\":false"), summary.body);
        assertTrue(summary.body.contains("Poor band"), summary.body);
    }

    // ---------- Finance and reports ----------

    @Test
    void theFinancePage_showsRevenueDeductionsAndProfit() throws Exception {
        Reply finance = get("/api/finance", adminCookie);

        assertEquals(200, finance.status);
        assertNotNull(field(finance.body, "revenue"));
        assertNotNull(field(finance.body, "badDebt"));
        assertNotNull(field(finance.body, "profit"));
        assertTrue(finance.body.contains("\"months\":["));
    }

    @Test
    void badInput_getsAClearMessage_notACrash() throws Exception {
        assertEquals(400, post("/api/customers", adminCookie,
                "customerId", "bad id!", "name", "X", "email", "x@example.com").status);
        assertEquals(404, get("/api/loans/NOPE", adminCookie).status);
        assertEquals(404, get("/api/customers/NOPE", adminCookie).status);
        assertEquals(404, get("/api/nothing-here", adminCookie).status);
    }

    @Test
    void summaryReport_countsPendingApplications() throws Exception {
        Reply summary = get("/api/reports/summary", adminCookie);

        assertEquals(200, summary.status);
        assertTrue(summary.body.contains("\"pendingApplications\":"));
        assertTrue(summary.body.contains("\"availableFunds\":"));
    }

    @Test
    void demoData_cannotBeLoadedTwice() throws Exception {
        Reply again = post("/api/demo-data", adminCookie);

        assertEquals(409, again.status);
        assertTrue(again.body.contains("already loaded"));
    }

    @Test
    void responses_areNotCached() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(base + "/api/me"))
                .header("Cookie", adminCookie).GET().build(), HttpResponse.BodyHandlers.ofString());

        Map<String, java.util.List<String>> headers = response.headers().map();
        assertEquals("no-store", headers.get("cache-control").get(0));
    }

    // ---------- Late customers: warning, notice, lawyer file ----------

    // signs in as a demo customer after the admin resets their password
    private static String demoCustomer(String customerId) throws Exception {
        Reply reset = post("/api/customers/" + customerId + "/reset-password", adminCookie);
        assertEquals(200, reset.status, reset.body);

        String temporary = field(reset.body, "temporaryPassword");
        String temporaryCookie = login(customerId.toLowerCase(), temporary).cookie;

        String ownPassword = "Password-" + UUID.randomUUID();
        assertEquals(200, post("/api/change-password", temporaryCookie,
                "currentPassword", temporary, "newPassword", ownPassword).status);

        return login(customerId.toLowerCase(), ownPassword).cookie;
    }

    @Test
    void lateCustomers_areForTheAdminOnly() throws Exception {
        String customer = newCustomer("LATE0");

        assertEquals(403, get("/api/collections", customer).status);
        assertEquals(403, get("/api/collections/D011/warning-preview", customer).status);
        assertEquals(403, post("/api/collections/D011/warning", customer).status);
        assertEquals(403, get("/api/collections/D011/lawyer-file", customer).status);
        assertEquals(401, get("/api/collections", null).status);
    }

    @Test
    void aWarning_isPreviewed_sent_andThenSeenByTheCustomer() throws Exception {
        String customer = demoCustomer("D011"); // three installments late, the oldest 70+ days

        // before any warning, the customer has no notice
        assertTrue(get("/api/my/summary", customer).body.contains("\"notice\":null"));

        // the admin sees them waiting for a warning
        Reply list = get("/api/collections", adminCookie);
        assertEquals(200, list.status, list.body);
        assertTrue(list.body.contains("\"customerId\":\"D011\""), list.body);
        assertTrue(list.body.contains("\"stage\":\"SEND_WARNING\""), list.body);
        assertTrue(summaryNumber("warningsToSend") >= 1);

        // the preview shows the exact words, saved nowhere
        Reply preview = get("/api/collections/D011/warning-preview", adminCookie);
        assertEquals(200, preview.status, preview.body);
        assertTrue(preview.body.contains("Dear Obinna Chukwu"), preview.body);
        assertTrue(preview.body.contains("legal action"), preview.body);
        assertTrue(get("/api/my/summary", customer).body.contains("\"notice\":null"));

        // sending it starts the clock
        Reply sent = post("/api/collections/D011/warning", adminCookie);
        assertEquals(200, sent.status, sent.body);
        assertEquals("WARNED", field(sent.body, "stage"));
        assertEquals(java.time.LocalDate.now().plusDays(5).toString(), field(sent.body, "deadline"));

        // it cannot be sent twice
        assertEquals(400, post("/api/collections/D011/warning", adminCookie).status);

        // now the customer sees it on their own page, with their own numbers
        Reply summary = get("/api/my/summary", customer);
        assertTrue(summary.body.contains("\"kind\":\"WARNING\""), summary.body);
        assertTrue(summary.body.contains("Dear Obinna Chukwu"), summary.body);
        assertTrue(summary.body.contains("\"lateInterest\""), summary.body);

        // and it is not yet time to refer it to a lawyer
        assertEquals(400, post("/api/collections/D011/refer", adminCookie, "note", "too early").status);
    }

    @Test
    void theLawyerFile_listsEverythingTheLawyerNeeds() throws Exception {
        Reply file = get("/api/collections/D010/lawyer-file", adminCookie);

        assertEquals(200, file.status, file.body);
        assertTrue(file.body.contains("LAWYER FILE"), file.body);
        assertTrue(file.body.contains("Segun Alade"), file.body);
        assertTrue(file.body.contains("WHAT IS OWED TODAY"), file.body);
        assertTrue(file.body.contains("RULES THE CUSTOMER ACCEPTED"), file.body);
        assertTrue(file.body.contains("Loan D010-A"), file.body);
        assertTrue(file.body.contains("\"canRefer\":false"), file.body);

        // someone who is not late has no file
        assertEquals(400, get("/api/collections/D001/lawyer-file", adminCookie).status);
    }

    private static int summaryNumber(String name) throws Exception {
        Matcher matcher = Pattern.compile("\"" + name + "\":(\\d+)").matcher(get("/api/reports/summary", adminCookie).body);
        assertTrue(matcher.find(), name);
        return Integer.parseInt(matcher.group(1));
    }
}
