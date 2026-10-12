// The Loans screen (search + table), plus the two loan pop-ups:
//  - "New loan" (checks the customer first, then asks for the rest)
//  - "Loan details" (summary + installments)
// Other screens reuse loanRow() and openLoanDetails() from here.

import * as api from "../api.js";
import { money, percent, badge, escapeHtml } from "../format.js";
import { openModal, closeModal, showToast, dataChanged, fillTable } from "../ui.js";
import { payLoan } from "./payments.js";

// ---------- Table rows shared with the dashboard ----------

export function loanRow(loan) {
    return `
        <tr>
            <td><strong>${escapeHtml(loan.loanId)}</strong></td>
            <td>${escapeHtml(loan.customerName)} <span class="muted">(${escapeHtml(loan.customerId)})</span></td>
            <td>${money(loan.principal)}</td>
            <td>${money(loan.outstanding)}</td>
            <td>${badge(loan.status)}</td>
            <td class="actions"><button class="btn btn-small" data-view-loan="${escapeHtml(loan.loanId)}">View</button></td>
        </tr>`;
}

// any "View" button inside this table opens that loan's details
export function listenForViewButtons(tbodyId) {
    document.getElementById(tbodyId).addEventListener("click", (event) => {
        const button = event.target.closest("[data-view-loan]");
        if (button) {
            openLoanDetails(button.dataset.viewLoan);
        }
    });
}

// ---------- Search ----------

const searchForm = document.getElementById("loan-search-form");
const searchMode = document.getElementById("search-mode");

export function init() {
    searchMode.addEventListener("change", () => {
        showSearchFields();
        if (searchMode.value === "all" || searchMode.value === "balance") {
            load();
        }
    });

    searchForm.addEventListener("submit", (event) => {
        event.preventDefault();
        load();
    });

    listenForViewButtons("loans-table");
    setUpNewLoanModal();
    setUpDetailsModal();
}

// only show the customer box or status list when that search is picked
function showSearchFields() {
    document.getElementById("search-customer-field").classList.toggle("hidden", searchMode.value !== "customer");
    document.getElementById("search-status-field").classList.toggle("hidden", searchMode.value !== "status");
}

export async function load() {
    const error = document.getElementById("loan-search-error");
    error.textContent = "";

    try {
        let loans;
        const mode = searchMode.value;

        if (mode === "customer") {
            const customerId = searchForm.customerId.value.trim();
            if (customerId === "") {
                error.textContent = "Enter a customer ID to search for.";
                fillTable("loans-table", "loans-empty", []);
                return;
            }
            loans = await api.getLoansByCustomer(customerId);
        } else if (mode === "status") {
            loans = await api.getLoansByStatus(searchForm.status.value);
        } else if (mode === "balance") {
            loans = await api.getLoansByBalance();
        } else {
            loans = await api.getAllLoans();
        }

        fillTable("loans-table", "loans-empty", loans.map(loanRow));
    } catch (e) {
        error.textContent = e.message;
        fillTable("loans-table", "loans-empty", []);
    }
}

// used by the Customers screen's "Loans" button
export function showCustomerLoans(customerId) {
    searchMode.value = "customer";
    searchForm.customerId.value = customerId;
    showSearchFields();
    location.hash = "#loans";
}

// ---------- New loan pop-up ----------

const newLoanForm = document.getElementById("new-loan-form");
const customerInput = document.getElementById("new-loan-customer");
const detailsFields = document.getElementById("new-loan-details");
const submitButton = document.getElementById("new-loan-submit");
const customerError = document.getElementById("new-loan-customer-error");
const customerOk = document.getElementById("new-loan-customer-ok");
const newLoanError = document.getElementById("new-loan-error");

export function openNewLoan(customerId = "") {
    newLoanForm.reset();
    lockLoanDetails();
    newLoanError.textContent = "";
    customerInput.value = customerId;
    openModal("new-loan-modal");

    if (customerId) {
        checkCustomer();
    } else {
        customerInput.focus();
    }
}

