// Things every screen uses: the toast message, opening/closing pop-ups,
// and telling the page that data changed so it can reload.

let toastTimer = null;

export function showToast(message) {
    const toast = document.getElementById("toast");
    toast.textContent = message;
    toast.classList.add("show");

    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove("show"), 3500);
}

export function openModal(modalId) {
    document.getElementById(modalId).classList.remove("hidden");
}

export function closeModal(modalId) {
    document.getElementById(modalId).classList.add("hidden");
}

// Any element with a data-close attribute closes the modal it's inside.
// Clicking the dark background around a modal closes it too.
export function setUpModals() {
    document.querySelectorAll(".modal").forEach((modal) => {
        modal.addEventListener("click", (event) => {
            if (event.target === modal || event.target.closest("[data-close]")) {
                modal.classList.add("hidden");
            }
        });
    });
}

// After a loan or payment is saved, the screen on show needs fresh data.
// app.js listens for this and reloads the current screen.
const listeners = [];

export function onDataChanged(callback) {
    listeners.push(callback);
}

export function dataChanged() {
    listeners.forEach((callback) => callback());
}

// shows a table's rows, or the "nothing here" message if there are none
export function fillTable(tbodyId, emptyId, rowsHtml) {
    document.getElementById(tbodyId).innerHTML = rowsHtml.join("");
    document.getElementById(emptyId).classList.toggle("hidden", rowsHtml.length > 0);
}
