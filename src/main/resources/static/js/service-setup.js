(() => {
    document.querySelectorAll('[data-setup-section]').forEach(section => {
        const tools = section.querySelector('[data-setup-tools]');
        const search = section.querySelector('[data-setup-search]');
        const filter = section.querySelector('[data-setup-filter]');
        const records = [...section.querySelectorAll('[data-setup-record]')];
        const update = () => {
            const query = search.value.trim().toLocaleLowerCase();
            const mode = filter?.value || 'all';
            let visible = 0;
            records.forEach(record => {
                const matches = (record.dataset.search || '').toLocaleLowerCase().includes(query)
                    && (mode === 'all' || (mode === 'unmapped' ? record.dataset.unmapped === 'true' : record.dataset.status === mode));
                record.hidden = !matches;
                if (matches) visible++;
            });
            section.querySelector('[data-setup-count]').textContent = `${visible} of ${records.length} shown`;
            section.querySelector('[data-setup-empty]').hidden = visible !== 0 || records.length === 0;
        };
        tools.hidden = false;
        search.addEventListener('input', update);
        filter?.addEventListener('change', update);
        if (filter) document.querySelector('[data-show-unmapped]')?.addEventListener('click', () => {
            search.value = ''; filter.value = 'unmapped'; update();
        });
        update();
    });
})();
