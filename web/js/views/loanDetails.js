// The "Loan details" pop-up, used by both the lender (admin) and the customer.
// It shows the loan summary, its installments (with an All / Paid / Unpaid
// filter) and every payment made on it.
//
// who is "admin" or "user": the admin looks loans up by ID, while the customer's
// routes only ever return that customer's own loans.

import * as api from "../api.js";
import { money, percent, badge, escapeHtml, shortDate } from "../format.js";
import { openModal, closeModal, showToast, fillTable } from "../ui.js";

const filterSelect = document.getElementById("details-filter");
const payButton = document.getElementById("details-pay");

let currentLoanId = null;
let currentWho = "admin";
let onPay = null;

export function init() {
    filterSelect.addEventListener("change", refresh);

    payButton.addEventListener("click", () => {
        closeModal("loan-details-modal");
        if (onPay) {
            onPay(currentLoanId);
        }
    });
}

// onPayCallback is called with the loan ID when the "pay" button is pressed
export async function openLoanDetails(loanId, who, onPayCallback) {
    currentLoanId = loanId;
    currentWho = who;
    onPay = onPayCallback;
    filterSelect.value = "all";

    // customers just see all their installments; the filter is for the lender
    filterSelect.classList.toggle("hidden", who !== "admin");

    const loaded = await refresh();
    if (loaded) {
        openModal("loan-details-modal");
    }
}

// A short note for a payment: only worth saying when the lender typed it in
// (for example cash paid at the counter).
function paymentNote(payment) {
    if (payment.byAdmin) {
        return currentWho === "admin" ? "Recorded by you" : "Recorded by the lender";
    }
    return "";
}

async function refresh() {
    let loan;
    try {
        if (currentWho === "admin") {
            loan = await api.getLoan(currentLoanId, filterSelect.value);
        } else {
            loan = await api.getMyLoan(currentLoanId, filterSelect.value);
        }
    } catch (e) {
        showToast(e.message);
        return false;
    }

    document.getElementById("details-title").textContent = `Loan ${loan.loanId}`;

    const customerRow = currentWho === "admin"
        ? `<div><span>Customer</span><span>${escapeHtml(loan.customerName)} (${escapeHtml(loan.customerId)})</span></div>`
        : "";

    document.getElementById("details-summary").innerHTML = `
        ${customerRow}
        <div><span>Status</span><span>${badge(loan.status)}</span></div>
        <div><span>Borrowed</span><span>${money(loan.principal)}</span></div>
        <div><span>Interest rate</span><span>${percent(loan.interestPercent)} per year</span></div>
        <div><span>Length</span><span>${loan.termMonths} months</span></div>
        <div><span>Started</span><span>${shortDate(loan.disbursementDate)}</span></div>
        <div><span>Total to repay</span><span>${money(loan.totalRepayable)}</span></div>
        ${Number(loan.lateFees) > 0 ? `<div><span>Late fees added</span><span>${money(loan.lateFees)}</span></div>` : ""}
        ${Number(loan.lateInterest) > 0 ? `<div><span>Interest on late amounts</span><span>${money(loan.lateInterest)}</span></div>` : ""}
        <div><span>Still owed</span><span><strong>${money(loan.outstanding)}</strong></span></div>`;

    const installmentRows = loan.installments.map((installment) => `
        <tr>
            <td>${shortDate(installment.dueDate)}</td>
            <td>${money(installment.amountDue)}${
                Number(installment.lateFee) > 0 || Number(installment.lateInterest) > 0
                    ? `<br><span class="muted">installment ${money(installment.baseAmount)} + late fee ${money(installment.lateFee)} + interest ${money(installment.lateInterest)}</span>`
                    : ""}</td>
            <td>${money(installment.amountPaid)}</td>
            <td>${badge(installment.status)}</td>
        </tr>`);
    fillTable("details-installments", "details-empty", installmentRows);

    // the Note column only appears when some payment has a note to show
    const anyNote = loan.payments.some((payment) => paymentNote(payment) !== "");
    document.getElementById("details-note-header").classList.toggle("hidden", !anyNote);

    const paymentRows = loan.payments.map((payment) => `
        <tr>
            <td>${shortDate(payment.date)}</td>
            <td>${money(payment.amount)}${
                Number(payment.prepaymentFee) > 0
                    ? `<br><span class="muted">includes ${money(payment.prepaymentFee)} prepayment charge</span>`
                    : ""}</td>
            ${anyNote ? `<td>${paymentNote(payment)}</td>` : ""}
        </tr>`);
    fillTable("details-payments", "details-payments-empty", paymentRows);

    // nothing left to pay on a paid-off loan
    payButton.textContent = currentWho === "admin" ? "Record a payment" : "Pay";
    payButton.classList.toggle("hidden", loan.status === "PAID_OFF");
    return true;
}
