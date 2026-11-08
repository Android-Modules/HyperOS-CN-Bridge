import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const catalog=JSON.parse(execFileSync('python3',['-c',"import zipfile; print(zipfile.ZipFile('dist/ishtar-cn-addons-OS3.0.308.0.zip').read('catalog.json').decode())"],{cwd:root}));
assert.equal(catalog.profile,'ishtar-elite308-addons');assert.equal(catalog.units.length,9);assert.equal(catalog.features.length,19);assert.equal(catalog.nativeFeatures.length,11);
const protectedPackages=['com.miui.tsmclient','com.miui.home','com.miui.gallery','com.xiaomi.account','com.android.providers.downloads','com.lbe.security.miui'];
for(const p of protectedPackages)assert.ok(!catalog.units.some(u=>u.package===p));
const files=new Set();for(const u of catalog.units){assert.equal(u.globalPresent,false);for(const f of u.files){assert.ok(f.path.startsWith('system/product/'));assert.ok(!files.has(f.path));files.add(f.path);}}
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE||'playwright');
const server=http.createServer(async(req,res)=>{try{const name=new URL(req.url,'http://localhost').pathname;assert.ok(['/','/index.html','/app.js','/style.css','/catalog.json'].includes(name));const body=name==='/catalog.json'?JSON.stringify(catalog):await fs.readFile(path.join(root,'ishtar/module/webroot',name==='/'?'index.html':name.slice(1)));res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':name.endsWith('.css')?'text/css':name.endsWith('.json')?'application/json':'text/html');res.end(body);}catch{res.writeHead(404);res.end();}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({headless:true,...(process.env.CHROME_EXECUTABLE?{executablePath:process.env.CHROME_EXECUTABLE}:{})});
try{
 const page=await browser.newPage({viewport:{width:393,height:852}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(({catalog})=>{
  const defaults=Object.fromEntries(catalog.features.map(f=>[f.id,{enabled:true,remove:true}]));
  window.qa={calls:[],rejectSave:false,state:{ok:true,desired:defaults,applied:structuredClone(defaults),actual:{'com.miui.gallery':{versionCode:'1',updated:false}},icons:{},pending:false,applying:false,result:{ok:true,message:'配置已在本次启动生效'},version:catalog.version,sourceAvailable:true}};
  window.ksu={exec(command,callback){qa.calls.push(command);setTimeout(()=>{let r=qa.state;if(command.includes(' save ')){if(qa.rejectSave){window[callback](1,JSON.stringify({ok:false,message:'LSPosed 未勾选所需应用'}),'');return;}r.desired=JSON.parse(atob(command.split(' ').at(-1)));r.pending=true;r.result={ok:true,message:'配置已保存，等待手动重启'};r={ok:true};}window[callback](0,JSON.stringify(r),'');},10);}};
 },{catalog});
 await page.goto('http://127.0.0.1:'+server.address().port);await page.waitForFunction(()=>document.querySelectorAll('[role=switch]').length===19);
 const symbol=()=>page.locator('.status-symbol > g:visible').evaluateAll(groups=>groups.map(g=>g.getAttribute('class')));
 assert.deepEqual(await symbol(),['status-ok']);
 assert.equal(await page.locator('#native [role=switch]').count(),11);
 const voice=page.getByRole('switch',{name:'小爱语音',exact:true});await voice.click();assert.ok(await page.locator('input[value=delete]').isChecked());await page.getByRole('button',{name:'取消',exact:true}).click();assert.equal(await voice.getAttribute('aria-checked'),'true');
 await voice.click();await page.getByRole('button',{name:'确认关闭',exact:true}).click();assert.equal(await page.evaluate(()=>qa.calls.filter(c=>c.includes(' save ')).length),0);
 await page.getByRole('button',{name:'保存，手动重启生效',exact:true}).click();await page.waitForFunction(()=>qa.state.desired.voice.enabled===false);await page.waitForFunction(()=>document.querySelector('#phase').textContent==='等待手动重启');assert.equal(await page.evaluate(()=>qa.state.desired.voice.remove),true);
 const portal=page.getByRole('switch',{name:'传送门',exact:true});await portal.click();await page.locator('input[value=keep]').check();await page.getByRole('button',{name:'确认关闭',exact:true}).click();await page.getByRole('button',{name:'保存，手动重启生效',exact:true}).click();await page.waitForFunction(()=>qa.state.desired.portal.remove===false);
 assert.deepEqual(await symbol(),['status-wait']);
 await page.getByRole('button',{name:'全部启用',exact:true}).click();await page.getByRole('button',{name:'保存，手动重启生效',exact:true}).click();await page.waitForFunction(()=>Object.values(qa.state.desired).every(x=>x.enabled));
 await page.waitForFunction(()=>!document.querySelector('#refresh').disabled);await page.evaluate(()=>{qa.state.pending=false;qa.state.applying=true;qa.state.result={ok:false,message:'组件未加载'};});await page.getByRole('button',{name:'刷新',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#phase').textContent==='应用失败');assert.equal(await page.locator('#phase').textContent(),'应用失败');
 await page.evaluate(()=>{qa.state.applying=false;qa.state.result={ok:true,message:'配置已在本次启动生效'};qa.state.icons={gallery:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1cAAAAASUVORK5CYII='};});await page.getByRole('button',{name:'刷新',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#phase').textContent==='配置已生效');assert.equal(await page.locator('#native img').count(),1);
 await page.evaluate(()=>qa.rejectSave=true);await voice.click();await page.getByRole('button',{name:'确认关闭',exact:true}).click();await page.getByRole('button',{name:'保存，手动重启生效',exact:true}).click();await page.getByRole('alert').filter({hasText:'LSPosed 未勾选'}).waitFor();assert.equal(await page.evaluate(()=>qa.state.desired.voice.enabled),true);assert.equal(await page.getByRole('button',{name:'保存，手动重启生效',exact:true}).isEnabled(),true);
 assert.ok(await page.evaluate(()=>qa.calls.every(c=>c.startsWith('sh /data/adb/modules/ishtar_cn_addons/config.sh ')&&!c.includes('reboot'))));
 // Scope restoration stays visibly pending until framework authorization and retry.
 await page.locator('#all').click();
 await page.evaluate(()=>{qa.state.waitingScope=true;qa.state.result={ok:false,message:'请允许 LSPosed 作用域请求'};});
 await page.locator('#refresh').click();await page.waitForFunction(()=>document.querySelector('#phase').textContent==='等待作用域授权');
 assert.ok(await page.locator('#retry-scope').isVisible());assert.deepEqual(await symbol(),['status-wait']);
 assert.ok((await page.locator('#result').textContent()).includes('若未出现通知'));
 await page.locator('#retry-scope').click();await page.waitForFunction(()=>qa.calls.some(c=>c.endsWith(' retry')));
 await page.waitForFunction(()=>!document.querySelector('#refresh').disabled);
 await page.evaluate(()=>{qa.state.waitingScope=false;qa.state.result={ok:true,message:'配置已在本次启动生效'};});
 // A success result from a previous boot must not be presented as verified now.
 await page.locator('#all').click();await page.evaluate(()=>{qa.state.bootVerified=false;});await page.locator('#refresh').click();
 await page.waitForFunction(()=>document.querySelector('#phase').textContent==='等待启动校验');
 assert.deepEqual(await symbol(),['status-wait']);
 assert.ok(await voice.isDisabled());assert.ok(await page.locator('#save').isDisabled());
 await page.evaluate(()=>{qa.state.result={ok:false,message:'组件文件未正确挂载'};});await page.locator('#refresh').click();
 await page.waitForFunction(()=>document.querySelector('#phase').textContent==='应用失败');
 assert.deepEqual(await symbol(),['status-failed']);
 await page.evaluate(()=>{qa.state.bootVerified=true;qa.state.result={ok:true,message:'配置已在本次启动生效'};});await page.locator('#refresh').click();
 await page.waitForFunction(()=>document.querySelector('#phase').textContent==='配置已生效');
 await voice.click();await page.getByRole('button',{name:'确认关闭',exact:true}).click();
 // Changing tabs and reading details must preserve a rejected, unsaved draft.
 await page.locator('#tab-native').click();assert.ok(await page.locator('#panel-features').isHidden());
 await page.getByRole('button',{name:'查看相册详情',exact:true}).click();
 assert.ok(await page.locator('#component-info').isVisible());assert.ok((await page.locator('#component-packages').textContent()).includes('com.miui.gallery'));
 await page.keyboard.press('Escape');assert.ok(await page.locator('#component-info').isHidden());
 assert.equal(await page.evaluate(()=>document.activeElement.id),'detail-gallery');
 await page.locator('#tab-native').focus();await page.keyboard.press('ArrowLeft');
 assert.equal(await page.locator('#tab-features').getAttribute('aria-selected'),'true');
 assert.equal(await voice.getAttribute('aria-checked'),'false');assert.ok(await page.locator('#save').isEnabled());
 await page.getByRole('button',{name:'刷新',exact:true}).click();await page.getByRole('alert').filter({hasText:'请先保存'}).waitFor();
 assert.equal(await voice.getAttribute('aria-checked'),'false');
 // Discard the draft through the existing all-on action, then check a missing source.
 await page.locator('#all').click();await page.evaluate(()=>{qa.state.sourceAvailable=false;});await page.locator('#refresh').click();
 await page.waitForFunction(()=>document.querySelector('#source-badge').textContent==='离线安装源缺失');
 await page.evaluate(()=>{qa.state.sourceAvailable=true;qa.rejectSave=false;});await page.locator('#refresh').click();
 await page.waitForFunction(()=>!document.querySelector('#refresh').disabled);
 for(const width of [320,360,393,850]){await page.setViewportSize({width,height:852});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}
 // Every individual switch must save exactly one selection, including native apps.
 for(const f of catalog.features) {
   await page.locator('#none').click();
   await page.locator(f.native?'#tab-native':'#tab-features').click();
   await page.locator('#switch-'+f.id).click();
   await page.locator('#save').click();
   await page.waitForFunction(id=>qa.state.desired[id].enabled && Object.values(qa.state.desired).filter(v=>v.enabled).length===1,f.id);
   await page.waitForFunction(()=>!document.querySelector('#refresh').disabled);
   const selected=await page.evaluate(()=>Object.entries(qa.state.desired).filter(([,v])=>v.enabled).map(([id])=>id));
   assert.deepEqual(selected,[f.id]);
   assert.equal(await page.locator('#enabled-count').textContent(),'1 / 19 项配置开启');
 }
 await page.locator('#all').click();await page.locator('#save').click();
 await page.waitForFunction(()=>Object.values(qa.state.desired).every(v=>v.enabled));
 await page.waitForFunction(()=>!document.querySelector('#refresh').disabled);
 await page.evaluate(()=>{qa.state.pending=false;});await page.locator('#refresh').click();
 await page.waitForFunction(()=>document.querySelector('#phase').textContent==='配置已生效');
 await page.locator('#tab-features').click();
 // Screenshots use a viewport capture; fixed footers must not repeat in stitched full-page captures.
 await page.setViewportSize({width:393,height:852});
 if(process.env.WEBUI_STATUS_FIXTURE){const fixture=JSON.parse(await fs.readFile(process.env.WEBUI_STATUS_FIXTURE,'utf8'));await page.evaluate(data=>{qa.state.icons=data.icons;qa.state.actual=data.actual;},{icons:fixture.icons,actual:fixture.actual});await page.locator('#refresh').click();await page.waitForFunction(()=>!document.querySelector('#refresh').disabled);}
 await page.evaluate(()=>{window.scrollTo(0,0);document.querySelector('#toast').hidden=true;});
 await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
 const shots=path.join(root,'diagnostics/ishtar-adaptation/webui');await fs.mkdir(shots,{recursive:true});
 await page.screenshot({path:path.join(shots,'light.png')});
 await page.emulateMedia({colorScheme:'dark'});await page.screenshot({path:path.join(shots,'dark.png')});
 await page.locator('#tab-native').click();await page.screenshot({path:path.join(shots,'native-dark.png')});
 await page.locator('#tab-features').click();await voice.click();await page.screenshot({path:path.join(shots,'disable-dark.png')});
 await page.keyboard.press('Escape');assert.equal(await voice.getAttribute('aria-checked'),'true');
 // A missing bridge can be retried without reloading the WebUI.
 await page.addInitScript(()=>{delete window.ksu;});await page.reload();await page.waitForFunction(()=>document.querySelector('#phase').textContent==='无法连接模块');
 assert.ok(await page.locator('#refresh').isEnabled());assert.ok(await page.locator('#save').isDisabled());
 await page.evaluate(()=>{window.ksu={exec(command,callback){window[callback](0,JSON.stringify(qa.state),'');}};});
 await page.locator('#refresh').click();await page.waitForFunction(()=>document.querySelector('#phase').textContent==='配置已生效');
 assert.deepEqual(errors,[]);console.log('PASS: 19 independent selections, 9 addon units, protected native apps, defaults, delete/retain/cancel, manual reboot, boot states, icons, scope rejection, tab draft retention, keyboard navigation, detail focus, missing source, connection retry, 320–850px and dark mode.');
}finally{await browser.close();server.close();}
