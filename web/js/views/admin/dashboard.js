// The admin Dashboard: number cards, the applications waiting for a decision,
// and the five newest loans.

import * as api from "../../api.js";
import { money } from "../../format.js";
import { showToast, fillTable } from "../../ui.js";
import { loanRow, listenForViewButtons } from "./loans.js";
import { applicationRow, handleOpenClick } from "./applications.js";

export function init() {
    listenForViewButtons("dash-loans");
    document.getElementById("dash-applications").addEventListener("click", handleOpenClick);

    // e.g. "Monday, 12 October 2026" in the welcome banner
    document.getElementById("dash-date").textContent = new Date().toLocaleDateString("en-GB", {
        weekday: "long",
        day: "numeric",
        month: "long",
        year: "numeric",
    });
}

export async function load() {
    try {
        // ask for everything at the same time instead of one after another
        const [summary, overdue, loans, waiting] = await Promise.all([
            api.getSummary(),
            api.getOverdue(),
            api.getAllLoans(),
            api.getApplications("PENDING"),
        ]);

        document.getElementById("dash-active").textContent = summary.active;
        document.getElementById("dash-pending").textContent = summary.pendingApplications;
        document.getElementById("dash-overdue").textContent = overdue.length;

        // under the count: how many customers need you to act
        document.getElementById("dash-action-note").textContent =
            `${summary.warningsToSend} to warn, ${summary.needsEscalation} to escalate`;
        document.getElementById("dash-interest").textContent = money(summary.interestEarned);
        document.getElementById("dash-funds").textContent = money(summary.availableFunds);

        for (const id of ["dash-interest", "dash-funds"]) {
            const value = document.getElementById(id);
            value.classList.toggle("long", value.textContent.length > 11);
        }

        fillTable("dash-applications", "dash-applications-empty",
            waiting.slice(0, 5).map((application) => applicationRow(application, false)));

        // loans come back oldest first, so take the last 5 and flip them
        const newest = loans.slice(-5).reverse();
        fillTable("dash-loans", "dash-loans-empty", newest.map(loanRow));
    } catch (e) {
        showToast(e.message);
    }
}
