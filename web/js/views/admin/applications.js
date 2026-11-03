// The Applications screen: the queue of loan applications, and the pop-up where
// the lender reads the system's advice and approves or declines.

import * as api from "../../api.js";
import { money, percent, badge, statusLabel, escapeHtml, shortDate, reasonList } from "../../format.js";
import { openModal, closeModal, showToast, dataChanged, fillTable, show, setMessage } from "../../ui.js";

const filter = document.getElementById("app-filter");
const approveButton = document.getElementById("approve-button");
const declineButton = document.getElementById("decline-button");

let currentApplicationId = null;

export function init() {
    filter.addEventListener("change", load);

    document.getElementById("applications-table").addEventListener("click", handleOpenClick);

    document.getElementById("application-plain").addEventListener("click", handlePlainClick);

    approveButton.addEventListener("click", approve);
    declineButton.addEventListener("click", decline);
}

// any button with data-open-application opens that application
export function handleOpenClick(event) {
    const button = event.target.closest("[data-open-application]");
    if (button) {
        openApplication(button.dataset.openApplication);
    }
}

// one table row, shared with the dashboard's "waiting" list
export function applicationRow(application, withDetails) {
    const id = escapeHtml(application.applicationId);
    const button = `<button class="btn btn-small" data-open-application="${id}">${
        application.status === "PENDING" ? "Review" : "View"}</button>`;

    const who = `${escapeHtml(application.customerName)} <span class="muted">(${escapeHtml(application.customerId)})</span>`;
    const score = `${application.scoreAtSubmission} ${badge(application.bandAtSubmission)}`;

    // a short line written by the system (not the AI), so the list loads at once
    const headline = `<td class="headline">${escapeHtml(application.headline)}</td>`;

    if (!withDetails) {
        return `
            <tr>
                <td>${who}</td>
                <td>${money(application.principal)}</td>
                <td>${score}</td>
                <td>${badge(application.advice)}</td>
                ${headline}
                <td class="actions">${button}</td>
            </tr>`;
    }

    return `
        <tr>
            <td><strong>${id}</strong></td>
            <td>${who}</td>
            <td>${money(application.principal)}</td>
            <td>${application.termMonths} months</td>
            <td>${score}</td>
            <td>${badge(application.advice)}</td>
            ${headline}
            <td>${badge(application.status)}</td>
            <td class="actions">${button}</td>
        </tr>`;
}

export async function load() {
    try {
        const applications = await api.getApplications(filter.value);
        fillTable("applications-table", "applications-empty", applications.map((a) => applicationRow(a, true)));
    } catch (e) {
        showToast(e.message);
    }
}

export async function openApplication(applicationId) {
    currentApplicationId = applicationId;
    setMessage("application-error", "");

    let application;
    try {
        application = await api.getApplication(applicationId);
    } catch (e) {
        showToast(e.message);
        return;
    }

    document.getElementById("application-title").textContent = `Application ${application.applicationId}`;

    let decision = "";
    if (application.status !== "PENDING") {
        const note = application.decisionNote ? ` ${escapeHtml(application.decisionNote)}` : "";
        decision = `<div class="full"><span>Decision</span><span>${badge(application.status)}
            by ${escapeHtml(application.decidedBy)} on ${shortDate(application.decidedOn)}.${note}</span></div>`;
        if (application.loanId) {
            decision += `<div><span>Loan</span><span>${escapeHtml(application.loanId)}</span></div>`;
        }
    }

    document.getElementById("application-summary").innerHTML = `
        <div><span>Customer</span><span>${escapeHtml(application.customerName)} (${escapeHtml(application.customerId)})</span></div>
        <div><span>Status</span><span>${badge(application.status)}</span></div>
        <div><span>Amount</span><span><strong>${money(application.principal)}</strong></span></div>
        <div><span>Term</span><span>${application.termMonths} months</span></div>
        <div><span>Interest rate</span><span>${percent(application.interestPercent)} per year</span></div>
        <div><span>Applied on</span><span>${shortDate(application.submittedOn)}</span></div>
        <div><span>Rules accepted</span><span>${application.rulesAcceptedOn ? shortDate(application.rulesAcceptedOn) : "Not recorded"}</span></div>
        ${decision}`;

    // the system's plain summary shows at once; the AI's wording replaces it if and when it arrives
    startPlain(application.summary, application.aiAvailable);
    if (application.aiAvailable) {
        loadAiSummary(applicationId);
    }

    document.getElementById("application-advice").innerHTML = `
        <div class="advice-head">System advice: ${badge(application.advice)}
            <span class="muted">Score when applied: ${application.scoreAtSubmission} (${statusLabel(application.bandAtSubmission)} band)</span></div>
        ${reasonList(application.reasons)}`;

    const factors = application.scoreNow.factors.map((factor) => `
        <tr>
            <td>${escapeHtml(factor.label)}</td>
            <td class="num">${factor.points > 0 && factor.label !== "Starting score" ? "+" : ""}${factor.points}</td>
        </tr>`);
    factors.push(`
        <tr class="total">
            <td>Score now (${statusLabel(application.scoreNow.band)} band)</td>
            <td class="num">${application.scoreNow.score}</td>
        </tr>`);
    document.getElementById("application-factors").innerHTML = factors.join("");

    // the decision controls only appear while it is waiting
    const pending = application.status === "PENDING";
    show("application-decision", pending);
    show("approve-button", pending);
    show("decline-button", pending);
    document.getElementById("approve-rate").value = application.interestPercent;
    document.getElementById("decline-note").value = "";

    openModal("application-modal");
}

