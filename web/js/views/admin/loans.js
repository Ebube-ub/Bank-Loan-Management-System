// The Loans screen (admin): search every loan, and open one to see its
// installments and payments.

import * as api from "../../api.js";
import { money, badge, escapeHtml } from "../../format.js";
import { fillTable, setMessage } from "../../ui.js";
import { openLoanDetails } from "../loanDetails.js";
import { prefillLoan } from "./payments.js";

// one table row, shared with the dashboard
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
            openLoan(button.dataset.viewLoan);
        }
    });
}

// opens the details; the pay button there goes to the Record a payment screen
export function openLoan(loanId) {
    openLoanDetails(loanId, "admin", prefillLoan);
}

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
}

// only show the customer box or status list when that search is picked
function showSearchFields() {
    document.getElementById("search-customer-field").classList.toggle("hidden", searchMode.value !== "customer");
    document.getElementById("search-status-field").classList.toggle("hidden", searchMode.value !== "status");
}

export async function load() {
    setMessage("loan-search-error", "");

    try {
        let loans;
        const mode = searchMode.value;

        if (mode === "customer") {
            const customerId = searchForm.customerId.value.trim();
            if (customerId === "") {
                setMessage("loan-search-error", "Enter a customer ID to search for.");
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
        setMessage("loan-search-error", e.message);
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
