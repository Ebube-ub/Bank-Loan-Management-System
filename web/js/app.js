// Starts the page. It decides which screen to show:
//
//   not signed in            -> the sign-in screen
//   signed in, temporary pw  -> "choose your own password"
//   signed in                -> the app, with the screens for their role
//
// The screen on show inside the app comes from the part of the URL after "#",
// e.g. http://localhost:8090/#loans. Clicking a sidebar link changes the
// "#..." part, and the "hashchange" event swaps the screen. This also makes
// the browser's Back button work.
//
// Which screens exist depends on the role: the lender (ADMIN) and the customer
// (USER) have different ones. The server checks the role on every request too,
// so hiding a screen here is only for tidiness, never for security.

import * as api from "./api.js";
import { setUpModals, onDataChanged, show, setMessage, showToast } from "./ui.js";
import { init as initLoanDetails } from "./views/loanDetails.js";

import * as dashboard from "./views/admin/dashboard.js";
import * as applications from "./views/admin/applications.js";
import * as customers from "./views/admin/customers.js";
import * as loans from "./views/admin/loans.js";
import * as payments from "./views/admin/payments.js";
import * as overdue from "./views/admin/overdue.js";
import * as finance from "./views/admin/finance.js";
import * as reports from "./views/admin/reports.js";

import * as overview from "./views/user/overview.js";
import * as myLoans from "./views/user/myLoans.js";

const adminViews = {
    dashboard: { title: "Dashboard", section: "view-dashboard", module: dashboard },
    applications: { title: "Applications", section: "view-applications", module: applications },
    customers: { title: "Customers", section: "view-customers", module: customers },
    loans: { title: "Loans", section: "view-loans", module: loans },
    payments: { title: "Payments", section: "view-payments", module: payments },
    overdue: { title: "Overdue", section: "view-overdue", module: overdue },
    finance: { title: "Finance", section: "view-finance", module: finance },
    reports: { title: "Reports", section: "view-reports", module: reports },
};

const userViews = {
    overview: { title: "My account", section: "view-overview", module: overview },
    "my-loans": { title: "My loans", section: "view-my-loans", module: myLoans },
};

const defaultView = { ADMIN: "dashboard", USER: "overview" };

let me = null;        // who is signed in
let views = null;     // the screens that person can use
let passwordChangeIsForced = false;
const alreadySetUp = new Set(); // each screen's init() must only run once

// ---------- Which big screen is showing ----------

function showOnly(screenId) {
    for (const id of ["login-screen", "password-screen", "app"]) {
        show(id, id === screenId);
    }
}

function showLogin(message) {
    me = null;
    clearScreens();
    setMessage("login-error", message);
    showOnly("login-screen");
    document.getElementById("login-username").focus();
}

function showPasswordScreen(forced) {
    passwordChangeIsForced = forced;
    document.getElementById("password-form").reset();
    setMessage("password-error", "");

    document.getElementById("password-title").textContent =
        forced ? "Choose your own password" : "Change your password";
    document.getElementById("password-note").textContent = forced
        ? "You signed in with a temporary password. Choose a new one to continue."
        : "Enter your current password, then the new one.";
    show("password-cancel", !forced);

    showOnly("password-screen");
}

// forget the last person's data so the next person to sign in never sees it
function clearScreens() {
    // only the tables the page fills in (they have an id); fixed tables stay as they are
    document.querySelectorAll("#app tbody[id]").forEach((body) => {
        body.innerHTML = "";
    });
    document.querySelectorAll("#app .result").forEach((box) => box.classList.add("hidden"));
    document.querySelectorAll("#app form").forEach((form) => form.reset());
}

// ---------- Signing in and out ----------

async function start() {
    try {
        enter(await api.getMe());
    } catch (e) {
        // 401 just means "not signed in yet"; anything else is worth telling them
        showLogin(e.status === 401 ? "" : e.message);
    }
}

function enter(account) {
    me = account;

    if (account.mustChangePassword) {
        showPasswordScreen(true);
    } else {
        startApp();
    }
}

