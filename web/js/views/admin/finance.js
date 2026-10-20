// The Finance screen (admin): three numbers and a month-by-month table.
//
//   Money earned = the interest, late fees and prepayment charges customers have paid
//   Money lost   = money lent on loans that were never repaid
//   Profit       = earned minus lost

import * as api from "../../api.js";
import { money, monthLabel } from "../../format.js";
import { showToast } from "../../ui.js";

export function init() {
    // nothing to set up: this screen only shows numbers
}

export async function load() {
    let finance;
    try {
        finance = await api.getFinance();
    } catch (e) {
        showToast(e.message);
        return;
    }

    document.getElementById("fin-revenue").textContent = money(finance.revenue);
    document.getElementById("fin-revenue-note").textContent =
        `Interest ${money(finance.interest)}, late fees ${money(finance.lateFees)}, prepayment charges ${money(finance.prepaymentFees)}`;
    document.getElementById("fin-lost").textContent = money(finance.badDebt);
    document.getElementById("fin-profit").textContent = money(finance.profit);

    // newest month first
    const rows = [...finance.months].reverse().map((month) => `
        <tr>
            <td><strong>${monthLabel(month.month)}</strong></td>
            <td>${money(month.earned)}</td>
            <td>${money(month.badDebt)}</td>
            <td><strong>${money(month.profit)}</strong></td>
        </tr>`);
    document.getElementById("finance-months").innerHTML = rows.join("");
}
