// Draws the charts on the Reports screen. Each chart is a picture made of SVG shapes,
// built as text and put into a box on the page. There is no chart library: the page's
// security policy only allows our own files, and three simple charts don't need one.
//
//   donut(box, slices, options)         a ring split into slices, with a legend
//   columns(box, months, series, empty)  pairs of columns for each month, with a legend
//
// Colours come from CSS classes (c-blue, c-green ...) rather than style="..." because the
// page's security policy does not allow inline styles. See "Charts" in styles.css.

import { money, escapeHtml } from "./format.js";

// ---------- Doughnut ----------

// slices: [{ label: "Active", value: 18, color: "c-blue" }, ...]
// options: { centre: "loans" (small text under the total), empty: "No loans yet." }
export function donut(box, slices, options) {
    const total = slices.reduce((sum, slice) => sum + slice.value, 0);

    if (total === 0) {
        box.innerHTML = `<p class="empty">${escapeHtml(options.empty)}</p>`;
        return;
    }

    const radius = 70;
    const circumference = 2 * Math.PI * radius;
    let used = 0; // how much of the ring is already taken by earlier slices

    const rings = slices.filter((slice) => slice.value > 0).map((slice) => {
        const length = (slice.value / total) * circumference;
        const ring = `
            <circle class="donut-slice ${slice.color}" cx="100" cy="100" r="${radius}" fill="none" stroke-width="28"
                    stroke-dasharray="${length} ${circumference - length}" stroke-dashoffset="${-used}"
                    transform="rotate(-90 100 100)">
                <title>${escapeHtml(slice.label)}: ${slice.value} (${percentOf(slice.value, total)}%)</title>
            </circle>`;
        used += length;
        return ring;
    });

    const description = `${options.centre}: ` + slices.map((slice) => `${slice.label} ${slice.value}`).join(", ");

    const legend = slices.map((slice) => `
        <li>
            <span class="legend-dot ${slice.color}"></span>
            <span class="legend-label">${escapeHtml(slice.label)}</span>
            <strong>${slice.value}</strong>
            <span class="muted">(${percentOf(slice.value, total)}%)</span>
        </li>`);

    box.innerHTML = `
        <div class="donut">
            <svg viewBox="0 0 200 200" role="img" aria-label="${escapeHtml(description)}">
                ${rings.join("")}
                <text x="100" y="102" text-anchor="middle" class="donut-total">${total}</text>
                <text x="100" y="122" text-anchor="middle" class="chart-caption">${escapeHtml(options.centre)}</text>
            </svg>
            <ul class="legend">${legend.join("")}</ul>
        </div>`;
}

function percentOf(value, total) {
    return Math.round((value / total) * 100);
}

// ---------- Columns ----------

// months: [{ month: "2026-09", lent: "340000.00", earned: "5200.00" }, ...]
// series: [{ key: "lent", label: "Lent", color: "c-blue" }, ...]  (key = the field to read from each month)
export function columns(box, months, series, emptyText) {
    const biggest = Math.max(0, ...months.flatMap((month) => series.map((s) => Number(month[s.key]))));

    if (biggest === 0) {
        box.innerHTML = `<p class="empty">${escapeHtml(emptyText)}</p>`;
        return;
    }

    // the picture is 640 wide and 300 tall; it shrinks to fit the screen
    const width = 640;
    const height = 300;
    const left = 64;    // room for the amounts on the left
    const right = 12;
    const top = 12;
    const bottom = 36;  // room for the month names
    const plotWidth = width - left - right;
    const plotHeight = height - top - bottom;

    // the left-hand scale goes up in round steps (e.g. 50k, 100k, 150k ...) and has at most 5 lines
    const step = roundStep(biggest / 4);
    const lines = Math.ceil(biggest / step);
    const axisMax = lines * step;
    const yOf = (value) => top + plotHeight - (value / axisMax) * plotHeight;

    let grid = "";
    for (let i = 0; i <= lines; i++) {
        const y = yOf(i * step);
        grid += `
            <line class="chart-grid-line" x1="${left}" x2="${width - right}" y1="${y}" y2="${y}"/>
            <text class="chart-axis" x="${left - 8}" y="${y + 4}" text-anchor="end">&#8358;${short(i * step)}</text>`;
    }

    const groupWidth = plotWidth / months.length;
    const barWidth = Math.min(groupWidth * 0.36, 28);
    const gap = 2;
    const groupInner = series.length * barWidth + (series.length - 1) * gap;

    let bars = "";
    months.forEach((month, index) => {
        const groupLeft = left + index * groupWidth + (groupWidth - groupInner) / 2;

        series.forEach((s, position) => {
            const value = Number(month[s.key]);
            if (value <= 0) {
                return;
            }
            const x = groupLeft + position * (barWidth + gap);
            const y = yOf(value);
            bars += `
                <rect class="column ${s.color}" x="${x}" y="${y}" width="${barWidth}" height="${top + plotHeight - y}" rx="2">
                    <title>${escapeHtml(shortMonth(month.month))} - ${escapeHtml(s.label)}: ${money(value)}</title>
                </rect>`;
        });

        bars += `<text class="chart-axis" x="${left + index * groupWidth + groupWidth / 2}" y="${height - bottom + 18}"
                       text-anchor="middle">${escapeHtml(shortMonth(month.month))}</text>`;
    });

    const description = "Money lent and earned each month: " + months.map((month) =>
        `${shortMonth(month.month)} ` + series.map((s) => `${s.label} ${money(month[s.key])}`).join(" and ")).join("; ");

    const legend = series.map((s) => `
        <li><span class="legend-dot ${s.color}"></span><span class="legend-label">${escapeHtml(s.label)}</span></li>`);

    box.innerHTML = `
        <svg class="columns-chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="${escapeHtml(description)}">
            ${grid}
            ${bars}
        </svg>
        <ul class="legend legend-row">${legend.join("")}</ul>`;
}

// "2026-10" -> "Oct 26"
function shortMonth(isoMonth) {
    const [year, month] = isoMonth.split("-").map(Number);
    return new Date(year, month - 1, 1).toLocaleDateString("en-GB", { month: "short", year: "2-digit" });
}

// picks a round step size that is at least `raw`: 1, 2, 5 or 10 times a power of ten
function roundStep(raw) {
    const power = 10 ** Math.floor(Math.log10(raw));
    for (const multiple of [1, 2, 5, 10]) {
        if (multiple * power >= raw) {
            return multiple * power;
        }
    }
    return 10 * power;
}

// 1500000 -> "1.5M", 250000 -> "250k", 800 -> "800"
function short(amount) {
    const oneDecimal = (n) => String(Math.round(n * 10) / 10);

    if (amount >= 1000000) {
        return oneDecimal(amount / 1000000) + "M";
    }
    if (amount >= 1000) {
        return oneDecimal(amount / 1000) + "k";
    }
    return String(amount);
}
