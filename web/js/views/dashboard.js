// The Dashboard: four number cards and the five newest loans.

import * as api from "../api.js";
import { money } from "../format.js";
import { showToast, fillTable } from "../ui.js";
import { loanRow, listenForViewButtons } from "./loans.js";

export function init() {
    listenForViewButtons("dash-loans");

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
        // ask for all three at the same time instead of one after another
        const [summary, overdue, loans] = await Promise.all([
            api.getSummary(),
            api.getOverdue(),
            api.getAllLoans(),
        ]);

        document.getElementById("dash-active").textContent = summary.active;
        document.getElementById("dash-overdue").textContent = overdue.length;
        document.getElementById("dash-interest").textContent = money(summary.interestEarned);
        document.getElementById("dash-funds").textContent = money(summary.availableFunds);

        // loans come back oldest first, so take the last 5 and flip them
        const newest = loans.slice(-5).reverse();
        fillTable("dash-loans", "dash-loans-empty", newest.map(loanRow));
    } catch (e) {
        showToast(e.message);
    }
}
