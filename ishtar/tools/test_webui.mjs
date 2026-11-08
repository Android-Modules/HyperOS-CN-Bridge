import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const catalog=JSON.parse(execFileSync('python3',['-c',"import zipfile; print(zipfile.ZipFile('dist/ishtar-cn-addons-OS3.0.308.0.zip').read('catalog.json').decode())"],{cwd:root}));
assert.equal(catalog.profile,'ishtar-elite308-addons');assert.equal(catalog.units.length,9);assert.equal(catalog.features.length,9);assert.equal(catalog.nativeFeatures.length,11);
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
  window.ksu={exec(command,callback){qa.calls.push(command);setTimeout(()=>{let r=qa.state;if(command.includes(' save ')){if(qa.rejectSave){window[callback](1,JSON.stringify({ok:false,message:'检测到应用双开或其他用户，组件切换仅支持主用户 0'}),'');return;}r.desired=JSON.parse(atob(command.split(' ').at(-1)));r.pending=true;r.result={ok:true,message:'配置已保存，等待手动重启'};r={ok:true};}window[callback](0,JSON.stringify(r),'');},10);}};
 },{catalog});
 await page.goto('http://127.0.0.1:'+server.address().port);await page.waitForFunction(()=>document.querySelectorAll('[role=switch]').length===9);
 assert.equal(await page.locator('#native [role=switch]').count(),0);
 const voice=page.getByRole('switch',{name:'小爱语音',exact:true});await voice.click();assert.ok(await page.locator('input[value=delete]').isChecked());await page.getByRole('button',{name:'取消',exact:true}).click();assert.equal(await voice.getAttribute('aria-checked'),'true');
 await voice.click();await page.getByRole('button',{name:'确认关闭',exact:true}).click();assert.equal(await page.evaluate(()=>qa.calls.filter(c=>c.includes(' save ')).length),0);
 await page.getByRole('button',{name:'保存，手动重启生效',exact:true}).click();await page.waitForFunction(()=>qa.state.desired.voice.enabled===false);await page.waitForFunction(()=>document.querySelector('#phase').textContent==='等待手动重启');assert.equal(await page.evaluate(()=>qa.state.desired.voice.remove),true);
 const portal=page.getByRole('switch',{name:'传送门',exact:true});await portal.click();await page.locator('input[value=keep]').check();await page.getByRole('button',{name:'确认关闭',exact:true}).click();await page.getByRole('button',{name:'保存，手动重启生效',exact:true}).click();await page.waitForFunction(()=>qa.state.desired.portal.remove===false);
 await page.getByRole('button',{name:'全部启用',exact:true}).click();await page.getByRole('button',{name:'保存，手动重启生效',exact:true}).click();await page.waitForFunction(()=>Object.values(qa.state.desired).every(x=>x.enabled));
 await page.waitForFunction(()=>!document.querySelector('#refresh').disabled);await page.evaluate(()=>{qa.state.pending=false;qa.state.applying=true;qa.state.result={ok:false,message:'组件未加载'};});await page.getByRole('button',{name:'刷新',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#phase').textContent==='应用失败');assert.equal(await page.locator('#phase').textContent(),'应用失败');
 await page.evaluate(()=>{qa.state.applying=false;qa.state.result={ok:true,message:'配置已在本次启动生效'};qa.state.icons={gallery:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1cAAAAASUVORK5CYII='};});await page.getByRole('button',{name:'刷新',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#phase').textContent==='已应用');assert.equal(await page.locator('#native img').count(),1);
 await page.evaluate(()=>qa.rejectSave=true);await voice.click();await page.getByRole('button',{name:'确认关闭',exact:true}).click();await page.getByRole('button',{name:'保存，手动重启生效',exact:true}).click();await page.getByRole('alert').filter({hasText:'检测到应用双开'}).waitFor();assert.equal(await page.evaluate(()=>qa.state.desired.voice.enabled),true);assert.equal(await page.getByRole('button',{name:'保存，手动重启生效',exact:true}).isEnabled(),true);
 assert.ok(await page.evaluate(()=>qa.calls.every(c=>c.startsWith('sh /data/adb/modules/ishtar_cn_addons/config.sh ')&&!c.includes('reboot'))));
 for(const width of [360,393,850]){await page.setViewportSize({width,height:852});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}
 await fs.mkdir(path.join(root,'diagnostics/ishtar-adaptation/webui'),{recursive:true});await page.screenshot({path:path.join(root,'diagnostics/ishtar-adaptation/webui/light.png'),fullPage:true});await page.emulateMedia({colorScheme:'dark'});await page.screenshot({path:path.join(root,'diagnostics/ishtar-adaptation/webui/dark.png'),fullPage:true});
 assert.deepEqual(errors,[]);console.log('PASS: 9 addon units, protected native apps, isolated ownership, defaults, delete/retain/cancel, manual reboot, boot states, native icons, multi-user rejection, mobile widths and dark mode.');
}finally{await browser.close();server.close();}
