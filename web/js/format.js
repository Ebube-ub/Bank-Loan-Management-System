// Small helpers for showing values on the page.

// "12200.00" -> "12,200.00"
export function money(amount) {
    return Number(amount).toLocaleString("en-US", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    });
}

// "0.1" -> "10%"
export function percent(rate) {
    const value = Number((Number(rate) * 100).toFixed(4));
    return value + "%";
}

// "PAID_OFF" -> "Paid off"
export function statusLabel(status) {
    const text = status.replace("_", " ").toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
}

// a coloured pill for a status or tier, e.g. ACTIVE, PAID_OFF, PREMIUM
export function badge(status) {
    return `<span class="badge badge-${status.toLowerCase()}">${statusLabel(status)}</span>`;
}

// Makes text safe to put inside HTML. Without this, a customer named
// "<b>Ada</b>" would show up bold, or worse, run a script.
export function escapeHtml(text) {
    return String(text)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}
