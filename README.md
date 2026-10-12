# NepoLM

NepoLM is a loan management system in Java with two ways to use it: a console menu and a web page. Both use the same rules and the same save file (`bank.dat`).

## Running the web version

From this folder:

```
.\run-web.ps1      # Windows PowerShell
./run-web.sh       # Git Bash, macOS, Linux
```

Then open http://localhost:8090 in your browser. Press Ctrl+C in the terminal to stop the server.

If port 8090 is taken, pass another one: `.\run-web.ps1 9000`.

## Running the console version

```
.\run.ps1
./run.sh
```

Don't run the console and web versions at the same time. Each keeps its own copy of the bank in memory, and whichever saves last overwrites the other's changes.

## Running the tests

```
.\run-tests.ps1
./run-tests.sh
```

## Project layout

| Folder | What's in it |
| --- | --- |
| `src/bank/model` | The bank rules: customers, loans, installments, reports |
| `src/bank/exception` | The errors the rules can throw |
| `src/bank/persistence` | Saving and loading `bank.dat` |
| `src/bank/Main.java` | The console menu |
| `src/bank/web` | The web server and its JSON API (`/api/...`) |
| `web/` | The web page: HTML, CSS and JavaScript |
| `test/` | JUnit tests |
