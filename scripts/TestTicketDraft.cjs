const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const source=fs.readFileSync('src/main/resources/static/js/ticket-draft.js','utf8');
const settle=()=>new Promise(resolve=>setImmediate(resolve));
function setup(load, postResponse) {
    const events={},posts=[],elements={},requests=[];let timer;
    for(const id of ['ticket-form','draft-panel','draft-status','draft-actions','restore-draft','discard-draft','categoryId','assetId','ticketType','subject','description','subtypeDetail-incident','subtypeDetail-service','severity'])
        elements[id]={value:'',hidden:true,listeners:{},addEventListener(name,fn){this.listeners[name]=fn;},dispatchEvent(){}};
    elements['ticket-form'].addEventListener=(name,fn)=>events[name]=fn;
    elements['ticket-form'].querySelector=()=>({value:'csrf-test-token'});
    elements['draft-panel'].dataset={endpoint:'/student/draft'};
    elements.ticketType.value='INCIDENT';
    vm.runInNewContext(source,{document:{getElementById:id=>elements[id]},window:{addEventListener(){}},
        AbortSignal:{timeout(ms){assert.equal(ms,10000);const controller=new AbortController();requests.push(controller);return controller.signal;}},
        setTimeout:(fn,delay)=>{if(delay===0)return setImmediate(fn);timer=fn;return 1;},clearTimeout(){},Event:class {},
        fetch:async (path,options)=>{if(!options.method)return load(options);posts.push({path,options});return typeof postResponse==='function' ? postResponse(path,options) : postResponse || {ok:true,status:path.endsWith('/clear')?204:200,headers:{get:()=> 'application/json'},json:async()=>({})};}});
    return {elements,posts,events,requests,edit(){events.input({target:{type:'text'}});timer();}};
}
test('failed initial draft loading never overwrites an existing SQL draft',async()=>{
    const state=setup(()=>Promise.reject(new Error('Offline')));await settle();
    state.elements.subject.value='New unsaved text';state.edit();await settle();
    assert.equal(state.posts.length,0);assert.match(state.elements['draft-status'].textContent,/Autosave is paused/);
});
test('a stored draft requires an explicit restore choice before autosaving',async()=>{
    const state=setup(()=>({ok:true,status:200,headers:{get:()=> 'application/json'},json:async()=>({data:{subject:'Previously saved work',ticketType:'INCIDENT'}})}));await settle();
    state.elements.subject.value='New unsaved text';state.edit();await settle();assert.equal(state.posts.length,0);
    state.elements['restore-draft'].listeners.click();assert.equal(state.elements.subject.value,'Previously saved work');
    state.edit();await settle();assert.equal(state.posts.length,1);
    assert.equal(state.posts[0].options.headers['X-CSRF-TOKEN'],'csrf-test-token');
});
test('an empty inbox draft response enables private partial autosave',async()=>{
    const state=setup(()=>({status:204}));await settle();state.elements.subject.value='Partial request';state.edit();await settle();
    assert.equal(state.posts.length,1);const data=JSON.parse(state.posts[0].options.body);
    assert.equal(data.subject,'Partial request');assert.equal(data.categoryId,null);assert.equal('userId' in data,false);
    assert.match(state.elements['draft-status'].textContent,/Draft saved/);
});

test('validation cancelled after waiting for autosave resumes draft saving',async()=>{
    const state=setup(()=>({status:204}));await settle();
    state.elements['ticket-form'].checkValidity=()=>false;
    state.elements['ticket-form'].reportValidity=()=>false;
    state.elements['ticket-form'].requestSubmit=()=>{};
    await state.events.submit({preventDefault(){},submitter:null});
    await settle();
    state.elements.subject.value='Corrected request';state.edit();await settle();
    assert.equal(state.posts.length,1);
});
test('rapid double submit requests only one native submission',async()=>{
    const state=setup(()=>({status:204}));await settle();let submitted=0;
    state.elements['ticket-form'].checkValidity=()=>true;
    state.elements['ticket-form'].requestSubmit=()=>submitted++;
    await Promise.all([state.events.submit({preventDefault(){}}),state.events.submit({preventDefault(){}})]);
    await settle();
    assert.equal(submitted,1);
});

