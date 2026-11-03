// The Customers screen (admin): add customers (each gets a login), see every
// customer's score, band and tier, open one to see why, and reset passwords.

import * as api from "../../api.js";
import { money, badge, escapeHtml, shortDate } from "../../format.js";
import { openModal, showToast, fillTable, show, setMessage } from "../../ui.js";
import { showCustomerLoans } from "./loans.js";

const form = document.getElementById("customer-form");

export function init() {
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        setMessage("customer-error", "");

        // FormData collects every field in the form by its name="" attribute
        const fields = Object.fromEntries(new FormData(form));

        try {
            const customer = await api.createCustomer(fields);
            form.reset();
            showCredentials(customer.name, customer.username, customer.temporaryPassword, "Customer added");
            load();
        } catch (e) {
            setMessage("customer-error", e.message);
        }
    });

    document.getElementById("customers-table").addEventListener("click", (event) => {
        const detailsButton = event.target.closest("[data-customer-details]");
        if (detailsButton) {
            openDetails(detailsButton.dataset.customerDetails);
        }

        const loansButton = event.target.closest("[data-customer-loans]");
        if (loansButton) {
            showCustomerLoans(loansButton.dataset.customerLoans);
        }

        const resetButton = event.target.closest("[data-customer-reset]");
        if (resetButton) {
            resetPassword(resetButton.dataset.customerReset, resetButton.dataset.customerName);
        }
    });

    document.getElementById("demo-button").addEventListener("click", loadDemoData);
}

export async function load() {
    try {
        const customers = await api.getCustomers();

        const rows = customers.map((customer) => {
            const id = escapeHtml(customer.customerId);

            return `
                <tr>
                    <td><strong>${id}</strong></td>
                    <td>${escapeHtml(customer.name)}</td>
                    <td>${customer.score}</td>
                    <td>${badge(customer.band)}</td>
                    <td>${badge(customer.tier)}</td>
                    <td>${activeText(customer, " / ")}</td>
                    <td>${customer.hasLogin ? "Yes" : '<span class="muted">No</span>'}</td>
                    <td class="actions">
                        <button class="btn btn-small" data-customer-details="${id}">Details</button>
                        <button class="btn btn-small" data-customer-loans="${id}">Loans</button>
                        <button class="btn btn-small" data-customer-reset="${id}" data-customer-name="${escapeHtml(customer.name)}">${
                            customer.hasLogin ? "Reset password" : "Create login"}</button>
                    </td>
                </tr>`;
        });

        fillTable("customers-table", "customers-empty", rows);

        // the demo customers are offered only until they have been loaded
        const demoLoaded = customers.some((customer) => customer.customerId === "D001");
        show("demo-card", !demoLoaded);
    } catch (e) {
        showToast(e.message);
    }
}

// "1 / 3", or "1 (none allowed)" for a customer in the Poor band
function activeText(customer, joiner) {
    if (customer.maxActiveLoans === 0) {
        return `${customer.activeLoans} (none allowed)`;
    }
    return `${customer.activeLoans}${joiner}${customer.maxActiveLoans}`;
}

// the pop-up that shows a login's temporary password (only ever shown once)
function showCredentials(name, username, password, title) {
    document.getElementById("credentials-title").textContent = title;
    document.getElementById("credentials-name").textContent = name;
    document.getElementById("credentials-username").textContent = username;
    document.getElementById("credentials-password").textContent = password;
    openModal("credentials-modal");
}

async function resetPassword(customerId, name) {
    const ok = window.confirm(`Give ${name} a new temporary password? Their current password will stop working.`);
    if (!ok) {
        return;
    }

    try {
        const login = await api.resetPassword(customerId);
        showCredentials(name, login.username, login.temporaryPassword, "New temporary password");
        load();
    } catch (e) {
        showToast(e.message);
    }
}

async function loadDemoData() {
    setMessage("demo-error", "");

    try {
        const result = await api.loadDemoData();
        showToast(`${result.customersAdded} demo customers added.`);
        load();
    } catch (e) {
        setMessage("demo-error", e.message);
    }
}

// the pop-up with a customer's score breakdown, loans and applications
async function openDetails(customerId) {
    let customer;
    try {
        customer = await api.getCustomer(customerId);
    } catch (e) {
        showToast(e.message);
        return;
    }

    document.getElementById("customer-title").textContent = `${customer.name} (${customer.customerId})`;

    document.getElementById("customer-summary").innerHTML = `
        <div><span>Email</span><span>${escapeHtml(customer.email)}</span></div>
        <div><span>Credit score</span><span><strong>${customer.score}</strong> ${badge(customer.band)}</span></div>
        <div><span>Tier</span><span>${badge(customer.tier)}</span></div>
        <div><span>Active loans</span><span>${activeText(customer, " of ")}</span></div>
        <div><span>Largest single loan</span><span>${money(customer.maxSingleLoan)}</span></div>
        <div><span>Login</span><span>${customer.hasLogin ? "Yes" : "No"}</span></div>`;

    const factors = customer.scoreDetails.factors.map((factor) => `
        <tr>
            <td>${escapeHtml(factor.label)}</td>
            <td class="num">${factor.points > 0 && factor.label !== "Starting score" ? "+" : ""}${factor.points}</td>
        </tr>`);
    factors.push(`
        <tr class="total">
            <td>Score</td>
            <td class="num">${customer.scoreDetails.score}</td>
        </tr>`);
    document.getElementById("customer-factors").innerHTML = factors.join("");

    const loanRows = customer.loans.map((loan) => `
        <tr>
            <td>${escapeHtml(loan.loanId)}</td>
            <td>${money(loan.principal)}</td>
            <td>${money(loan.outstanding)}</td>
            <td>${badge(loan.status)}</td>
        </tr>`);
    fillTable("customer-loans", "customer-loans-empty", loanRows);

    const applicationRows = customer.applications.map((application) => `
        <tr>
            <td>${escapeHtml(application.applicationId)}</td>
            <td>${money(application.principal)}</td>
            <td>${shortDate(application.submittedOn)}</td>
            <td>${badge(application.advice)}</td>
            <td>${badge(application.status)}</td>
        </tr>`);
    fillTable("customer-applications", "customer-applications-empty", applicationRows);

    openModal("customer-modal");
}
