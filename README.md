# NepoLM

NepoLM is a loan management system in Java with two ways to use it: a web page (with logins for the lender and for customers) and a console menu. Both use the same rules and the same save file (`bank.dat`). All amounts on the web page are in naira (₦).

## What it does

- **Credit score** (300 to 850) worked out from each customer's loan history: on-time and late payments, overdue installments, paid-off and defaulted loans, and how much of the ₦2,000,000 borrowing limit is in use. A new customer starts at 600. Customers can open "How is my score worked out?" on their account page to see the rules and their own breakdown.
- **Bands** decide what a customer is allowed:

  | Band | Score | Largest single loan | Tier and active loans |
  | --- | --- | --- | --- |
  | Excellent | 750 to 850 | ₦1,000,000 | Premium, up to 3 |
  | Good | 670 to 749 | ₦600,000 | Premium, up to 3 |
  | Fair | 580 to 669 | ₦300,000 | Standard, up to 2 |
  | Poor | 300 to 579 | none | cannot borrow |

- **Advice, then approval.** A customer applies; the system checks the rules and advises the lender (Lend, Review or Decline). Nothing is lent until the lender approves it. Applications that break a rule are declined straight away, with the reason.
- **Late fee.** If an installment is paid after its due date, a fee of 5% of that installment is added once. Customers are told about it before they apply, and it counts as money earned. To change it, change `LATE_FEE_RATE` in `src/bank/model/Bank.java`.
- **Interest on late amounts.** An overdue amount (the installment plus its late fee) grows by the loan's yearly rate divided by 12 each month, compounded (a month counts as 30 days, and part months count). The interest is added to what is due, so paying the installment pays its fee and interest too. Example: a ₦100,000 installment 75 days late costs ₦100,000 + ₦5,000 late fee + ₦4,429.84 interest = ₦109,429.84. The formula is `Installment.lateInterestFor`.
- **Warnings and escalation.** On the **Overdue** page the lender sees late installments coloured by how late they are, with the late fee and interest split out. More than 10 days late, a **Send warning** button appears; one click puts the warning on the customer's own pages. If they have not paid 5 days after the warning, the case needs escalation (a count shows in the menu and on the Dashboard, and the customer sees a stronger notice), and the lender can prepare a lawyer file and mark the case as referred. The system sends nothing by email or text. The days are `WARNING_AFTER_DAYS` and `ESCALATE_AFTER_DAYS` in `Bank.java`, and the wording is in `CollectionMessages.java`.
- **Rules before applying.** Above the Apply button, customers read the rules (interest, late fee, interest on late amounts, warnings, prepayment charge) with the system's own numbers, and must tick "I have read and understood these rules". The day they ticked is saved with the application.
- **Prepayment penalty.** Paying ahead has a 2% charge: money that pays installments which are not due yet is charged 2%, and the charge is taken out of the payment (so paying ₦X always costs exactly ₦X). The next installment is never charged, so paying it a few days early is free. The pay box shows customers what it would cost to pay the whole loan off today. The charge counts as money earned. To change it, change `PREPAYMENT_FEE_RATE` in `src/bank/model/Bank.java`.
- **The bank's money.** A new bank starts with ₦5,000,000 (`STARTING_CAPITAL` in `Bank.java`). A bank saved with less is topped up once, by the difference.
- **Two roles.** The lender (admin) sees everything: applications, customers and their scores, loans, payments, finance (money earned, money lost and profit, month by month) and reports. A customer sees their own score and loans, applies for loans, makes payments, and sees any warning the lender has sent.

## Running the web version

From this folder:

```
powershell -ExecutionPolicy Bypass -File .\run-web.ps1    # Windows
./run-web.sh                                              # Git Bash, macOS, Linux
```

Then open http://localhost:8090 in your browser. Press Ctrl+C in the terminal to stop the server. If port 8090 is taken, pass another one after the script name, for example `./run-web.sh 9000`.

**First run.** If there is no admin login yet, one is created and its temporary password is printed once in the terminal (username `admin`). You choose your own password when you first sign in. A brand-new bank also gets 11 demo customers (D001 to D011) with loan histories. If you already have a `bank.dat`, an admin can add the demo customers from the Customers page.

**Customers.** The admin adds a customer, which creates a login: the username is the customer ID in lower case, with a temporary password shown once. The customer chooses their own password when they first sign in. "Reset password" gives a new temporary one.

**Security notes.** Passwords are stored only as salted hashes. Sessions use an HttpOnly, SameSite=Strict cookie, and the server checks the role on every request. The server only listens on `localhost`. Before putting it on the internet, serve it over HTTPS and add the `Secure` flag to the cookie.

## Running the console version

```
.\run.ps1
./run.sh
```

The console works as the admin. It shows amounts as `NGN` because Windows consoles can't print the naira sign.

Don't run the console and web versions at the same time. Each keeps its own copy of the bank in memory, and whichever saves last overwrites the other's changes.

## Running the tests

```
.\run-tests.ps1
./run-tests.sh
```

## Project layout

| Folder | What's in it |
| --- | --- |
| `src/bank/model` | The bank rules: customers, loans, credit score, applications, finance |
| `src/bank/demo` | The demo customers |
| `src/bank/security` | Password hashing |
| `src/bank/exception` | The errors the rules can throw |
| `src/bank/persistence` | Saving and loading `bank.dat` |
| `src/bank/Main.java` | The console menu |
| `src/bank/web` | The web server and its JSON API (`/api/...`) |
| `web/` | The web page: HTML, CSS and JavaScript |
| `test/` | JUnit tests |

`bank.dat` holds customer details and login hashes, so it is not kept in git.
