// "My loans" (customer): their own loans, the details of each one, and paying.
// Paying is a small pop-up that opens from a loan, so there is no separate screen for it.

import * as api from "../../api.js";
import { money, badge, escapeHtml, shortDate } from "../../format.js";
import { openModal, closeModal, showToast, dataChanged, fillTable, setMessage } from "../../ui.js";
import { openLoanDetails } from "../loanDetails.js";
import { renderRules } from "../../rules.js";
import { renderNotice } from "../../notice.js";

const payForm = document.getElementById("pay-form");
let payingLoanId = null;
let prepaymentPercent = "2"; // replaced with the real figure when the loans are loaded

export function init() {
    document.getElementById("my-loans-table").addEventListener("click", (event) => {
        const viewButton = event.target.closest("[data-view-loan]");
        if (viewButton) {
            // the pay button inside the details opens the same pay pop-up
            openLoanDetails(viewButton.dataset.viewLoan, "user", openPay);
        }

        const payButton = event.target.closest("[data-pay-loan]");
        if (payButton) {
            openPay(payButton.dataset.payLoan);
        }
    });

    payForm.addEventListener("submit", pay);
}

export async function load() {
    try {
        const [loans, summary] = await Promise.all([api.getMyLoans(), api.getMySummary()]);

        renderNotice("notice-loans", summary.notice, false);
        renderRules("rules-loans", summary, "The rules for your loans");
        prepaymentPercent = summary.prepaymentFeePercent;

        const rows = loans.map((loan) => {
            const id = escapeHtml(loan.loanId);
            const owes = Number(loan.outstanding) > 0;

            return `
                <tr>
                    <td><strong>${id}</strong></td>
                    <td>${money(loan.principal)}</td>
                    <td>${money(loan.outstanding)}${lateSummary(loan.owedToday)}</td>
                    <td>${badge(loan.status)}</td>
                    <td class="actions">
                        <button class="btn btn-small" data-view-loan="${id}">View</button>
                        ${owes ? `<button class="btn btn-small btn-primary" data-pay-loan="${id}">Pay</button>` : ""}
                    </td>
                </tr>`;
        });
        fillTable("my-loans-table", "my-loans-empty", rows);
    } catch (e) {
        showToast(e.message);
    }
}

// "Late now: installment + late fee + interest = total", or nothing when the loan is not late
function lateSummary(owed) {
    if (Number(owed.total) <= 0) {
        return "";
    }

    return `<br><span class="muted">Late now: ${money(owed.installments)} installment + ${money(owed.lateFees)} late fee
        + ${money(owed.lateInterest)} interest = <strong>${money(owed.total)}</strong></span>`;
}

// opens the pay pop-up: the late amount (or the next installment) is already filled in
async function openPay(loanId) {
    payingLoanId = loanId;
    setMessage("pay-error", "");

    let loan;
    try {
        loan = await api.getMyLoan(loanId, "unpaid");
    } catch (e) {
        showToast(e.message);
        return;
    }

    document.getElementById("pay-title").textContent = `Pay on loan ${loan.loanId}`;

    const next = loan.installments[0];
    const late = loan.owedToday;
    let info = `You still owe ${money(loan.outstanding)}.`;
    let suggested = loan.outstanding;

    if (Number(late.total) > 0) {
        // something is late: suggest paying all of it, and show the three lines it is made of
        suggested = late.total;
        info += ` To clear what is late today, pay ${money(late.total)}: ${money(late.installments)} installment
            + ${money(late.lateFees)} late fee + ${money(late.lateInterest)} interest.
            Interest grows every day, so this figure is for today.`;
    } else if (next) {
        suggested = (Number(next.amountDue) - Number(next.amountPaid)).toFixed(2);
        info += ` Your next installment is ${money(suggested)}, due ${shortDate(next.dueDate)}.`;
    }

    // paying the whole loan today costs a little more than what is owed, so say how much
    if (Number(loan.payoffFee) > 0) {
        info += ` To pay off the whole loan today you would pay ${money(loan.payoffAmount)}, which includes a ${money(loan.payoffFee)} prepayment charge.`;
    }

    document.getElementById("pay-info").textContent = info;
    document.getElementById("pay-note").textContent =
        `Paying your next installment has no extra charge. Money that pays later installments before they are due has a ${prepaymentPercent}% prepayment charge taken out of it.`;
    document.getElementById("pay-amount").value = suggested;

    openModal("pay-modal");
    document.getElementById("pay-amount").select();
}

async function pay(event) {
    event.preventDefault();
    setMessage("pay-error", "");

    const amount = payForm.amount.value;

    try {
        const loan = await api.payMyLoan(payingLoanId, amount);
        closeModal("pay-modal");

        let message = `Payment of ${money(amount)} received. `;
        if (Number(loan.prepaymentFee) > 0) {
            message += `${money(loan.prepaymentFee)} of it was a prepayment charge. `;
        }
        message += loan.status === "PAID_OFF" ? "This loan is now fully paid." : `You now owe ${money(loan.outstanding)}.`;
        showToast(message);
        dataChanged();
    } catch (e) {
        setMessage("pay-error", e.message);
    }
}