test('an expired session cannot report a draft cleared or enable overwriting',async()=>{
    const state=setup(()=>({ok:true,status:200,headers:{get:()=> 'application/json'},json:async()=>({data:{subject:'Saved work'}})}),
        {ok:true,status:200,redirected:true,headers:{get:()=> 'text/html'}});
    await settle();await state.elements['discard-draft'].listeners.click();
    assert.equal(state.elements['draft-actions'].hidden,false);
    assert.match(state.elements['draft-status'].textContent,/Could not clear/);
    state.edit();await settle();assert.equal(state.posts.length,1);
});

test('a successful clear response enables saving a new draft',async()=>{
    const state=setup(()=>({ok:true,status:200,headers:{get:()=> 'application/json'},json:async()=>({data:{subject:'Saved work'}})}));
    await settle();await state.elements['discard-draft'].listeners.click();
    assert.equal(state.elements['draft-actions'].hidden,true);
    state.edit();await settle();assert.equal(state.posts.length,2);
});

test('a redirected autosave cannot report successful persistence',async()=>{
    const state=setup(()=>({status:204}),{ok:true,status:200,redirected:true,headers:{get:()=> 'application/json'},json:async()=>({})});
    await settle();state.edit();await settle();
    assert.match(state.elements['draft-status'].textContent,/Draft could not be saved/);
});

test('a stalled autosave times out so ticket submission can continue',async()=>{
    const state=setup(()=>({status:204}),(_path,options)=>new Promise((resolve,reject)=>{
        options.signal?.addEventListener('abort',()=>reject(new Error('Timed out')));
    }));
    await settle();state.edit();await settle();
    let submitted=0;
    state.elements['ticket-form'].checkValidity=()=>true;
    state.elements['ticket-form'].requestSubmit=()=>submitted++;
    const submission=state.events.submit({preventDefault(){}});
    assert.equal(submitted,0);
    assert.ok(state.posts[0].options.signal,'Autosave must have a request deadline');
    state.requests.forEach(controller=>controller.abort());
    await submission;
    await settle();
    assert.equal(submitted,1);
    assert.match(state.elements['draft-status'].textContent,/could not be saved/);
});

test('a stalled initial draft load times out without enabling overwrites',async()=>{
    let signal;
    const state=setup(options=>new Promise((resolve,reject)=>{
        signal=options.signal;
        signal?.addEventListener('abort',()=>reject(new Error('Timed out')));
    }));
    assert.ok(signal,'Draft loading must have a request deadline');
    state.requests.forEach(controller=>controller.abort());await settle();
    state.edit();await settle();
    assert.equal(state.posts.length,0);
    assert.match(state.elements['draft-status'].textContent,/Autosave is paused/);
});

test('equipment selection survives autosave and draft restoration',async()=>{
    const state=setup(()=>({ok:true,status:200,headers:{get:()=> 'application/json'},json:async()=>({data:{assetId:42,subject:'Printer issue',ticketType:'INCIDENT'}})}));
    await settle();state.elements['restore-draft'].listeners.click();
    assert.equal(state.elements.assetId.value,42);
    state.edit();await settle();
    assert.equal(JSON.parse(state.posts[0].options.body).assetId,42);
});

test('native resubmission waits until the original submit event finishes dispatching',async()=>{
    const state=setup(()=>({status:204}));await settle();
    let dispatching=true,submitted=0;
    state.elements['ticket-form'].checkValidity=()=>true;
    state.elements['ticket-form'].requestSubmit=()=>{if(!dispatching)submitted++;};
    await state.events.submit({preventDefault(){}});
    assert.equal(submitted,0);
    dispatching=false;await settle();
    assert.equal(submitted,1);
});
