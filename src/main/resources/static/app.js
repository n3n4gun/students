const routes = { SUPER_ADMIN: "/superadmin.html", ADMIN: "/admin.html", TEACHER: "/teacher.html" };
const page = document.body.dataset.page;
const message = document.querySelector("#message");
let session;
try { session = JSON.parse(sessionStorage.getItem("student-session")); } catch { sessionStorage.removeItem("student-session"); }
function home(roles) { return Object.keys(routes).find(role => roles?.includes("ROLE_" + role)); }
function active() { return session?.token && session.expiresAt > Date.now() && home(session.roles); }
function show(text, error = false) {
    message.textContent = text;
    message.classList.toggle("error", error);
}
function logout() {
    sessionStorage.removeItem("student-session");
    location.replace("/index.html");
}
async function api(path, options = {}) {
    if (!active()) { logout(); throw new Error("Войдите снова."); }
    let response;
    try {
        response = await fetch(path, { ...options, headers: {
            ...(options.body ? { "Content-Type": "application/json" } : {}),
            Authorization: "Bearer " + session.token
        } });
    } catch { throw new Error("Не удалось соединиться с сервером."); }
    if (response.status === 401) { logout(); throw new Error("Войдите снова."); }
    if (response.status === 403) throw new Error("Недостаточно прав для этой операции.");
    if (!response.ok) throw new Error("Ошибка запроса: " + response.status);
    const text = await response.text();
    return text ? JSON.parse(text) : null;
}
function button(text, action) {
    const element = document.createElement("button");
    element.type = "button";
    element.textContent = text;
    element.addEventListener("click", async () => {
        element.disabled = true;
        try { await action(); } catch (error) { show(error.message, true); }
        finally { element.disabled = false; }
    });
    return element;
}
async function load() {
    show("Загрузка…");
    const users = page === "SUPER_ADMIN";
    const endpoint = users ? "/api/v1/users" : "/api/v1/student";
    const data = await api(endpoint);
    const list = document.querySelector("#records");
    list.replaceChildren();
    for (const record of (users ? data.responses : data.response)) {
        const item = document.createElement("li");
        const title = document.createElement("p");
        title.textContent = users
            ? record.email + " — " + record.roles.join(", ")
            : record.name + " " + record.surname;
        item.append(title);
        if (!users) {
            item.append(button("Контакты", async () => {
                const details = await api(endpoint + "/" + record.id);
                let info = item.querySelector(".contacts");
                if (!info) { info = document.createElement("p"); info.className = "contacts"; item.append(info); }
                info.textContent = "Email: " + (details.email || "не указан") + " · Телефон: " + (details.phoneNumber || "не указан");
            }));
        }
        if (page !== "TEACHER") {
            item.append(button("Удалить", async () => {
                if (!confirm("Удалить " + (users ? record.email : record.name + " " + record.surname) + "?")) return;
                await api(endpoint + "/" + record.id, { method: "DELETE" });
                await load();
            }));
        }
        list.append(item);
    }
    show(data.totalCount ? "Всего: " + data.totalCount : "Список пуст.");
}
if (page === "login") {
    if (active()) location.replace(routes[home(session.roles)]);
    document.querySelector("#login-form").addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const submit = form.querySelector("button");
        submit.disabled = true;
        show("Выполняется вход…");
        try {
            const response = await fetch("/api/v1/auth/login", {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email: form.elements.email.value.trim(), password: form.elements.password.value })
            });
            if (!response.ok) throw new Error([401, 403].includes(response.status) ? "Неверный email или пароль." : "Ошибка входа: " + response.status);
            const data = await response.json();
            const role = home(data.roles);
            if (!role || !data.token || !(data.expiresIn > 0)) throw new Error("Не удалось определить доступный кабинет.");
            sessionStorage.setItem("student-session", JSON.stringify({
                token: data.token, roles: data.roles, expiresAt: Date.now() + data.expiresIn * 1000
            }));
            form.reset();
            location.replace(routes[role]);
        } catch (error) {
            show(error instanceof TypeError ? "Не удалось соединиться с сервером." : error.message, true);
        } finally { submit.disabled = false; }
    });
} else if (!active()) {
    logout();
} else if (home(session.roles) !== page) {
    location.replace(routes[home(session.roles)]);
} else {
    document.querySelector("main").hidden = false;
    document.querySelector("#logout").addEventListener("click", logout);
    document.querySelector("#refresh").addEventListener("click", () => load().catch(error => show(error.message, true)));
    document.querySelector("#create-form")?.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const submit = form.querySelector("button");
        submit.disabled = true;
        const values = Object.fromEntries(new FormData(form));
        if (page === "SUPER_ADMIN") { values.roles = [values.role]; delete values.role; }
        try {
            await api(page === "SUPER_ADMIN" ? "/api/v1/users/save" : "/api/v1/student/save", {
                method: "POST", body: JSON.stringify(values)
            });
            form.reset();
            await load();
        } catch (error) { show(error.message, true); }
        finally { submit.disabled = false; }
    });
    load().catch(error => show(error.message, true));
}
window.addEventListener("pageshow", event => {
    if (event.persisted) location.reload();
});
