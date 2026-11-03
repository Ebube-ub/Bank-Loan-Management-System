# How NepoLM works: the credit score and the lending rules

This page explains, in plain words, how a customer's credit score is worked out and every rule the system uses to decide whether someone can borrow. Every number here is what the system really uses.

---

## 1. What is a credit score?

A credit score is a number from **300 to 850** that shows how reliable someone is at paying money back. A higher number means more trust, so they can borrow more.

- The score is **worked out fresh every time** it is needed, from the customer's loan history. It is never typed in by hand, so it can't be wrong or out of date.
- Everyone with no history starts at **600**. That is "cautious, but not blocked".
- After that, good behaviour adds points and bad behaviour takes points away.

---

## 2. How the points work

| What happened | Points | Limit |
| --- | --- | --- |
| Paid an installment **on or before** its due date | **+10** each | Counts up to 15 installments (so at most **+150**) |
| Paid an installment **late** | **-15** each | No limit |
| An installment is **overdue right now** (past its due date and unpaid) | **-40** each | No limit |
| Fully paid off a loan | **+25** each | Counts up to 3 loans (so at most **+75**) |
| A loan **defaulted** (see section 5) | **-150** each | No limit |
| How much of the borrowing limit is in use (see below) | **+10, 0, -20 or -40** | Only while the customer has a loan running |

The final score never goes below 300 or above 850.

### The "borrowing limit in use" points

Each customer can owe at most **₦2,000,000** across all their running loans. The system looks at how much of that they are using (the original amounts of their running loans):

| Share of the limit in use | Points |
| --- | --- |
| Under 25% | +10 |
| 25% up to 50% | 0 |
| Over 50% up to 80% | -20 |
| Over 80% | -40 |

Borrowing close to the limit looks risky, so it costs points. If a customer has no running loan, this part is skipped.

### Worked examples

**A brand-new customer.** No history: 600. That is the Fair band.

**A good customer.** One loan fully paid off, with 4 installments paid on time, plus a current loan of ₦400,000 with 2 installments paid on time:

| | Points |
| --- | --- |
| Starting score | 600 |
| 6 installments paid on time | +60 |
| 1 loan paid off | +25 |
| ₦400,000 is 20% of the limit | +10 |
| **Score** | **695 (Good)** |

**A struggling customer.** 3 installments on time, 1 paid late, 1 overdue now, and a loan of ₦1,200,000 (60% of the limit):

| | Points |
| --- | --- |
| Starting score | 600 |
| 3 installments on time | +30 |
| 1 installment paid late | -15 |
| 1 installment overdue now | -40 |
| 60% of the limit in use | -20 |
| **Score** | **555 (Poor)** |

### Things that are good to know

- **The best possible score is 835**, not 850: 600 + 150 (on time) + 75 (paid-off loans) + 10 (small borrowing).
- **Being overdue hurts twice, in different ways.** While an installment is overdue it costs -40. Once it is finally paid, the -40 goes away but it becomes "paid late", which costs -15 for good. It also carries the late fee (section 5).
- **Paying early is fine.** An installment paid before its due date counts as on time.
- **A late payment is remembered.** The -15 for a late installment never goes away.
- **Paying off a defaulted loan.** If a defaulted loan is later paid in full, the system counts it as paid off. The -150 goes away and +25 is added, but the late installments still count against the customer.

---

## 3. The four bands

The score puts the customer in a band, like a school grade. **The band, not the exact score, decides what they are allowed.**

| Band | Score | Largest single loan | Loans at once | Tier | What the system advises |
| --- | --- | --- | --- | --- | --- |
| **Excellent** | 750 to 850 | ₦1,000,000 | 3 | Premium | **Lend** |
| **Good** | 670 to 749 | ₦600,000 | 3 | Premium | **Lend** |
| **Fair** | 580 to 669 | ₦300,000 | 2 | Standard | **Review** (the lender takes a closer look) |
| **Poor** | 300 to 579 | none | 0 | none | **Decline** (cannot borrow) |

---

## 4. The rules for getting a loan

### Step 1: the customer applies
The customer picks only an **amount** and a **number of months** (1 to 60). The interest rate is set by the lender (20% a year to start). A customer can only have **one application waiting** at a time. Before applying they must tick a box saying they have read and understood the rules shown above the Apply button, and the day they ticked it is saved with the application.

### Step 2: the system checks the rules
If **any** of these is true, the application is **declined straight away** and the customer is told why:

1. There is an **overdue installment** on one of their loans. It must be paid first.
2. They have a **defaulted loan** that has not been paid off.
3. Their score is in the **Poor** band.
4. They already have the **most loans** their band allows (Fair: 2, Good and Excellent: 3).
5. The amount is **more than their band's largest single loan**.
6. The amount would take their total borrowed **over ₦2,000,000**.
7. The **bank does not have enough money** to lend that amount.

### Step 3: the lender decides
If no rule is broken, the application waits for the lender (admin). The system **advises** (Lend for Excellent and Good, Review for Fair), but **nothing is lent until the lender approves it**. When the lender approves, the rules are **checked again**, in case something changed while the application waited.

### How much can a customer borrow?
The smallest of these three amounts:
- their band's largest single loan,
- what is left under the ₦2,000,000 total limit,
- what money the bank has available.

