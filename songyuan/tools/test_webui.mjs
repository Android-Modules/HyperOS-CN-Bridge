// Run with PLAYWRIGHT_MODULE pointing to playwright-core's index.mjs.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const webroot=path.join(root,'songyuan/module/webroot');
const catalog=JSON.parse(execFileSync('python3',['-c',"import zipfile; print(zipfile.ZipFile('dist/songyuan-cn-complete-OS3.0.306.0.zip').read('catalog.json').decode())"],{cwd:root}));
assert.equal(catalog.features.length,19);assert.equal(catalog.units.length,31);
const ownership=new Set();
for(const u of catalog.units){assert.ok(u.owners.length);for(const f of u.files){assert.match(f.path,/^(system|payload)\//);assert.ok(!f.path.includes('..'));assert.ok(!ownership.has(f.path),f.path);ownership.add(f.path);}}
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE||'playwright');
const server=http.createServer(async(req,res)=>{try{const name=new URL(req.url,'http://localhost').pathname;assert.ok(['/','/index.html','/app.js','/style.css','/catalog.json'].includes(name));const body=name==='/catalog.json'?JSON.stringify(catalog):await fs.readFile(path.join(webroot,name==='/'?'index.html':name.slice(1)));res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':name.endsWith('.css')?'text/css':name.endsWith('.json')?'application/json':'text/html');res.end(body);}catch{res.writeHead(404);res.end();}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({...(process.env.CHROME_EXECUTABLE?{executablePath:process.env.CHROME_EXECUTABLE}:{}),headless:true});
try{
const context=await browser.newContext({viewport:{width:393,height:852},deviceScaleFactor:2});
const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.addInitScript(({catalog})=>{
 const defaults=Object.fromEntries(catalog.features.map(f=>[f.id,{enabled:true,remove:true}]));
 window.qa={calls:[],state:{ok:true,desired:defaults,applied:structuredClone(defaults),actual:{},pending:false,result:{ok:true,message:'配置已在本次启动生效'},version:catalog.version,sourceAvailable:true}};
 window.ksu={exec(command,callback){window.qa.calls.push(command);setTimeout(()=>{let result=window.qa.state;if(command.includes('config.sh save ')){result.desired=JSON.parse(atob(command.split(' ').at(-1)));result.pending=true;result.result={ok:true,message:'配置已保存，等待手动重启'};result={ok:true};}window[callback](0,JSON.stringify(result),'');},10);}};
},{catalog});
await page.goto('http://127.0.0.1:'+server.address().port);
await page.waitForFunction(()=>document.querySelectorAll('.switch[aria-checked=true]').length===19);
assert.equal(await page.locator('.switch').count(),19);
await page.getByRole('button',{name:'查看相册详情',exact:true}).click();
await page.getByText('支持官方更新 · 需版本兼容',{exact:true}).waitFor();
await page.getByRole('button',{name:'知道了',exact:true}).click();
const toggle=page.getByRole('switch',{name:'主题与壁纸国内化',exact:true});await toggle.click();
assert.equal(await page.locator('input[value=delete]').isChecked(),true);
await page.getByRole('button',{name:'取消',exact:true}).click();assert.equal(await toggle.getAttribute('aria-checked'),'true');
await toggle.click();await page.getByRole('button',{name:'确认关闭',exact:true}).click();
assert.equal(await toggle.getAttribute('aria-checked'),'false');
assert.equal(await page.evaluate(()=>qa.calls.filter(x=>x.includes(' save ')).length),0);
await page.getByRole('button',{name:'保存配置',exact:true}).click();
await page.getByText('已保存，等待手动重启',{exact:true}).waitFor();
assert.deepEqual(await page.evaluate(()=>qa.state.desired.theme),{enabled:false,remove:true});
assert.ok(await page.evaluate(()=>qa.calls.every(c=>!c.includes('reboot'))));
await page.getByRole('switch',{name:'云服务国内化',exact:true}).click();
await page.locator('input[value=keep]').check();await page.getByRole('button',{name:'确认关闭',exact:true}).click();
await page.getByRole('button',{name:'保存配置',exact:true}).click();await page.waitForFunction(()=>qa.state.desired.cloud.enabled===false);
assert.equal(await page.evaluate(()=>qa.state.desired.cloud.remove),false);
await page.getByRole('button',{name:'全部启用',exact:true}).click();await page.getByRole('button',{name:'保存配置',exact:true}).click();
await page.waitForFunction(()=>Object.values(qa.state.desired).every(x=>x.enabled));
assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
await fs.mkdir(path.join(root,'diagnostics/webui'),{recursive:true});
await page.screenshot({path:path.join(root,'diagnostics/webui/components-light.png'),fullPage:true});
await page.getByRole('button',{name:'说明',exact:true}).click();await page.getByText('可以在应用商店更新吗？',{exact:true}).waitFor();
assert.equal(await page.locator('#save-bar').isVisible(),false);
await page.getByRole('button',{name:'状态',exact:true}).click();await page.getByText('已集成修复',{exact:true}).waitFor();
await page.getByRole('button',{name:'组件',exact:true}).click();
await page.emulateMedia({colorScheme:'dark'});await page.screenshot({path:path.join(root,'diagnostics/webui/components-dark.png'),fullPage:true});
// A reboot already committed must never be reported as waiting for reboot.
await page.evaluate(()=>{qa.state.pending=false;qa.state.applying=true;qa.state.result={ok:false,message:'组件未加载',phase:'late'};});
await page.getByRole('button',{name:'状态',exact:true}).click();
await page.getByRole('button',{name:'刷新状态',exact:true}).click();
await page.getByText('应用失败',{exact:true}).waitFor();
assert.equal(await page.getByText('等待手动重启',{exact:true}).count(),0);
await page.evaluate(()=>{qa.state.applying=false;qa.state.result={ok:true,message:'配置已在本次启动生效'};qa.state.icons={tsm:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1cAAAAASUVORK5CYII='};});
await page.getByRole('button',{name:'刷新状态',exact:true}).click();
await page.getByText('已应用',{exact:true}).waitFor();
await page.getByRole('button',{name:'组件',exact:true}).click();
assert.equal(await page.locator('.app-icon img').count(),1);
assert.deepEqual(errors,[]);console.log('PASS: catalog ownership, 19 defaults, details, cancel, delete/retain, save, manual reboot, update notes, navigation, mobile width, dark mode, failed/applied status, installed app icon.');
}finally{await browser.close();server.close();}
