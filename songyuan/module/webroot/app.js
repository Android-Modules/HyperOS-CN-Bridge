'use strict';
const $ = id => document.getElementById(id);
let catalog, status, draft, disabling, busy=false, selectedPage='components';
const icons={tsm:['卡','#eef4ff','#497aca'],wallet:['钱','#fff0e7','#e99955'],nextpay:['付','#e9f5ff','#5a91bf'],unionpay:['银','#eaf5f2','#5c9d8b'],payment:['支','#eef0ff','#7987c9'],voice:['爱','#e9f5ff','#5d9ddb'],vision:['识','#edf5ff','#6898d9'],aiasst:['AI','#eeeefe','#8b86ce'],personal:['屏','#ebf3ff','#6b91cb'],sms:['信','#e9f7ee','#5eab83'],portal:['传','#eef0ff','#818ece'],yellow:['号','#fff7e6','#c4a05a'],hybrid:['快','#fff0e8','#d98c60'],market:['应','#fff0e7','#ea965d'],cloud:['云','#eaf3ff','#6599dc'],calendar:['历','#ffefed','#dc857b'],clock:['时','#edf0f6','#78859c'],theme:['主','#f3ecff','#ad8bcb'],gallery:['相','#fff0e7','#df9b70']};
const clone = x => JSON.parse(JSON.stringify(x));
function el(tag, cls, text){const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e;}
function icon(id){const [text,bg,color]=icons[id];const e=el('span','app-icon',text);e.style.setProperty('--icon-bg',bg);e.style.setProperty('--icon-color',color);const src=status?.icons?.[id];if(typeof src==='string'&&/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(src)){const img=el('img');img.alt='';img.src=src;img.width=48;img.height=48;img.addEventListener('error',()=>e.replaceChildren(document.createTextNode(text)),{once:true});e.replaceChildren(img);}return e;}
function phase(){return status.result.ok===false?'应用失败':status.applying?'正在完成启动配置':status.pending?'等待手动重启':'已应用';}
function toast(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('toast').hidden=true,6000);}
function bridge(op,value){
  return new Promise((resolve,reject)=>{
    if(!window.ksu || typeof window.ksu.exec!=='function'){reject(new Error('请在 KernelSU 的模块 WebUI 中打开此页面'));return;}
    const callback='songyuan_'+Date.now()+'_'+Math.random().toString(16).slice(2);
    const timer=setTimeout(()=>{delete window[callback];reject(new Error('操作仍在进行，请稍后刷新状态，勿重复保存'));},300000);
    window[callback]=(code,out,err)=>{clearTimeout(timer);delete window[callback];try{
      const lines=String(out).trim().split('\n');let result;
      for(let i=lines.length-1;i>=0;i--){try{result=JSON.parse(lines[i]);break;}catch{}}
      if(!result || Number(code)!==0 || !result.ok)throw new Error(result?.message||String(err).slice(0,180)||'无法读取模块配置');resolve(result);
    }catch(e){reject(e);}};
    const encoded=value?btoa(JSON.stringify(value)):'';
    // Fixed command and validated base64; no text from package metadata enters shell.
    window.ksu.exec('sh /data/adb/modules/songyuan_cn_complete/config.sh '+op+(encoded?' '+encoded:''),callback);
  });
}
function changed(){return !!(catalog&&status&&draft)&&catalog.features.some(f=>draft[f.id].enabled!==status.desired[f.id].enabled||draft[f.id].remove!==status.desired[f.id].remove);}
function update(){
  const on=catalog.features.filter(f=>draft[f.id].enabled).length,dirty=changed();
  $('count').textContent=`${on} / ${catalog.features.length}`;
  $('summary-title').textContent=`${on} 个组件已选择启用`;
  $('summary-sub').textContent=status.result.ok===false?'上次应用未完成，请查看状态':status.applying?'已重启，正在完成启动配置':status.pending?'已保存的配置等待手动重启':'国行组件与配套修复已就绪';
  $('status-dot').className='status-dot'+(status.result.ok===false?' error':status.pending?' pending':'');
  $('save-title').textContent=dirty?'有未保存的更改':status.result.ok===false?'配置应用失败':status.applying?'正在完成启动配置':status.pending?'已保存，等待手动重启':`${on} 个组件已启用`;
  $('save-sub').textContent=dirty?'保存后请手动重启手机':status.pending?'本页面不会自动重启手机':'点组件名称查看版本与更新说明';
  $('save').disabled=!dirty||busy||status.applying;$('enable-all').disabled=busy||on===catalog.features.length;
  $('version').textContent=status.version;
  document.querySelectorAll('.switch').forEach(b=>{b.setAttribute('aria-checked',String(draft[b.dataset.id].enabled));b.disabled=busy;});
  document.querySelectorAll('.row-desc').forEach(e=>{const f=catalog.features.find(f=>f.id===e.dataset.id),d=draft[f.id];e.textContent=d.enabled?f.description:(d.remove?'已选择关闭 · 删除国内版文件':'已选择关闭 · 保留国内版文件');});
  renderStatus();
}
function render(){
  const list=$('component-list');list.replaceChildren();
  for(const category of [...new Set(catalog.features.map(f=>f.category))]){
    list.append(el('h3','group-heading',category));const group=el('div','component-group');
    for(const f of catalog.features.filter(f=>f.category===category)){
      const row=el('div','component-row'),detail=el('button','row-detail');detail.type='button';detail.setAttribute('aria-label',`查看${f.name}详情`);
      const title=el('span','row-title',f.name),badge=el('span','row-meta',f.update==='official'?'官方可更新':'建议配套');title.append(badge);
      const desc=el('span','row-desc');desc.dataset.id=f.id;detail.append(title,desc);detail.addEventListener('click',()=>showDetail(f));
      const toggle=el('button','switch');toggle.type='button';toggle.dataset.id=f.id;toggle.setAttribute('role','switch');toggle.setAttribute('aria-label',`${f.name}国内化`);
      toggle.addEventListener('click',()=>{if(draft[f.id].enabled)showDisable(f);else{draft[f.id].enabled=true;update();}});
      row.append(icon(f.id),detail,toggle);group.append(row);
    }list.append(group);
  }
  list.setAttribute('aria-busy','false');update();
}
function showDisable(f){
  disabling=f;$('disable-title').textContent=`关闭${f.name}？`;
  document.querySelector('input[name=removal][value=delete]').checked=true;
  $('disable-extra').textContent=f.id==='cloud'?'云服务关闭后会回到 POCO 桌面并迁移布局；相册需要的账号与同步依赖会继续保留。':f.id==='tsm'?'智能卡切换涉及 NFC 共享 UID；系统密钥与 UID 绑定，卡片或认证可能需要重新验证。':'';
  $('disable').showModal();
}
function showDetail(f){
  const box=$('detail-content');box.replaceChildren();const heading=el('div','detail-heading'),titles=el('div');titles.append(el('h2','',f.name),el('p','',f.description));heading.append(icon(f.id),titles);box.append(heading);
  const info=el('div','detail-section');info.append(el('h3','',f.update==='official'?'支持官方更新 · 需版本兼容':'核心依赖 · 建议保持配套'));
  info.append(el('p','',f.update==='official'?'同包名、同签名的官方国行更新通常可以继续生效；商店更新优先运行，模块的区域和依赖修复继续保留。后续版本仍需适配当前系统，不能保证每个新版都兼容。':'建议使用本模块的同版本国行组合。只有同包名、同签名且适配当前系统的更新才可能正常工作；避免单独混装账号、云、NFC 或支付依赖。'));box.append(info);
  const packages=el('div','detail-section');packages.append(el('h3','','包含组件与版本'));
  for(const u of catalog.units.filter(u=>u.owners.includes(f.id))){const item=el('div','package',u.package),a=status.actual[u.package];item.append(el('strong','',`配套版本 ${u.version} · ${a?(a.updated?'正在运行商店更新':'系统组件')+' · versionCode '+(a.versionCode||'—'):'当前未安装'}`));packages.append(item);}box.append(packages);
  const note=el('div','notice');note.append(el('p','','关闭时也会处理该组件的商店更新包。保留模式可恢复保留的更新；删除模式重新启用时恢复模块配套版本。共享依赖会按其他启用组件的需要保留。'));box.append(note);$('detail').showModal();
}
function renderStatus(){
  const box=$('runtime-status');box.replaceChildren();
  const entries=[['模块版本',status.version],['适用系统','OS3.0.306.0.WGNMIXM'],['配置状态',phase()],['本地安装源',status.sourceAvailable?'可用 · 支持离线重新启用':'缺失，请重新安装模块'],['最近结果',status.result.message||'尚无记录']];
  for(const [label,value]of entries){const line=el('div','status-line');line.append(el('span','',label),el('strong','',value));box.append(line);}
}
async function refresh(){try{const result=await bridge('status');status=result;draft=clone(result.desired);render();}catch(e){toast(e.message);$('summary-title').textContent='无法连接模块';$('summary-sub').textContent=e.message;}}
$('confirm-disable').addEventListener('click',()=>{draft[disabling.id]={enabled:false,remove:document.querySelector('input[name=removal]:checked').value==='delete'};$('disable').close();update();});
document.querySelectorAll('[data-close]').forEach(b=>b.addEventListener('click',()=>$(b.dataset.close).close()));
$('enable-all').addEventListener('click',()=>{for(const f of catalog.features)draft[f.id]={enabled:true,remove:true};update();});
$('save').addEventListener('click',async()=>{if(busy||!changed())return;busy=true;update();$('busy').showModal();try{await bridge('save',draft);await refresh();toast('配置已保存，请手动重启手机后生效');}catch(e){toast(e.message);}finally{busy=false;$('busy').close();update();}});
$('busy').addEventListener('cancel',e=>e.preventDefault());
$('refresh').addEventListener('click',async()=>{if(changed()){toast('请先保存更改后再刷新状态');return;}await refresh();});
document.querySelectorAll('nav button').forEach(b=>b.addEventListener('click',()=>{selectedPage=b.dataset.page;document.querySelectorAll('.page').forEach(p=>p.hidden=p.id!==selectedPage+'-page');document.querySelectorAll('nav button').forEach(n=>n.classList.toggle('selected',n===b));$('save-bar').hidden=selectedPage!=='components';window.scrollTo(0,0);}));
fetch('catalog.json').then(r=>{if(!r.ok)throw new Error('组件清单无法读取');return r.json();}).then(c=>{catalog=c;return refresh();}).catch(e=>toast(e.message));
