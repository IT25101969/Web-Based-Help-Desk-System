(() => {
    const form = document.getElementById('ticket-form'), panel = document.getElementById('draft-panel');
    if (!form || !panel) return;
    const status = document.getElementById('draft-status'), actions = document.getElementById('draft-actions');
    const token = form.querySelector('input[name="_csrf"]')?.value;
    let ready = false, paused = false, submitting = false, timer, stored;
    let pending = Promise.resolve();
    const value = id => document.getElementById(id)?.value || '';
    const capture = () => ({categoryId: value('categoryId') ? Number(value('categoryId')) : null,assetId:value('assetId') ? Number(value('assetId')) : null,ticketType:value('ticketType') || null,subject:value('subject'),description:value('description'),incidentDetail:value('subtypeDetail-incident'),serviceDetail:value('subtypeDetail-service'),severity:value('severity')});
    async function request(path, data) {
        const response = await fetch(panel.dataset.endpoint + path, {method:'POST',signal:AbortSignal.timeout(10000),headers:{'Content-Type':'application/json','X-CSRF-TOKEN':token,Accept:'application/json'},body:data ? JSON.stringify(data) : undefined});
        if (!response.ok || response.redirected) throw new Error('Draft not saved');
        if (path === '/clear' && response.status !== 204) throw new Error('Draft not cleared');
        if (path !== '/clear' && !response.headers.get('content-type')?.includes('application/json')) throw new Error('Draft not saved');
        return response.status === 204 ? null : response.json();
    }
    function save() {
        if (!ready || paused) return pending;
        status.textContent = 'Saving your draft…';
        const data = capture();
        pending = pending.then(async () => {
            try {await request('',data); status.textContent = 'Draft saved to your account.';}
            catch {status.textContent = 'Draft could not be saved. Keep this page open; we’ll retry when you reconnect or edit again.';}
        });
        return pending;
    }
    form.addEventListener('input', event => {if(event.target.type==='file')return;clearTimeout(timer);timer=setTimeout(save,1000);});
    form.addEventListener('change', event => {if(event.target.type==='file')return;clearTimeout(timer);timer=setTimeout(save,1000);});
    window.addEventListener('online',save);
    document.getElementById('restore-draft').addEventListener('click', () => {
        const data=stored.data;
        for (const [id,key] of Object.entries({categoryId:'categoryId',assetId:'assetId',ticketType:'ticketType',subject:'subject',description:'description','subtypeDetail-incident':'incidentDetail','subtypeDetail-service':'serviceDetail',severity:'severity'})) {
            const element=document.getElementById(id); if(element)element.value=data[key] ?? '';
        }
        document.getElementById('ticketType').dispatchEvent(new Event('change',{bubbles:true}));
        document.getElementById('categoryId').dispatchEvent(new Event('change',{bubbles:true}));
        actions.hidden=true; ready=true; status.textContent='Draft restored. Review the category and choose any attachment again.';
    });
    document.getElementById('discard-draft').addEventListener('click',async () => {
        try {await request('/clear');actions.hidden=true;ready=true;status.textContent='Ready for a new draft. Start typing to save.';}
        catch {status.textContent='Could not clear the saved draft. Please try again.';}
    });
    form.addEventListener('submit',async event => {
        if(submitting)return;
        event.preventDefault();
        if(paused)return;
        clearTimeout(timer);paused=true;
        await pending;
        // Resume in a new task: browsers can ignore requestSubmit while the original
        // submit event is still dispatching, even after awaiting a resolved promise.
        setTimeout(() => {
            if(!form.checkValidity()){paused=false;form.reportValidity();return;}
            submitting=true; form.requestSubmit(event.submitter || undefined);
        },0);
    });
    (async () => {
        try {
            const response=await fetch(panel.dataset.endpoint,{signal:AbortSignal.timeout(10000),headers:{Accept:'application/json'}});
            if(response.redirected)throw new Error('Unavailable');
            if(response.status===204){ready=true;status.textContent='Start typing to save your draft automatically.';return;}
            if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))throw new Error('Unavailable');
            stored=await response.json(); actions.hidden=false;
            status.textContent='You have a saved draft. Restore it or start a new draft to enable autosave.';
        } catch {ready=false;status.textContent='Draft service is unavailable. Autosave is paused to protect any existing draft. You can still submit this ticket; reload this page to retry draft loading.';}
    })();
})();
