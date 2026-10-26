(() => {
    document.querySelectorAll('[data-password-toggle]').forEach(button => {
        const input = document.getElementById(button.dataset.passwordToggle);
        if (!input) return;
        button.hidden = false;
        button.addEventListener('click', () => {
            const visible = input.type === 'password';
            input.type = visible ? 'text' : 'password';
            button.textContent = visible ? 'Hide' : 'Show';
            button.setAttribute('aria-pressed', String(visible));
        });
    });
    document.querySelectorAll('form[data-confirm]').forEach(form => {
        form.addEventListener('submit', event => {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });
    const password = document.getElementById('create-password');
    password?.addEventListener('input', () => {
        password.setCustomValidity(new TextEncoder().encode(password.value).length > 72
            ? 'Password must be at most 72 UTF-8 bytes. Use fewer characters.' : '');
    });
})();