function startApp() {
    views = me.role === "ADMIN" ? adminViews : userViews;

    // only this role's links show in the sidebar
    document.querySelectorAll("#nav a").forEach((link) => {
        link.classList.toggle("hidden", link.dataset.role !== me.role);
    });

    document.getElementById("user-name").textContent = me.name;
    document.getElementById("user-role").textContent = me.role === "ADMIN" ? "Lender (admin)" : "Customer";

    // set up each of this role's screens once
    for (const key in views) {
        const module = views[key].module;
        if (!alreadySetUp.has(module)) {
            module.init();
            alreadySetUp.add(module);
        }
    }

    showOnly("app");

    if (!views[currentViewName()]) {
        location.hash = "#" + defaultView[me.role];
    }
    showView();
}

async function signOut() {
    try {
        await api.logout();
    } catch (e) {
        // even if the server can't be reached, show the sign-in screen
    }
    location.hash = "";
    showLogin("");
}

document.getElementById("login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.target;
    setMessage("login-error", "");

    try {
        const account = await api.login(form.username.value, form.password.value);
        form.reset();
        enter(account);
    } catch (e) {
        setMessage("login-error", e.message);
        form.password.value = "";
    }
});

document.getElementById("password-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.target;
    setMessage("password-error", "");

    if (form.newPassword.value !== form.confirmPassword.value) {
        setMessage("password-error", "The two new passwords are not the same.");
        return;
    }

    try {
        await api.changePassword(form.currentPassword.value, form.newPassword.value);
        showToast("Password changed.");

        if (passwordChangeIsForced) {
            enter(await api.getMe());
        } else {
            showOnly("app");
        }
    } catch (e) {
        setMessage("password-error", e.message);
    }
});

document.getElementById("password-cancel").addEventListener("click", () => showOnly("app"));
document.getElementById("change-password-button").addEventListener("click", () => showPasswordScreen(false));
document.getElementById("logout-button").addEventListener("click", signOut);

// the server said we are no longer signed in (for example it was restarted)
window.addEventListener("nepolm:signed-out", () => {
    if (me) {
        showLogin("Please sign in again.");
    }
});

// ---------- Screens inside the app ----------

function currentViewName() {
    return location.hash.slice(1);
}

function showView() {
    if (!me || !views) {
        return;
    }

    let name = currentViewName();
    if (!views[name]) {
        name = defaultView[me.role];
    }

    // show this screen's <section>, hide all the others
    for (const set of [adminViews, userViews]) {
        for (const key in set) {
            document.getElementById(set[key].section).classList.toggle("hidden", set !== views || key !== name);
        }
    }

    // highlight the matching sidebar link
    document.querySelectorAll("#nav a").forEach((link) => {
        link.classList.toggle("active", link.dataset.role === me.role && link.dataset.view === name);
    });

    document.getElementById("page-title").textContent = views[name].title;

    // always fetch fresh data when a screen is opened
    views[name].module.load();
    refreshPendingCount();
}

// the little numbers on the "Applications" and "Overdue" links (lender only)
async function refreshPendingCount() {
    const applicationsBadge = document.getElementById("pending-count");
    const overdueBadge = document.getElementById("overdue-count");

    if (!me || me.role !== "ADMIN") {
        applicationsBadge.classList.add("hidden");
        overdueBadge.classList.add("hidden");
        return;
    }

    try {
        const summary = await api.getSummary();
        applicationsBadge.textContent = summary.pendingApplications;
        applicationsBadge.classList.toggle("hidden", summary.pendingApplications === 0);

        // customers who need a warning, or whose case needs escalation
        const needAction = summary.warningsToSend + summary.needsEscalation;
        overdueBadge.textContent = needAction;
        overdueBadge.classList.toggle("hidden", needAction === 0);
    } catch (e) {
        // not important enough to show an error for
    }
}

setUpModals();
initLoanDetails();

// after a new loan, application or payment, reload whatever screen is showing
onDataChanged(() => showView());

window.addEventListener("hashchange", showView);
start();
