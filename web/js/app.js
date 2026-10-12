// Starts the page and switches between screens.
//
// The screen on show comes from the part of the URL after "#", e.g.
// http://localhost:8090/#loans shows the Loans screen. Clicking a sidebar
// link changes the "#..." part, and the "hashchange" event below swaps the
// screen. This also makes the browser's Back button work.

import * as dashboard from "./views/dashboard.js";
import * as customers from "./views/customers.js";
import * as loans from "./views/loans.js";
import * as payments from "./views/payments.js";
import * as reports from "./views/reports.js";
import { setUpModals, onDataChanged } from "./ui.js";

const views = {
    dashboard: { title: "Dashboard", module: dashboard },
    customers: { title: "Customers", module: customers },
    loans: { title: "Loans", module: loans },
    payments: { title: "Payments", module: payments },
    reports: { title: "Reports", module: reports },
};

function currentViewName() {
    const name = location.hash.slice(1);
    return views[name] ? name : "dashboard";
}

function showView() {
    const name = currentViewName();

    // show this screen's <section>, hide the others
    for (const key in views) {
        document.getElementById(`view-${key}`).classList.toggle("hidden", key !== name);
    }

    // highlight the matching sidebar link
    document.querySelectorAll(".nav a").forEach((link) => {
        link.classList.toggle("active", link.dataset.view === name);
    });

    document.getElementById("page-title").textContent = views[name].title;

    // always fetch fresh data when a screen is opened
    views[name].module.load();
}

// set up each screen's buttons and forms once
for (const key in views) {
    views[key].module.init();
}

setUpModals();

document.getElementById("new-loan-button").addEventListener("click", () => loans.openNewLoan());

// after a new loan or payment, reload whatever screen is showing
onDataChanged(() => views[currentViewName()].module.load());

window.addEventListener("hashchange", showView);
showView();
