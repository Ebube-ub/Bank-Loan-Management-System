// The Payments screen (admin): record a payment a customer made to the lender
// (for example cash at the counter), and see which loans still have money owed.

import * as api from "../../api.js";
import { money, badge, escapeHtml } from "../../format.js";
import { showToast, dataChanged, fillTable, setMessage } from "../../ui.js";

const form = document.getElementById("payment-form");
const loanIdInput = document.getElementById("payment-loan-id");
const result = document.getElementById("payment-result");

export function init() {
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        setMessage("payment-error", "");
        result.classList.add("hidden");

        const loanId = loanIdInput.value.trim();
        const amount = form.amount.value;

        try {
            const loan = await api.recordPayment(loanId, amount);

            let html = `
                Payment of <strong>${money(amount)}</strong> recorded on loan <strong>${escapeHtml(loan.loanId)}</strong>
                for ${escapeHtml(loan.customerName)}.<br>
                Status: ${badge(loan.status)}<br>
                Outstanding balance: <strong>${money(loan.outstanding)}</strong>`;

            if (Number(loan.prepaymentFee) > 0) {
                html += `<br><span class="muted">${money(loan.prepaymentFee)} of it was a prepayment charge, for paying installments before they are due.</span>`;
            }

            if (Number(loan.overpayment) > 0) {
                html += `<br><span class="muted">${money(loan.overpayment)} was not used because the loan is now fully paid.</span>`;
            }

            result.innerHTML = html;
            result.classList.remove("hidden");
            form.amount.value = "";
            showToast(`Payment recorded on ${loan.loanId}.`);
            dataChanged();
        } catch (e) {
            setMessage("payment-error", e.message);
        }
    });

    // "Record payment" buttons in the table fill in the loan ID for you
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
                <td class="actions"><button class="btn btn-small" data-pay-loan="${escapeHtml(loan.loanId)}">Record payment</button></td>
            </tr>`);
        fillTable("payments-loans", "payments-empty", rows);
    } catch (e) {
        showToast(e.message);
    }
}

// used by the loan details pop-up's "Record a payment" button
export function prefillLoan(loanId) {
    loanIdInput.value = loanId;
    setMessage("payment-error", "");
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
