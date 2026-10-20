// The warning (and later the escalation notice) a customer sees at the top of their pages,
// once the lender has sent a warning. The words come from the server with the customer's
// own numbers, so what they read is what is owed today.

import { money, escapeHtml } from "./format.js";

export function renderNotice(containerId, notice, withPayLink) {
    const box = document.getElementById(containerId);

    if (!notice) {
        box.className = "hidden";
        box.innerHTML = "";
        return;
    }

    const escalated = notice.kind === "ESCALATED";
    box.className = `customer-notice ${escalated ? "notice-escalated" : "notice-warning"}`;

    box.innerHTML = `
        <h3>${escapeHtml(notice.title)}</h3>
        <p class="notice-text">${escapeHtml(notice.message)}</p>
        <p class="notice-total">To clear everything that is late today: <strong>${money(notice.owed.total)}</strong></p>
        ${withPayLink ? '<a class="btn btn-primary" href="#my-loans">Go to My loans to pay</a>' : ""}`;
}
