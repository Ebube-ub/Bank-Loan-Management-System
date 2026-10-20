// One function per server endpoint. Every function returns a Promise, so
// call them with "await". If the server answers with an error, the function
// throws an ApiError whose message is the server's message
// (e.g. "Customer ID D001 already exists.").
//
// If the server says "you are not signed in" (401) anywhere except on the
// login form, the page is told so it can show the login screen again.

export class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}

async function request(method, url, data) {
    const options = { method: method };

    if (data) {
        // send the fields like an HTML form does: name=Ada&tier=STANDARD
        options.headers = { "Content-Type": "application/x-www-form-urlencoded" };
        options.body = new URLSearchParams(data);
    }

    let response;
    try {
        response = await fetch(url, options);
    } catch (error) {
        throw new ApiError("Can't reach the server. Is it still running?", 0);
    }

    // The server always answers with JSON. If it doesn't (for example the page
    // is hosted somewhere that has no backend and returns a plain 404), show a
    // friendly message instead of a confusing "Unexpected token" error.
    let body;
    try {
        body = await response.json();
    } catch (error) {
        throw new ApiError("The server didn't answer properly. Is the NepoLM server running?", response.status);
    }

    if (!response.ok) {
        if (response.status === 401 && url !== "/api/login") {
            window.dispatchEvent(new Event("nepolm:signed-out"));
        }
        throw new ApiError(body.error || "Something went wrong.", response.status);
    }
    return body;
}

// encodeURIComponent makes IDs with spaces or symbols safe to put in a URL
const id = (value) => encodeURIComponent(value);

// ---------- Signing in ----------

export const login = (username, password) => request("POST", "/api/login", { username, password });
export const logout = () => request("POST", "/api/logout", {});
export const getMe = () => request("GET", "/api/me");
export const changePassword = (currentPassword, newPassword) =>
    request("POST", "/api/change-password", { currentPassword, newPassword });

// ---------- Customer (user) routes ----------

export const getMySummary = () => request("GET", "/api/my/summary");
export const getMyLoans = () => request("GET", "/api/my/loans");
export const getMyLoan = (loanId, filter) => request("GET", `/api/my/loans/${id(loanId)}?installments=${id(filter)}`);
export const payMyLoan = (loanId, amount) => request("POST", `/api/my/loans/${id(loanId)}/payments`, { amount });
export const getMyApplications = () => request("GET", "/api/my/applications");
export const applyForLoan = (principal, termMonths, acceptRules) =>
    request("POST", "/api/my/applications", { principal, termMonths, acceptRules });

// ---------- Admin routes: late customers ----------

export const getCollections = () => request("GET", "/api/collections");
export const getWarningPreview = (customerId) => request("GET", `/api/collections/${id(customerId)}/warning-preview`);
export const sendWarning = (customerId) => request("POST", `/api/collections/${id(customerId)}/warning`, {});
export const getLawyerFile = (customerId) => request("GET", `/api/collections/${id(customerId)}/lawyer-file`);
export const referToLawyer = (customerId, note) => request("POST", `/api/collections/${id(customerId)}/refer`, { note });

// ---------- Admin routes: customers ----------

export const getCustomers = () => request("GET", "/api/customers");
export const getCustomer = (customerId) => request("GET", `/api/customers/${id(customerId)}`);
export const createCustomer = (customer) => request("POST", "/api/customers", customer);
export const resetPassword = (customerId) => request("POST", `/api/customers/${id(customerId)}/reset-password`, {});
export const loadDemoData = () => request("POST", "/api/demo-data", {});

// ---------- Admin routes: loans and payments ----------

export const getAllLoans = () => request("GET", "/api/loans");
export const getLoansByCustomer = (customerId) => request("GET", `/api/loans?customerId=${id(customerId)}`);
export const getLoansByStatus = (status) => request("GET", `/api/loans?status=${id(status)}`);
export const getLoansByBalance = () => request("GET", "/api/loans?sort=balance");
export const getLoan = (loanId, filter) => request("GET", `/api/loans/${id(loanId)}?installments=${id(filter)}`);
export const recordPayment = (loanId, amount) => request("POST", `/api/loans/${id(loanId)}/payments`, { amount });

// ---------- Admin routes: applications ----------

// status is "PENDING", "APPROVED", "DECLINED", or "" for all
export const getApplications = (status) =>
    request("GET", status ? `/api/applications?status=${id(status)}` : "/api/applications");
export const getApplication = (applicationId) => request("GET", `/api/applications/${id(applicationId)}`);
// interestRate is a decimal such as "0.2"; leave it out to keep the application's rate
export const approveApplication = (applicationId, interestRate) =>
    request("POST", `/api/applications/${id(applicationId)}/approve`, interestRate ? { interestRate } : {});
export const declineApplication = (applicationId, note) =>
    request("POST", `/api/applications/${id(applicationId)}/decline`, { note });

// ---------- Admin routes: reports and finance ----------

export const getSummary = () => request("GET", "/api/reports/summary");
export const getTopBorrowers = (limit) => request("GET", `/api/reports/top-borrowers?limit=${id(limit)}`);
export const getOverdue = () => request("GET", "/api/reports/overdue");
export const getFinance = () => request("GET", "/api/finance");