---

## 5. Interest and fees

### Interest
Interest is simple: **loan amount × yearly rate × years**, added to the loan and split into equal monthly installments. For example, ₦1,200 at 12% for 12 months has ₦144 of interest. The total is ₦1,344, so each installment is ₦112.

### Late fee (5%)
If an installment is not paid by its due date, **5% of that installment is added, once**. It is added to what is due on that installment, so paying the installment means paying the fee too. The fee counts as money earned by the bank.

### Interest on late amounts (compound)
A late amount keeps growing. It is the installment plus its late fee, and every month it grows by the loan's yearly rate divided by 12 (1.67% a month at 20% a year). Each month's growth is added before the next month's is worked out, which is what "compound" means. A month counts as 30 days, and part months count.

Example: a ₦100,000 installment that is 75 days late. With the 5% late fee it is ₦105,000, and the interest for 75 days is ₦4,429.84, so the customer owes ₦109,429.84. The interest is added to what is due, so paying the installment pays its fee and interest too, and the figure grows every day until it is paid. If the customer part-pays, the interest grows only on what is still unpaid from that day.

### Warnings and escalation
- **More than 10 days late:** a **Send warning** button appears on the Overdue page. One click (after a check of the exact words) puts the warning on the customer's own account and loans pages, itemised as installment, late fee and interest.
- **5 days after the warning, still unpaid:** the case becomes **Needs escalation**. The lender sees a count in the menu and on the Dashboard, and the customer sees a stronger notice.
- **Lawyer file:** from a case that needs escalation, the lender can prepare a plain-text file for a lawyer (customer details, what is owed and how it adds up, the warning and its dates, every loan's schedule and payments, and the day the customer accepted the rules), then mark the case as referred. The system never contacts anyone itself.
- **When the customer pays everything that was late,** the case closes and the notices go away. If they are late again later, the process starts fresh.
- **Colour bands** on the Overdue page: 1 to 10 days yellow (Just late), 11 to 20 orange (Late), 21 to 30 red (Seriously late), 31 to 60 dark red (Very late), over 60 maroon (Critical).

### Prepayment charge (2%)
This applies when a customer pays **ahead of schedule**.
- Money that pays **installments that are not due yet** is charged 2%, **except the next installment in line**, which is always free, even if paid early.
- The charge is **taken out of the payment**, so paying ₦X always costs exactly ₦X.
- The pay box shows the price of paying the whole loan off today.
- The charge counts as money earned by the bank.

### Default
A loan is marked **defaulted** when **3 installments in a row** are overdue. It is treated as money the bank lost (it shows under "Money lost" on the Finance page). From that day everything still unpaid on the loan is due now, and the interest keeps growing on all of it.

---

## 6. The bank's money

- A new bank starts with **₦5,000,000**.
- When a loan is approved, the amount lent leaves the bank's available money.
- Payments (including interest and fees) go back into it.

**Finance page:** *Money earned* is the interest, late fees, interest on late amounts and prepayment charges paid. *Money lost* is the unpaid money on defaulted loans. *Profit* is earned minus lost.

---

## 7. Where each number lives (if you ever want to change it)

| What | File | Name |
| --- | --- | --- |
| Points for each event, starting score | `src/bank/model/CreditScorer.java` | the `*_POINTS` and `*_COUNT` numbers at the top |
| Points for how much of the limit is used | `src/bank/model/CreditScorer.java` | `limitInUsePoints` |
| Band score ranges, loan limits, loans at once, advice | `src/bank/model/CreditBand.java` | the four band lines |
| Total a customer can owe (₦2,000,000) | `src/bank/model/Bank.java` | `MAX_TOTAL_BORROWED` |
| The bank's starting money | `src/bank/model/Bank.java` | `STARTING_CAPITAL` |
| Late fee (5%) | `src/bank/model/Bank.java` | `LATE_FEE_RATE` |
| Prepayment charge (2%) | `src/bank/model/Bank.java` | `PREPAYMENT_FEE_RATE` |
| Days late before Send warning appears (10) | `src/bank/model/Bank.java` | `WARNING_AFTER_DAYS` |
| Days after the warning before escalation (5) | `src/bank/model/Bank.java` | `ESCALATE_AFTER_DAYS` |
| The interest formula on late amounts | `src/bank/model/Installment.java` | `lateInterestFor` |
| The colour bands for late installments | `src/bank/model/OverdueBand.java` | the five band lines |
| The wording of the warning and escalation messages | `src/bank/model/CollectionMessages.java` | `warning` and `escalation` |
| Missed installments before default (3) | `src/bank/model/Bank.java` | `CONSECUTIVE_MISSED_FOR_DEFAULT` |
| Longest loan (60 months) | `src/bank/model/Bank.java` | `MAX_TERM_MONTHS` |
| The starting interest rate (20%) | `src/bank/model/Bank.java` | `STARTING_INTEREST_RATE` |

**One thing to remember:** the "Read before you apply" box takes its numbers from the system, so it updates itself. But the "How is my score worked out?" box on the website is typed by hand in `web/index.html`. If you change a score rule in the code, change it there too, then run the tests (`run-tests`) to check nothing broke. Have your lawyer approve the wording in `CollectionMessages.java` before real customers see it.
