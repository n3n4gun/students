let token = null;

const loginForm = document.querySelector("#login-form");
const emailInput = document.querySelector("#email");
const passwordInput = document.querySelector("#password");
const message = document.querySelector("#message");
const loadButton = document.querySelector("#load-students");
const studentsList = document.querySelector("#students");
const loginButton = loginForm.querySelector('button[type="submit"]');

function showMessage(text, isError = false) {
    message.textContent = text;
    message.classList.toggle("error", isError);
}

// Вход в систему
loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    token = null;
    loadButton.hidden = true;
    studentsList.replaceChildren();
    loginButton.disabled = true;
    showMessage("Выполняется вход…");

    try {
        const response = await fetch("/api/v1/auth/login", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                email: emailInput.value.trim(),
                password: passwordInput.value
            })
        });

        if (!response.ok) {
            if ([401, 403].includes(response.status)) {
                throw new Error("Неверный email или пароль.");
            }

            throw new Error(`Ошибка входа: ${response.status}.`);
        }

        const data = await response.json();
        token = data.token;
        passwordInput.value = "";

        const canViewStudents = data.roles.some(role =>
            ["ROLE_ADMIN", "ROLE_TEACHER"].includes(role)
        );

        loadButton.hidden = !canViewStudents;

        showMessage(
            canViewStudents
                ? "Вход выполнен. Можно загрузить студентов."
                : "Вход выполнен. У этой учётной записи нет доступа к списку студентов."
        );
    } catch (error) {
        showMessage(
            error instanceof TypeError
                ? "Не удалось соединиться с сервером."
                : error.message,
            true
        );
    } finally {
        loginButton.disabled = false;
    }
});

// Получение списка студентов
loadButton.addEventListener("click", async () => {
    if (!token) {
        showMessage("Сначала войди в систему.", true);
        return;
    }

    loadButton.disabled = true;
    showMessage("Загружаем студентов…");

    try {
        const response = await fetch("/api/v1/student", {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });

        if (response.status === 401) {
            token = null;
            loadButton.hidden = true;
            studentsList.replaceChildren();
            throw new Error("Срок авторизации истёк. Войди снова.");
        }

        if (response.status === 403) {
            throw new Error("Нет прав для просмотра студентов.");
        }

        if (!response.ok) {
            throw new Error(`Ошибка загрузки: ${response.status}.`);
        }

        const data = await response.json();
        studentsList.replaceChildren();

        for (const student of data.response) {
            const item = document.createElement("li");

            item.textContent =
                `${student.id}: ${student.name} ${student.surname}`;

            studentsList.append(item);
        }

        showMessage(
            data.totalCount === 0
                ? "Студентов пока нет."
                : `Всего студентов: ${data.totalCount}`
        );
    } catch (error) {
        showMessage(
            error instanceof TypeError
                ? "Не удалось соединиться с сервером."
                : error.message,
            true
        );
    } finally {
        loadButton.disabled = false;
    }
});