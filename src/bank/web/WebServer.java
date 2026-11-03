package bank.web;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import bank.ai.AiService;

// Starts NepoLM, the web version of the Bank Loan Management System.
//
// Run it from the project folder (run-web.ps1 / run-web.sh do this), then
// open http://localhost:8090 in a browser. To use another port, pass it as
// the first argument: java -cp out bank.web.WebServer 9000
//
// Don't run this and the console app (bank.Main) at the same time: each
// keeps its own copy of the bank in memory, and whichever saves last
// overwrites the other's changes in bank.dat.
public class WebServer {

    private static final int DEFAULT_PORT = 8090;
    private static final String DATA_FILE = "bank.dat";

    public static void main(String[] args) throws IOException {

        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        BankService service = new BankService(DATA_FILE);

        // only reachable from this computer (localhost), since there's no login
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);

        HttpServer server;
        try {
            server = HttpServer.create(address, 0);
        } catch (BindException e) {
            System.out.println("Port " + port + " is already being used by another program.");
            System.out.println("Try another one, e.g.: java -cp out bank.web.WebServer 9000");
            return;
        }

        // The AI features need a key in the .env file (see .env.example). Without
        // one they switch themselves off and the page shows the system's own summaries.
        AiService ai = AiService.fromEnvironment();
        System.out.println(ai.isConfigured()
                ? "AI summaries are on."
                : "AI summaries are off (no NVIDIA_API_KEY in .env). The system's own summaries are used.");

        server.createContext("/api", new ApiHandler(service, ai));
        server.createContext("/", new StaticFileHandler(Path.of("web")));

        // A few threads, so a slow AI answer for one request never makes the other pages wait.
        // (The bank itself is still used one request at a time: see BankService.)
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.start();

        // the first time there is no admin login, one is created with a random password
        String adminPassword = service.takeFirstAdminPassword();
        if (adminPassword != null) {
            System.out.println();
            System.out.println("Admin login created. Write this down, it is shown only once:");
            System.out.println("  username: admin");
            System.out.println("  temporary password: " + adminPassword);
            System.out.println("You will be asked to choose your own password when you first sign in.");
            System.out.println();
        }

        System.out.println("NepoLM is running.");
        System.out.println("Open http://localhost:" + port + " in your browser.");
        System.out.println("Press Ctrl+C to stop the server.");
    }
}
