// The Overdue screen (admin).
//
//   Needs action      customers who need a warning, or whose case needs escalation
//   Warned and watched   everyone else who is late
//   Overdue installments  every late installment, coloured by how late it is
//
// "Send warning" puts the warning on the customer's own page. "Prepare lawyer file" shows
// everything your lawyer needs; from there you can mark the case as referred.

import * as api from "../../api.js";
import { money, escapeHtml, shortDate } from "../../format.js";
import { showToast, fillTable, openModal, closeModal, dataChanged, setMessage, show } from "../../ui.js";

let warningCustomerId = null;
let lawyerCustomerId = null;

export function init() {
    document.getElementById("view-overdue").addEventListener("click", (event) => {
        const warningButton = event.target.closest("[data-send-warning]");
        if (warningButton) {
            openWarning(warningButton.dataset.sendWarning);
        }

        const lawyerButton = event.target.closest("[data-lawyer-file]");
        if (lawyerButton) {
            openLawyerFile(lawyerButton.dataset.lawyerFile);
        }
    });

    document.getElementById("warning-send").addEventListener("click", sendWarning);
    document.getElementById("lawyer-copy").addEventListener("click", copyLawyerFile);
    document.getElementById("lawyer-refer-button").addEventListener("click", refer);
}

// "18 days, Late" with the band's colour. The words are there too, so the colour is never the only clue.
export function bandPill(days, band, label) {
    const unit = days === 1 ? "day" : "days";
    return `<span class="band band-${escapeHtml(band.toLowerCase())}">${days} ${unit} \u00b7 ${escapeHtml(label)}</span>`;
}

function customerCell(customer) {
    return `${escapeHtml(customer.name)} <span class="muted">(${escapeHtml(customer.customerId)})</span>`;
}

function actionRow(customer) {
    const id = escapeHtml(customer.customerId);
    let what;
    let button;

    if (customer.stage === "SEND_WARNING") {
        what = "More than 10 days late. Send a warning.";
        button = `<button class="btn btn-small btn-primary" data-send-warning="${id}">Send warning</button>`;
    } else {
        what = `<strong>Needs escalation.</strong> Warned ${shortDate(customer.warningSentOn)}, not paid by ${shortDate(customer.deadline)}.`;
        button = `<button class="btn btn-small btn-primary" data-lawyer-file="${id}">Prepare lawyer file</button>`;
    }

    return `
        <tr>
            <td>${customerCell(customer)}</td>
            <td>${bandPill(customer.oldestDaysOverdue, customer.band, customer.bandLabel)}</td>
            <td class="num"><strong>${money(customer.owed.total)}</strong></td>
            <td>${what}</td>
            <td class="actions">${button}</td>
        </tr>`;
}

function waitingRow(customer) {
    const id = escapeHtml(customer.customerId);
    let where;
    let button = "";

    if (customer.stage === "WARNED") {
        where = `Warning sent ${shortDate(customer.warningSentOn)}. Has until ${shortDate(customer.deadline)} to pay.`;
    } else if (customer.stage === "REFERRED") {
        where = `Referred to your lawyer on ${shortDate(customer.referredOn)}.`;
        button = `<button class="btn btn-small" data-lawyer-file="${id}">Lawyer file</button>`;
    } else {
        where = "Watching. The Send warning button appears after 10 days.";
    }

    return `
        <tr>
            <td>${customerCell(customer)}</td>
            <td>${bandPill(customer.oldestDaysOverdue, customer.band, customer.bandLabel)}</td>
            <td class="num">${money(customer.owed.total)}</td>
            <td>${where}</td>
            <td class="actions">${button}</td>
        </tr>`;
}

