(() => {
    const panel = document.getElementById('faq-suggestions');
    if (!panel) return;
    const subject = document.getElementById('subject');
    const category = document.getElementById('categoryId');
    const results = document.getElementById('suggestion-results');
    const status = document.getElementById('suggestion-status');
    let timer, controller, sequence = 0;
    async function update() {
        const request = ++sequence;
        controller?.abort();
        results.replaceChildren();
        const query = subject.value.trim();
        if (!category.value && query.length < 3) {
            status.textContent = 'Choose a category or enter at least three characters to find guides.';
            return;
        }
        controller = new AbortController();
        const params = new URLSearchParams({q: query.length >= 3 ? query : ''});
        if (category.value) params.set('categoryId', category.value);
        status.textContent = 'Looking for helpful guides…';
        try {
            const response = await fetch(`${panel.dataset.endpoint}?${params}`, {signal: controller.signal, headers: {Accept: 'application/json'}});
            if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw new Error('Unavailable');
            const articles = await response.json();
            if (request !== sequence) return;
            status.textContent = articles.length ? 'These guides may answer your question. Your ticket form stays here.' : 'No matching guides yet. You can still submit your ticket below.';
            for (const article of articles) {
                const link = document.createElement('a');
                link.className = 'suggestion-link';
                const url = new URL(panel.dataset.faqUrl, location.origin);
                url.searchParams.set('q', article.question);
                link.href = url.href; link.target = '_blank'; link.rel = 'noopener';
                const title = document.createElement('strong'); title.textContent = article.question;
                const excerpt = document.createElement('span'); excerpt.textContent = article.excerpt;
                link.append(title, excerpt); results.append(link);
            }
        } catch (error) {
            if (error.name !== 'AbortError' && request === sequence) status.textContent = 'Guides are unavailable right now. You can continue submitting your ticket.';
        }
    }
    subject.addEventListener('input', () => {clearTimeout(timer); timer = setTimeout(update, 350);});
    category.addEventListener('change', () => {clearTimeout(timer); update();});
    update();
})();
