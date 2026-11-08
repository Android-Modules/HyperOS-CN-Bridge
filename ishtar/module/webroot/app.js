'use strict';
const $=id=>document.getElementById(id),clone=x=>JSON.parse(JSON.stringify(x));
let catalog,status,draft,disabling,busy=false;
const text=(tag,value,cls)=>{const e=document.createElement(tag);e.textContent=value;if(cls)e.className=cls;return e;};
function toast(message){$('toast').textContent=message;$('toast').hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('toast').hidden=true,8000);}
function bridge(op,value){return new Promise((resolve,reject)=>{
  if(!window.ksu||typeof window.ksu.exec!=='function'){reject(new Error('请从 KernelSU 模块 WebUI 打开'));return;}
  const cb='ishtar_'+Date.now()+'_'+Math.random().toString(16).slice(2);
  const timer=setTimeout(()=>{delete window[cb];reject(new Error('操作仍在进行，请稍后刷新，勿重复保存'));},300000);
  window[cb]=(code,out,err)=>{clearTimeout(timer);delete window[cb];let data;for(const line of String(out).trim().split('\n').reverse()){try{data=JSON.parse(line);break;}catch{}}if(Number(code)!==0||!data?.ok)reject(new Error(data?.message||String(err).slice(0,180)||'模块操作失败'));else resolve(data);};
  window.ksu.exec('sh /data/adb/modules/ishtar_cn_addons/config.sh '+op+(value?' '+btoa(JSON.stringify(value)):''),cb);
});}
function changed(){return catalog.features.some(f=>draft[f.id].enabled!==status.desired[f.id].enabled||draft[f.id].remove!==status.desired[f.id].remove);}
function icon(f){const e=text('span',f.name.slice(0,1),'icon'),src=status.icons?.[f.id];if(typeof src==='string'&&/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(src)){const img=document.createElement('img');img.alt='';img.src=src;img.addEventListener('error',()=>e.replaceChildren(document.createTextNode(f.name.slice(0,1))),{once:true});e.replaceChildren(img);}return e;}
function render(){
  $('phase').textContent=status.result.ok===false?'应用失败':status.applying?'正在完成启动配置':status.pending?'等待手动重启':'已应用';
  $('result').textContent=status.result.message||'';$('version').textContent=status.version+' · 离线安装源'+(status.sourceAvailable?'可用':'缺失');
  const list=$('features');list.replaceChildren();
  for(const f of catalog.features){const row=text('div','','row'),content=text('div','','content');content.append(text('strong',f.name));
    const units=catalog.units.filter(u=>u.owners.includes(f.id)),on=draft[f.id].enabled;
    content.append(text('p',on?f.description:(draft[f.id].remove?'已关闭 · 删除展开文件':'已关闭 · 保留展开文件')));
    for(const u of units){const a=status.actual[u.package];content.append(text('p',u.package+' · 配套 '+u.version+' · '+(a?(a.updated?'商店更新':'系统组件')+' / '+a.versionCode:'未加载')));}
    const b=text('button','','switch');b.type='button';b.setAttribute('role','switch');b.setAttribute('aria-checked',String(on));b.setAttribute('aria-label',f.name);b.disabled=busy||status.applying;
    b.addEventListener('click',()=>{if(on){disabling=f;$('disable-title').textContent='关闭'+f.name+'？';document.querySelector('input[value=delete]').checked=true;$('disable').showModal();}else{draft[f.id].enabled=true;render();}});
    row.append(icon(f),content,b);list.append(row);
  }
  const native=$('native');native.replaceChildren();for(const f of catalog.nativeFeatures){const row=text('div','','row'),content=text('div','','content'),installed=f.packages.filter(p=>status.actual[p]);content.append(text('strong',f.name+' · '+(installed.length?'ROM 管理':'当前未安装')));content.append(text('p',f.description));row.append(icon(f),content);native.append(row);}
  const dirty=changed();$('dirty').textContent=dirty?'有未保存的更改':'未修改配置';$('save').disabled=!dirty||busy||status.applying;$('all').disabled=busy||status.applying;$('refresh').disabled=busy;
}
async function refresh(){status=await bridge('status');draft=clone(status.desired);render();}
$('confirm').addEventListener('click',()=>{draft[disabling.id]={enabled:false,remove:document.querySelector('input[name=removal]:checked').value==='delete'};$('disable').close();render();});
$('cancel').addEventListener('click',()=>$('disable').close());
$('all').addEventListener('click',()=>{for(const f of catalog.features)draft[f.id]={enabled:true,remove:true};render();});
$('refresh').addEventListener('click',async()=>{if(changed()){toast('请先保存更改后再刷新');return;}try{await refresh();}catch(e){toast(e.message);}});
$('save').addEventListener('click',async()=>{if(busy||!changed())return;busy=true;render();try{await bridge('save',draft);await refresh();toast('配置已保存，请手动重启并解锁');}catch(e){toast(e.message);}finally{busy=false;render();}});
(async()=>{try{const r=await fetch('catalog.json');if(!r.ok)throw new Error('组件清单读取失败');catalog=await r.json();await refresh();}catch(e){$('phase').textContent='无法连接模块';toast(e.message);}})();
