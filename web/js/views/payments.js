// The Payments screen: a payment form and a list of loans that still
// have money owed on them.

import * as api from "../api.js";
import { money, badge, escapeHtml } from "../format.js";
import { showToast, dataChanged, fillTable } from "../ui.js";

const form = document.getElementById("payment-form");
const loanIdInput = document.getElementById("payment-loan-id");
const error = document.getElementById("payment-error");
const result = document.getElementById("payment-result");

export function init() {
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        error.textContent = "";
        result.classList.add("hidden");

        const loanId = loanIdInput.value.trim();
        const amount = form.amount.value;

        try {
            const loan = await api.makePayment(loanId, amount);

            let html = `
                Payment of <strong>${money(amount)}</strong> applied to loan <strong>${escapeHtml(loan.loanId)}</strong>.<br>
                Status: ${badge(loan.status)}<br>
                Outstanding balance: <strong>${money(loan.outstanding)}</strong>`;

            if (Number(loan.overpayment) > 0) {
                html += `<br><span class="muted">${money(loan.overpayment)} was not used because the loan is now fully paid.</span>`;
            }

            result.innerHTML = html;
            result.classList.remove("hidden");
            form.amount.value = "";
            showToast(`Payment applied to ${loan.loanId}.`);
            dataChanged();
        } catch (e) {
            error.textContent = e.message;
        }
    });

    // "Pay" buttons in the table fill in the loan ID for you
    document.getElementById("payments-loans").addEventListener("click", (event) => {
        const button = event.target.closest("[data-pay-loan]");
        if (button) {
            loanIdInput.value = button.dataset.payLoan;
            result.classList.add("hidden");
            form.amount.focus();
        }
    });
}

export async function load() {
    try {
        const loans = await api.getAllLoans();
        const owing = loans.filter((loan) => Number(loan.outstanding) > 0);

        const rows = owing.map((loan) => `
            <tr>
                <td><strong>${escapeHtml(loan.loanId)}</strong></td>
                <td>${escapeHtml(loan.customerName)}</td>
                <td>${money(loan.outstanding)}</td>
                <td>${badge(loan.status)}</td>
                <td class="actions"><button class="btn btn-small" data-pay-loan="${escapeHtml(loan.loanId)}">Pay</button></td>
            </tr>`);
        fillTable("payments-loans", "payments-empty", rows);
    } catch (e) {
        showToast(e.message);
    }
}

// used by the loan details pop-up's "Make a payment" button
export function payLoan(loanId) {
    loanIdInput.value = loanId;
    error.textContent = "";
    result.classList.add("hidden");

    if (location.hash === "#payments") {
        form.amount.focus();
    } else {
        // the screen only becomes visible after the hash changes,
        // and a hidden box can't be focused
        window.addEventListener("hashchange", () => form.amount.focus(), { once: true });
        location.hash = "#payments";
    }
}
