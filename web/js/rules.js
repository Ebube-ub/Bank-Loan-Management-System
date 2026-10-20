// The rules a customer reads before they borrow, in plain words: interest, the late fee,
// interest on late amounts, warnings and escalation, and the charge for paying early.
// Every number comes from the server (the same settings the system uses), so this
// text can never promise something the system does not do.

import { money, percent, escapeHtml } from "./format.js";

export function renderRules(containerId, summary, title) {
    const example = summary.lateExample;

    document.getElementById(containerId).innerHTML = `
        <h3>${escapeHtml(title)}</h3>
        <ul>
            <li><strong>Interest.</strong> ${percent(summary.interestPercent)} a year, set by the lender.</li>
            <li><strong>Paying late.</strong> If an installment is paid after its due date, a late fee of
                ${percent(summary.lateFeePercent)} of that installment is added, once.</li>
            <li><strong>Late amounts grow, and you pay it.</strong> An overdue amount (the installment plus its
                late fee) also grows by ${summary.monthlyPercent}% each month, which is the yearly rate split into 12.
                Each month's growth is added before the next month's is worked out.
                Example: a ${money(example.installment)} installment that is ${example.days} days late costs
                ${money(example.installment)} + ${money(example.lateFee)} late fee +
                ${money(example.interest)} interest = <strong>${money(example.total)}</strong>.</li>
            <li><strong>Warnings.</strong> After more than ${summary.warningAfterDays} days overdue you get a warning
                on your account page. If it is still unpaid ${summary.escalateAfterDays} days after the warning,
                your account is escalated, and the lender may take further action, which can include legal action.</li>
            <li><strong>Paying early.</strong> Money that pays installments before they are due, other than your
                next installment, has a ${percent(summary.prepaymentFeePercent)} prepayment charge.</li>
        </ul>`;
}
