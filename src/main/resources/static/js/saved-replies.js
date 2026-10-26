(() => {
    const select = document.getElementById('saved-reply');
    const button = document.getElementById('insert-saved-reply');
    const text = document.getElementById('comment-text');
    if (!select || !button || !text) return;
    button.addEventListener('click', () => {
        const message = select.selectedOptions[0]?.dataset.message;
        if (!message) {document.getElementById('saved-reply-status').textContent = 'Choose a template first.'; return;}
        text.value = text.value.trim() ? `${text.value}\n\n${message}` : message;
        text.dispatchEvent(new Event('input', {bubbles:true})); text.focus();
        document.getElementById('saved-reply-status').textContent = 'Reply inserted. Review and personalize it before sending.';
    });
})();