export async function load() {
    try {
        const [collections, overdue] = await Promise.all([api.getCollections(), api.getOverdue()]);

        const needAction = collections.customers.filter(
            (c) => c.stage === "SEND_WARNING" || c.stage === "NEEDS_ESCALATION");
        const others = collections.customers.filter(
            (c) => c.stage !== "SEND_WARNING" && c.stage !== "NEEDS_ESCALATION");

        // escalations first, then warnings to send
        needAction.sort((a, b) => (a.stage === b.stage ? 0 : a.stage === "NEEDS_ESCALATION" ? -1 : 1));

        fillTable("action-table", "action-empty", needAction.map(actionRow));
        fillTable("waiting-table", "waiting-empty", others.map(waitingRow));

        // the longest overdue first
        overdue.sort((a, b) => b.daysOverdue - a.daysOverdue);

        const rows = overdue.map((entry) => {
            const extras = Number(entry.lateFee) + Number(entry.lateInterest);

            return `
            <tr>
                <td>${escapeHtml(entry.customerName)}</td>
                <td>${escapeHtml(entry.loanId)}</td>
                <td>${shortDate(entry.dueDate)}</td>
                <td>${bandPill(entry.daysOverdue, entry.band, entry.bandLabel)}</td>
                <td class="num">${money(entry.baseAmount)}</td>
                <td class="num">${money(extras)}</td>
                <td class="num"><strong>${money(entry.owedToday)}</strong></td>
            </tr>`;
        });
        fillTable("overdue-table", "overdue-empty", rows);
    } catch (e) {
        showToast(e.message);
    }
}

// ---------- Send warning ----------

async function openWarning(customerId) {
    warningCustomerId = customerId;
    setMessage("warning-error", "");

    try {
        const preview = await api.getWarningPreview(customerId);

        document.getElementById("warning-title").textContent = "Send warning";
        document.getElementById("warning-info").textContent =
            `This is exactly what the customer will read on their account and loans pages. ` +
            `They have until ${shortDate(preview.deadline)} to pay.`;
        document.getElementById("warning-message").textContent = preview.message;

        openModal("warning-modal");
    } catch (e) {
        showToast(e.message);
    }
}

async function sendWarning() {
    setMessage("warning-error", "");

    try {
        const sent = await api.sendWarning(warningCustomerId);
        closeModal("warning-modal");
        showToast(`Warning sent to ${sent.name}. They have until ${shortDate(sent.deadline)} to pay.`);
        dataChanged();
    } catch (e) {
        setMessage("warning-error", e.message);
    }
}

// ---------- Lawyer file ----------

async function openLawyerFile(customerId) {
    lawyerCustomerId = customerId;
    setMessage("lawyer-error", "");
    document.getElementById("lawyer-note").value = "";

    try {
        const file = await api.getLawyerFile(customerId);

        document.getElementById("lawyer-title").textContent = `Lawyer file: ${file.name}`;
        document.getElementById("lawyer-text").textContent = file.text;

        show("lawyer-refer", file.canRefer);

        const info = document.getElementById("lawyer-referred-info");
        if (file.referredOn) {
            info.textContent = `Referred to your lawyer on ${shortDate(file.referredOn)}.` +
                (file.referredNote ? ` Note: ${file.referredNote}` : "");
        }
        show("lawyer-referred-info", Boolean(file.referredOn));

        openModal("lawyer-modal");
    } catch (e) {
        showToast(e.message);
    }
}

async function copyLawyerFile() {
    const text = document.getElementById("lawyer-text").textContent;

    try {
        await navigator.clipboard.writeText(text);
        showToast("The lawyer file is copied.");
    } catch (e) {
        // some browsers refuse: select the text so it can be copied by hand
        const range = document.createRange();
        range.selectNodeContents(document.getElementById("lawyer-text"));
        window.getSelection().removeAllRanges();
        window.getSelection().addRange(range);
        showToast("Press Ctrl+C to copy the selected text.");
    }
}

async function refer() {
    setMessage("lawyer-error", "");

    try {
        await api.referToLawyer(lawyerCustomerId, document.getElementById("lawyer-note").value);
        closeModal("lawyer-modal");
        showToast("Marked as referred to your lawyer.");
        dataChanged();
    } catch (e) {
        setMessage("lawyer-error", e.message);
    }
}
