const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
class Element {
 constructor(tag) { this.tag=tag; this.children=[]; this.textContent=''; this.hidden=false; this.listeners={}; }
 append(item) { this.children.push(item); }
 replaceChildren() { this.children=[]; }
 addEventListener(name, action) { this.listeners[name]=action; }
}
const sandbox={window:{}, document:{createElement: tag=>new Element(tag)}, Date, JSON};
vm.runInNewContext(fs.readFileSync('dual-server/src/main/resources/static/native-permissions.js','utf8'),sandbox);
const render=sandbox.window.DualNativePermissions.render;
const all=(e)=>[e,...e.children.flatMap(all)];
(async()=>{
 const container=new Element('main');const calls=[];
 const row={request_key:'key',execution:'execution',status:'REQUEST_PENDING',can_decide:true,can_cancel:true,request:{deadlineMs:Date.now()+60000},raw_request:{toolCall:{title:'<script>not executable</script>'}},options:[{optionId:'exact-allow',name:'Once'},{optionId:'exact-deny',name:'Deny'}]};
 render(container,{requests:[row]}, async(...args)=>calls.push(args));
 assert.equal(all(container).filter(e=>e.tag==='button').length,3);
 const button=all(container).find(e=>e.tag==='button');await button.listeners.click();
 assert.equal(calls[0][0],'decide');assert.equal(calls[0][1].optionId,'exact-allow');assert.equal(calls[0][1].requestKey,'key');assert.equal(button.disabled,true);
 assert.ok(all(container).some(e=>e.textContent==='<script>not executable</script>'));
 render(container,{requests:[{...row,status:'HISTORICAL',can_decide:false,can_cancel:false}]},()=>{throw Error('history has no actions')});
 assert.equal(all(container).filter(e=>e.tag==='button').length,0);
 render(container,{status:'BUSY',reason:'writer in progress',requests:[]},()=>{});assert.equal(container.hidden,false);assert.equal(all(container).filter(e=>e.tag==='button').length,0);
 render(container,{requests:[]},()=>{});assert.equal(container.hidden,true);
 for(const [html,js] of [['index.html','console.js'],['mgt/index.html','mgt/app.js']]) {
  const prefix='dual-server/src/main/resources/static/';assert.match(fs.readFileSync(prefix+html,'utf8'),/native-permissions\.js/);assert.match(fs.readFileSync(prefix+js,'utf8'),/DualNativePermissions\.render/);
 }
 console.log('PASS: shared DOM projection; exact choice, history/BUSY zero action, text-safe details. This is not a real browser proof.');
})().catch(error=>{console.error(error);process.exitCode=1;});
