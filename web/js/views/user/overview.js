// "My account" (customer): their credit score, band and limits, the form to
// apply for a loan, and the applications they have made.
//
// The customer sees the outcome of an application in plain words. The system's
// advice (Lend / Review / Decline) is only for the lender.

import * as api from "../../api.js";
import { money, badge, escapeHtml, shortDate, reasonList } from "../../format.js";
import { showToast, dataChanged, fillTable, show, setMessage } from "../../ui.js";
import { renderRules } from "../../rules.js";
import { renderNotice } from "../../notice.js";

const form = document.getElementById("apply-form");
const result = document.getElementById("apply-result");
const acceptBox = document.getElementById("apply-accept");
const applyButton = document.getElementById("apply-button");

// Apply stays switched off until the customer ticks "I have read and understood these rules"
function updateApplyButton() {
    applyButton.disabled = !acceptBox.checked;
}

const SCORE_MIN = 300;
const SCORE_MAX = 850;

export function init() {
    acceptBox.addEventListener("change", updateApplyButton);

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        setMessage("apply-error", "");
        result.classList.add("hidden");

        applyButton.disabled = true;

        try {
            const application = await api.applyForLoan(form.principal.value, form.termMonths.value, acceptBox.checked);
            form.reset();
            showResult(application);
            dataChanged();
        } catch (e) {
            setMessage("apply-error", e.message);
        } finally {
            updateApplyButton();
        }
    });
}

function showResult(application) {
    result.classList.remove("hidden", "result-good", "result-bad");

    if (application.status === "DECLINED") {
        result.classList.add("result-bad");
        result.innerHTML = `<strong>Your application was not approved.</strong><br>${escapeHtml(application.message)}`;
        return;
    }

    result.classList.add("result-good");
    result.innerHTML = `<strong>Your application was sent.</strong><br>
        ${escapeHtml(application.applicationId)}: ${money(application.principal)} over ${application.termMonths} months.
        The lender will decide on it. You will see the answer in "My applications" below.`;
    showToast("Application sent.");
}

export async function load() {
    let summary;
    let applications;

    try {
        [summary, applications] = await Promise.all([api.getMySummary(), api.getMyApplications()]);
    } catch (e) {
        showToast(e.message);
        return;
    }

    renderNotice("notice-overview", summary.notice, true);

    // ----- score card -----
    document.getElementById("me-score").textContent = summary.score;
    document.getElementById("me-band").innerHTML = badge(summary.band);

    // one plain bar, as long as the score is between 300 and 850, coloured by band
    const share = (summary.score - SCORE_MIN) / (SCORE_MAX - SCORE_MIN);
    const fill = document.getElementById("score-bar-fill");
    fill.style.width = `${Math.max(0, Math.min(1, share)) * 100}%`;
    fill.className = `score-bar-fill ${summary.band.toLowerCase()}`;

    document.getElementById("me-active").textContent = summary.maxActiveLoans === 0
        ? `You have ${summary.activeLoans} loan(s) now.`
        : `You have ${summary.activeLoans} of the ${summary.maxActiveLoans} loans you are allowed.`;
    document.getElementById("me-available").textContent = money(summary.availableToBorrow);
    document.getElementById("how-limit").textContent = money(summary.borrowingLimit);

    // how the score is worked out: this customer's own breakdown
    const factors = summary.scoreDetails.factors.map((factor) => `
        <tr>
            <td>${escapeHtml(factor.label)}</td>
            <td class="num">${factor.points > 0 && factor.label !== "Starting score" ? "+" : ""}${factor.points}</td>
        </tr>`);
    factors.push(`
        <tr class="total">
            <td>Your score</td>
            <td class="num">${summary.score}</td>
        </tr>`);
    document.getElementById("me-factors").innerHTML = factors.join("");

    // ----- the apply form, or the reason it can't be used -----
    show("apply-form", summary.canApply);
    show("apply-blocked", !summary.canApply);

    if (summary.canApply) {
        document.getElementById("apply-rate-note").textContent =
            `You can borrow up to ${money(summary.availableToBorrow)} right now.`;
        renderRules("rules-apply", summary, "Read before you apply");
        document.getElementById("apply-amount").max = summary.availableToBorrow;
    } else {
        document.getElementById("apply-blocked").innerHTML =
            `<strong>You can't apply for a loan right now.</strong>${reasonList(summary.reasons)}`;
    }

    // ----- applications -----
    const rows = applications.map((application) => `
        <tr>
            <td><strong>${money(application.principal)}</strong> <span class="muted">for ${application.termMonths} months</span></td>
            <td>${shortDate(application.submittedOn)}</td>
            <td>${badge(application.status)}</td>
            <td>${escapeHtml(application.message)}</td>
        </tr>`);
    fillTable("my-applications", "my-applications-empty", rows);
}