// ---------- The plain summary ("In plain words") ----------
//
// Two versions of the same four parts: the one the system wrote (shown at once) and the
// AI's simpler wording (swapped in when it arrives). The AI's version looks different
// (blue panel, "AI" badge) so it is obvious what the AI did, and a button flips back to
// the system's original so the two can be compared.

let plain = null; // { system, ai, view: "system" | "ai", status: "waiting" | "off" | "done" | "failed", note }

function startPlain(system, aiAvailable) {
    plain = { system, ai: null, view: "system", status: aiAvailable ? "waiting" : "off", note: "" };
    drawPlain();
}

function drawPlain() {
    const showingAi = plain.view === "ai" && plain.ai !== null;
    const summary = showingAi ? plain.ai : plain.system;

    const rows = [
        ["Facts", summary.facts],
        ["Looks good", summary.looksGood],
        ["Check", summary.check],
        ["Advice", summary.advice],
    ].map(([label, text]) => `<div><dt>${label}</dt><dd>${escapeHtml(text)}</dd></div>`);

    let tag;
    if (showingAi) {
        tag = "Reworded by AI in simpler words. The advice is still the system's own.";
    } else if (plain.status === "waiting") {
        tag = "AI is making this simpler...";
    } else if (plain.status === "done") {
        tag = "The system's original wording";
    } else if (plain.status === "failed") {
        tag = plain.note ? `Written by the system. ${plain.note}` : "Written by the system";
    } else {
        tag = "Written by the system";
    }

    const toggle = plain.ai
        ? `<button type="button" class="link-button plain-toggle" data-plain-toggle>${
            showingAi ? "Show the system's original" : "Show the AI's simpler version"}</button>`
        : "";

    const box = document.getElementById("application-plain");
    box.className = "plain-summary" + (showingAi ? " is-ai" : "") + (plain.status === "waiting" ? " is-waiting" : "");
    box.innerHTML = `
        <div class="plain-head">
            <h3>${showingAi ? '<span class="ai-badge">&#10022; AI</span>' : ""}In plain words</h3>
            <span class="source-tag ${showingAi ? "source-ai" : ""}">${escapeHtml(tag)}</span>
        </div>
        <dl class="plain-list">${rows.join("")}</dl>
        ${toggle}`;
}

// the "show the system's original / the AI's version" button
function handlePlainClick(event) {
    if (plain && plain.ai && event.target.closest("[data-plain-toggle]")) {
        plain.view = plain.view === "ai" ? "system" : "ai";
        drawPlain();
    }
}

// Asks the server for the AI's version. It can take up to a minute, so the pop-up
// is already open with the system's version; if the admin has moved on to another
// application by the time the answer comes back, the answer is ignored.
async function loadAiSummary(applicationId) {
    try {
        const aiSummary = await api.getAiSummary(applicationId);
        if (currentApplicationId === applicationId && plain) {
            plain.ai = aiSummary;
            plain.view = "ai";
            plain.status = "done";
            drawPlain();
        }
    } catch (e) {
        if (currentApplicationId === applicationId && plain) {
            plain.status = "failed";
            plain.note = e.message;
            drawPlain();
        }
    }
}

async function approve() {
    const percentText = document.getElementById("approve-rate").value;

    // the page shows a percentage (20), the server wants a decimal (0.2)
    const rate = String(Number((Number(percentText) / 100).toFixed(6)));

    await decide(() => api.approveApplication(currentApplicationId, rate), "Loan approved");
}

async function decline() {
    const note = document.getElementById("decline-note").value;

    await decide(() => api.declineApplication(currentApplicationId, note), "Application declined");
}

async function decide(action, message) {
    setMessage("application-error", "");
    approveButton.disabled = true;
    declineButton.disabled = true;

    try {
        await action();
        closeModal("application-modal");
        showToast(`${message}.`);
        dataChanged();
    } catch (e) {
        setMessage("application-error", e.message);
    } finally {
        approveButton.disabled = false;
        declineButton.disabled = false;
    }
}
