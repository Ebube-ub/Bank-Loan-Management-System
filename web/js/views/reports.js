// The Reports screen: loan status counts, money totals, top borrowers
// and overdue installments.

import * as api from "../api.js";
import { money, escapeHtml } from "../format.js";
import { showToast, fillTable } from "../ui.js";

const limitInput = document.getElementById("top-limit");

export function init() {
    limitInput.addEventListener("change", loadTopBorrowers);
}

export async function load() {
    try {
        const [summary, overdue] = await Promise.all([api.getSummary(), api.getOverdue()]);

        document.getElementById("report-active").textContent = summary.active;
        document.getElementById("report-paid-off").textContent = summary.paidOff;
        document.getElementById("report-defaulted").textContent = summary.defaulted;
        document.getElementById("report-interest").textContent = money(summary.interestEarned);
        document.getElementById("report-funds").textContent = money(summary.availableFunds);

        const overdueRows = overdue.map((entry) => `
            <tr>
                <td>${escapeHtml(entry.customerName)}</td>
                <td>${escapeHtml(entry.loanId)}</td>
                <td>${entry.dueDate}</td>
                <td>${entry.daysOverdue}</td>
            </tr>`);
        fillTable("overdue-table", "overdue-empty", overdueRows);
    } catch (e) {
        showToast(e.message);
    }

    loadTopBorrowers();
}

async function loadTopBorrowers() {
    let limit = Number(limitInput.value);
    if (!Number.isInteger(limit) || limit < 1) {
        limit = 5;
        limitInput.value = 5;
    }

    try {
        const borrowers = await api.getTopBorrowers(limit);

        const rows = borrowers.map((borrower, index) => `
            <tr>
                <td>${index + 1}</td>
                <td>${escapeHtml(borrower.name)} <span class="muted">(${escapeHtml(borrower.customerId)})</span></td>
                <td>${money(borrower.totalBorrowed)}</td>
            </tr>`);
        fillTable("top-borrowers", "top-borrowers-empty", rows);
    } catch (e) {
        showToast(e.message);
    }
}
