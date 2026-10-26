// The admin Dashboard: number cards, the applications waiting for a decision,
// and the five newest loans.

import * as api from "../../api.js";
import { money, escapeHtml, shortDate } from "../../format.js";
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

// ---------- The watch list ----------
//
// The system picks the names and writes a sentence for each, so the list shows at once.
// The AI then rewords the sentences; that can take a minute, so it is asked for
// separately and the page never waits for it. If it fails, the system's sentences stay.

const watchSourceTags = {
    waiting: "AI is making these simpler...",
    off: "Sentences by the system",
    failed: "Sentences by the system",
    done: "Sentences written by AI (marked in blue). The names and reasons are the system's own.",
};

let watchRun = 0; // a newer load makes an older, slower answer be ignored

function watchItem(entry, aiSentence) {
    // a sentence the AI wrote gets the blue AI look; otherwise it is the system's own
    const badge = aiSentence ? '<span class="ai-badge">&#10022; AI</span>' : "";

    return `
        <li class="watch-item ${aiSentence ? "is-ai" : ""}">
            <div class="watch-top">
                <strong>${escapeHtml(entry.name)}</strong>
                <span class="muted">(${escapeHtml(entry.customerId)})</span>
                <span class="reason-tag">${escapeHtml(entry.reasonLabel)}</span>
            </div>
            <p class="watch-sentence">${badge}${escapeHtml(aiSentence || entry.sentence)}</p>
            <span class="muted">Next installment ${money(entry.nextAmount)}, due ${shortDate(entry.nextDue)}</span>
        </li>`;
}

function showWatchlist(entries, sentences, state, note) {
    document.getElementById("watch-list").innerHTML =
        entries.map((entry) => watchItem(entry, sentences[entry.customerId])).join("");
    document.getElementById("watch-empty").classList.toggle("hidden", entries.length > 0);

    const tag = document.getElementById("watch-source");
    tag.classList.toggle("hidden", entries.length === 0);
    tag.classList.toggle("source-ai", state === "done");
    tag.classList.toggle("is-working", state === "waiting");
    tag.textContent = state === "failed" && note
        ? `${watchSourceTags[state]}. ${note}`
        : watchSourceTags[state];
}

async function loadWatchlist() {
    const run = ++watchRun;

    let list;
    try {
        list = await api.getWatchlist();
    } catch (e) {
        showToast(e.message);
        return;
    }
    if (run !== watchRun) {
        return;
    }

    showWatchlist(list.entries, {}, list.aiAvailable ? "waiting" : "off");
    if (list.entries.length === 0 || !list.aiAvailable) {
        return;
    }

    try {
        const reworded = await api.getWatchlistAi();
        if (run === watchRun) {
            showWatchlist(list.entries, reworded.sentences, "done");
        }
    } catch (e) {
        if (run === watchRun) {
            showWatchlist(list.entries, {}, "failed", e.message);
        }
    }
}

export async function load() {
    loadWatchlist(); // on its own: the rest of the Dashboard never waits for it

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
