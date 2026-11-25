const fs=require('fs'), vm=require('vm'), assert=require('node:assert/strict');
function fn(source,name){const start=source.indexOf(`function ${name}(`); assert.ok(start>=0,name); const tail=source.slice(start);const next=tail.slice(1).search(/\n(?:async )?function /);return next<0?tail:tail.slice(0,next+1);}
const checks=[];
for(const side of ['console','mgt']){
 const source=fs.readFileSync(side==='console'?'dual-server/src/main/resources/static/console.js':'dual-server/src/main/resources/static/mgt/app.js','utf8');
 let calls=[], prompts=[], confirms=[];let delivery;
 const context={crypto:{randomUUID:()=> 'ui-new-id'},Date,JSON,asList:x=>Array.isArray(x)?x:[],
 window:{prompt:()=>prompts.shift(),confirm:()=>confirms.shift()},postAction:(url,body)=>calls.push({url,body}),
 state:{selectedId:'ws-ui'},selectedWorkdirParam:()=> 'root',selectedBase:()=> '/api/workstreams/ws-ui',workdirParam:()=> 'root',deliveryView:()=>delivery};
 vm.createContext(context); vm.runInContext(fn(source,'resolveDelivery'),context);
 const template={schema:'dual.delivery-resolution.v1',operation_id:'old-operation',plan_sha256:'sha',candidate:{ref:'candidate',sha256:'candidate-sha'},frozen_runtime_identity:{binary:'identity'},stop_basis:null};
 const invoke=(disposition)=>side==='console'?context.resolveDelivery(disposition):context.resolveDelivery(delivery,disposition);
 delivery={currentActions:[],resolutionTemplate:template}; invoke('AUTHORIZE_NEW_APPLY');assert.equal(calls.length,0);
 delivery={currentActions:['RESOLVE_AUTHORIZE_NEW_APPLY'],resolutionTemplate:template};prompts=['reason','actual check'];confirms=[false];invoke('AUTHORIZE_NEW_APPLY');assert.equal(calls.length,0);
 prompts=['reason','actual check'];confirms=[true,true];invoke('AUTHORIZE_NEW_APPLY');assert.equal(calls.length,1);
 assert.equal(calls[0].body.operation_id,'old-operation'); assert.equal(calls[0].body.plan_sha256,'sha');
 assert.deepEqual(JSON.parse(JSON.stringify(calls[0].body.candidate)),template.candidate);
 assert.equal(calls[0].body.stop_basis.type,'OPERATOR_CONFIRMED_STOPPED');assert.equal(calls[0].body.request_id,'ui-new-id');
 calls=[];delivery={currentActions:['RESOLVE_CLOSE_OR_CONFIRM_STOP'],resolutionTemplate:template};prompts=['close only'];confirms=[false,true];invoke('CLOSE_APPLICATION');assert.equal(calls.length,1);assert.equal(calls[0].body.stop_basis,null);
 checks.push({surface:side,tests:['no-current-action-zero-post','cancelled-stop-zero-post','explicit-authorize-keeps-owner-binding','close-only-does-not-assert-stop'],pass:true});
 if(side==='mgt'){
  const make=()=>({children:[],textContent:'',innerHTML:'',className:'',appendChild(x){this.children.push(x)},addEventListener(){}});
  context.document={createElement:make};context.els={overviewDeliveryMerge:make()};context.escapeHtml=x=>String(x).replaceAll('<','&lt;');
  vm.runInContext(fn(source,'renderOverviewDelivery'),context);
  context.renderOverviewDelivery({status:'COMMITTED',historicalStatus:'COMMITTED',currentActions:[],nextCommands:['inspect actual target'],stopBasis:'PROGRAM_OBSERVED_TERMINATION'});
  const nodes=context.els.overviewDeliveryMerge.children.flatMap(x=>x.children);
  assert.ok(nodes.some(x=>x.textContent==='inspect actual target'));assert.ok(!nodes.some(x=>x.textContent.includes('Authorize')));
 }
}
fs.writeFileSync('output/lifecycle-batch-06-20261009/implementation/ui-verification.json',JSON.stringify({status:'PASS',kind:'Node VM projection and form probes, not browser visual proof',checks},null,2)+'\n');