function setUpNewLoanModal() {
    // typing a different ID locks the rest of the form again...
    customerInput.addEventListener("input", lockLoanDetails);
    // ...and leaving the box checks the new ID straight away
    customerInput.addEventListener("change", checkCustomer);

    newLoanForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        // pressing Enter in the customer box just runs the check
        if (detailsFields.disabled) {
            checkCustomer();
            return;
        }

        newLoanError.textContent = "";
        submitButton.disabled = true;

        // the form asks for a percentage (10) but the server wants a decimal (0.1)
        const interestRate = Number((Number(newLoanForm.interestPercent.value) / 100).toFixed(6));

        try {
            const loan = await api.applyForLoan({
                customerId: customerInput.value.trim(),
                loanId: newLoanForm.loanId.value.trim(),
                principal: newLoanForm.principal.value,
                interestRate: String(interestRate),
                termMonths: newLoanForm.termMonths.value,
            });

            closeModal("new-loan-modal");
            showToast(`Loan ${loan.loanId} approved: ${money(loan.totalRepayable)} to repay over ${loan.installmentCount} months.`);
            dataChanged();
        } catch (e) {
            newLoanError.textContent = e.message;
        } finally {
            submitButton.disabled = false;
        }
    });
}

function lockLoanDetails() {
    detailsFields.disabled = true;
    submitButton.disabled = true;
    customerError.textContent = "";
    customerOk.textContent = "";
}

async function checkCustomer() {
    const customerId = customerInput.value.trim();
    lockLoanDetails();

    if (customerId === "") {
        return;
    }

    try {
        await api.checkEligibility(customerId);

        // ignore the answer if the box was changed while we were waiting
        if (customerInput.value.trim() !== customerId) {
            return;
        }

        customerOk.textContent = `Customer ${customerId} can take a new loan.`;
        detailsFields.disabled = false;
        submitButton.disabled = false;
        newLoanForm.loanId.focus();
    } catch (e) {
        if (customerInput.value.trim() === customerId) {
            customerError.textContent = e.message;
        }
    }
}

// ---------- Loan details pop-up ----------

const filterSelect = document.getElementById("details-filter");
let currentLoanId = null;

export async function openLoanDetails(loanId) {
    currentLoanId = loanId;
    filterSelect.value = "all";

    const loaded = await showLoanDetails();
    if (loaded) {
        openModal("loan-details-modal");
    }
}

function setUpDetailsModal() {
    filterSelect.addEventListener("change", showLoanDetails);

    document.getElementById("details-pay").addEventListener("click", () => {
        closeModal("loan-details-modal");
        payLoan(currentLoanId);
    });
}

async function showLoanDetails() {
    let loan;
    try {
        loan = await api.getLoan(currentLoanId, filterSelect.value);
    } catch (e) {
        showToast(e.message);
        return false;
    }

    document.getElementById("details-title").textContent = `Loan ${loan.loanId}`;

    document.getElementById("details-summary").innerHTML = `
        <div><span>Customer</span><span>${escapeHtml(loan.customerName)} (${escapeHtml(loan.customerId)})</span></div>
        <div><span>Status</span><span>${badge(loan.status)}</span></div>
        <div><span>Principal</span><span>${money(loan.principal)}</span></div>
        <div><span>Interest rate</span><span>${percent(loan.interestRate)} per year</span></div>
        <div><span>Term</span><span>${loan.termMonths} months</span></div>
        <div><span>Start date</span><span>${loan.disbursementDate}</span></div>
        <div><span>Total repayable</span><span>${money(loan.totalRepayable)}</span></div>
        <div><span>Outstanding</span><span><strong>${money(loan.outstanding)}</strong></span></div>`;

    const rows = loan.installments.map((installment) => `
        <tr>
            <td>${installment.dueDate}</td>
            <td>${money(installment.amountDue)}</td>
            <td>${money(installment.amountPaid)}</td>
            <td>${badge(installment.status)}</td>
        </tr>`);
    fillTable("details-installments", "details-empty", rows);

    // nothing left to pay on a paid-off loan
    document.getElementById("details-pay").classList.toggle("hidden", loan.status === "PAID_OFF");
    return true;
}
