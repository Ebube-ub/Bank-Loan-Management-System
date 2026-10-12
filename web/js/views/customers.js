// The Customers screen: register form + table of all customers.

import * as api from "../api.js";
import { badge, escapeHtml } from "../format.js";
import { showToast, fillTable } from "../ui.js";
import { openNewLoan, showCustomerLoans } from "./loans.js";

const form = document.getElementById("customer-form");
const error = document.getElementById("customer-error");

export function init() {
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        error.textContent = "";

        // FormData collects every field in the form by its name="" attribute
        const fields = Object.fromEntries(new FormData(form));

        try {
            const customer = await api.registerCustomer(fields);
            showToast(`${customer.name} registered as ${customer.customerId}.`);
            form.reset();
            load();
        } catch (e) {
            error.textContent = e.message;
        }
    });

    document.getElementById("customers-table").addEventListener("click", (event) => {
        const loansButton = event.target.closest("[data-customer-loans]");
        if (loansButton) {
            showCustomerLoans(loansButton.dataset.customerLoans);
        }

        const newLoanButton = event.target.closest("[data-customer-new-loan]");
        if (newLoanButton) {
            openNewLoan(newLoanButton.dataset.customerNewLoan);
        }
    });
}

export async function load() {
    try {
        const customers = await api.getCustomers();

        const rows = customers.map((customer) => {
            const id = escapeHtml(customer.customerId);
            const atLimit = customer.activeLoans >= customer.maxActiveLoans;

            return `
                <tr>
                    <td><strong>${id}</strong></td>
                    <td>${escapeHtml(customer.name)}</td>
                    <td>${escapeHtml(customer.email)}</td>
                    <td>${badge(customer.tier)}</td>
                    <td>${customer.activeLoans} / ${customer.maxActiveLoans}</td>
                    <td class="actions">
                        <button class="btn btn-small" data-customer-loans="${id}">Loans</button>
                        <button class="btn btn-small" data-customer-new-loan="${id}" ${atLimit ? "disabled title=\"At the loan limit for this tier\"" : ""}>New loan</button>
                    </td>
                </tr>`;
        });

        fillTable("customers-table", "customers-empty", rows);
    } catch (e) {
        showToast(e.message);
    }
}
