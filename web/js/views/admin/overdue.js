// The Overdue screen (admin).
//
//   Needs action      customers who need a warning, or whose case needs escalation
//   Warned and watched   everyone else who is late
//   Overdue installments  every late installment, coloured by how late it is
//
// "Send warning" puts the warning on the customer's own page.
//
// The "Prepare lawyer file" button and its pop-up are switched off for now. The server
// routes (GET /api/collections/{id}/lawyer-file and POST .../refer), LawyerFile.java and
// api.js's getLawyerFile / referToLawyer are still there, so it can be brought back.

import * as api from "../../api.js";
import { money, escapeHtml, shortDate } from "../../format.js";
import { showToast, fillTable, openModal, closeModal, dataChanged, setMessage } from "../../ui.js";

let warningCustomerId = null;

export function init() {
    document.getElementById("view-overdue").addEventListener("click", (event) => {
        const warningButton = event.target.closest("[data-send-warning]");
        if (warningButton) {
            openWarning(warningButton.dataset.sendWarning);
        }
    });

    document.getElementById("warning-send").addEventListener("click", sendWarning);
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
    let button = "";

    if (customer.stage === "SEND_WARNING") {
        what = "More than 10 days late. Send a warning.";
        button = `<button class="btn btn-small btn-primary" data-send-warning="${id}">Send warning</button>`;
    } else {
        what = `<strong>Needs escalation.</strong> Warned ${shortDate(customer.warningSentOn)}, not paid by ${shortDate(customer.deadline)}.`;
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
    let where;
    const button = "";

    if (customer.stage === "WARNED") {
        where = `Warning sent ${shortDate(customer.warningSentOn)}. Has until ${shortDate(customer.deadline)} to pay.`;
    } else if (customer.stage === "REFERRED") {
        where = `Referred to your lawyer on ${shortDate(customer.referredOn)}.`;
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
