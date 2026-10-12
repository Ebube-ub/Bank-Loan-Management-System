// One function per server endpoint. Every function returns a Promise, so
// call them with "await". If the server answers with an error, the
// function throws an Error whose message is the server's message
// (e.g. "Customer ID C001 already exists.").

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
        throw new Error("Can't reach the server. Is it still running?");
    }

    const body = await response.json();

    if (!response.ok) {
        throw new Error(body.error || "Something went wrong.");
    }
    return body;
}

// encodeURIComponent makes IDs with spaces or symbols safe to put in a URL
const id = (value) => encodeURIComponent(value);

// ---------- Customers ----------

export function getCustomers() {
    return request("GET", "/api/customers");
}

export function registerCustomer(customer) {
    return request("POST", "/api/customers", customer);
}

export function checkEligibility(customerId) {
    return request("GET", `/api/customers/${id(customerId)}/eligibility`);
}

// ---------- Loans ----------

export function getAllLoans() {
    return request("GET", "/api/loans");
}

export function getLoansByCustomer(customerId) {
    return request("GET", `/api/loans?customerId=${id(customerId)}`);
}

export function getLoansByStatus(status) {
    return request("GET", `/api/loans?status=${id(status)}`);
}

export function getLoansByBalance() {
    return request("GET", "/api/loans?sort=balance");
}

// filter is "all", "paid" or "unpaid"
export function getLoan(loanId, filter) {
    return request("GET", `/api/loans/${id(loanId)}?installments=${id(filter)}`);
}

export function applyForLoan(loan) {
    return request("POST", "/api/loans", loan);
}

export function makePayment(loanId, amount) {
    return request("POST", `/api/loans/${id(loanId)}/payments`, { amount: amount });
}

// ---------- Reports ----------

export function getSummary() {
    return request("GET", "/api/reports/summary");
}

export function getTopBorrowers(limit) {
    return request("GET", `/api/reports/top-borrowers?limit=${id(limit)}`);
}

export function getOverdue() {
    return request("GET", "/api/reports/overdue");
}
