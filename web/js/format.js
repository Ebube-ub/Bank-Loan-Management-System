// Small helpers for showing values on the page.

// "12200.00" -> "₦12,200.00". Every amount of money on the site goes through here.
export function money(amount) {
    const number = Number(amount);
    const sign = number < 0 ? "-" : "";

    return sign + "₦" + Math.abs(number).toLocaleString("en-US", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    });
}

// "20" -> "20%"
export function percent(value) {
    return value + "%";
}

// "PAID_OFF" -> "Paid off"
export function statusLabel(status) {
    const text = status.replace("_", " ").toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
}

// a coloured pill for a status, band, advice or tier,
// e.g. ACTIVE, PAID_OFF, EXCELLENT, LEND, PREMIUM
export function badge(status) {
    return `<span class="badge badge-${status.toLowerCase()}">${statusLabel(status)}</span>`;
}

// "2026-10-15" -> "15 Oct 2026"
export function shortDate(isoDate) {
    if (!isoDate) {
        return "";
    }
    const [year, month, day] = isoDate.split("-").map(Number);
    return new Date(year, month - 1, day).toLocaleDateString("en-GB", {
        day: "numeric",
        month: "short",
        year: "numeric",
    });
}

// "2026-10" -> "Oct 2026"
export function monthLabel(isoMonth) {
    const [year, month] = isoMonth.split("-").map(Number);
    return new Date(year, month - 1, 1).toLocaleDateString("en-GB", { month: "short", year: "numeric" });
}

// Makes text safe to put inside HTML. Without this, a customer named
// "<b>Ada</b>" would show up bold, or worse, run a script.
export function escapeHtml(text) {
    return String(text ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

// a list of reasons as bullet points
export function reasonList(reasons) {
    return `<ul class="reasons">${reasons.map((reason) => `<li>${escapeHtml(reason)}</li>`).join("")}</ul>`;
}
