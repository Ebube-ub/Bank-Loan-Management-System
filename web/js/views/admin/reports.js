// The Reports screen (admin): loan status counts, money totals, three charts and top borrowers.

import * as api from "../../api.js";
import { donut, columns } from "../../charts.js";
import { money, escapeHtml } from "../../format.js";
import { showToast, fillTable } from "../../ui.js";

const limitInput = document.getElementById("top-limit");

export function init() {
    limitInput.addEventListener("change", loadTopBorrowers);
}

export async function load() {
    try {
        const summary = await api.getSummary();

        document.getElementById("report-active").textContent = summary.active;
        document.getElementById("report-paid-off").textContent = summary.paidOff;
        document.getElementById("report-defaulted").textContent = summary.defaulted;
        document.getElementById("report-interest").textContent = money(summary.interestEarned);
        document.getElementById("report-funds").textContent = money(summary.availableFunds);
    } catch (e) {
        showToast(e.message);
    }

    loadCharts();
    loadTopBorrowers();
}

// the colour of each credit band's slice, matching how the bands are coloured elsewhere
const BAND_COLORS = { EXCELLENT: "c-green", GOOD: "c-teal", FAIR: "c-amber", POOR: "c-red" };

async function loadCharts() {
    try {
        const analytics = await api.getAnalytics();

        donut(document.getElementById("chart-loan-status"), [
            { label: "Active", value: analytics.loanStatus.active, color: "c-blue" },
            { label: "Paid off", value: analytics.loanStatus.paidOff, color: "c-green" },
            { label: "Defaulted", value: analytics.loanStatus.defaulted, color: "c-red" },
        ], { centre: "loans", empty: "No loans yet." });

        donut(document.getElementById("chart-credit-bands"),
            analytics.creditBands.map((band) => ({
                label: band.label,
                value: band.customers,
                color: BAND_COLORS[band.band],
            })),
            { centre: "customers", empty: "No customers yet." });

        columns(document.getElementById("chart-months"), analytics.months, [
            { key: "lent", label: "Lent", color: "c-blue" },
            { key: "earned", label: "Earned", color: "c-green" },
        ], "Nothing lent or earned yet.");
    } catch (e) {
        showToast(e.message);
    }
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
