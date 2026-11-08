'use strict';
const $ = id => document.getElementById(id);
const clone = value => JSON.parse(JSON.stringify(value));
let catalog, status, draft, disabling, busy = false;

function text(tag, value = '', className = '') {
  const element = document.createElement(tag);
  element.textContent = value;
  if (className) element.className = className;
  return element;
}
function toast(message) {
  $('toast').textContent = message;
  $('toast').hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { $('toast').hidden = true; }, 8000);
}
function bridge(op, value) {
  return new Promise((resolve, reject) => {
    if (!window.ksu || typeof window.ksu.exec !== 'function') {
      reject(new Error('请从 KernelSU 模块 WebUI 打开'));
      return;
    }
    const cb = 'ishtar_' + Date.now() + '_' + Math.random().toString(16).slice(2);
    const timer = setTimeout(() => {
      delete window[cb];
      reject(new Error('操作仍在进行，请稍后刷新，勿重复保存'));
    }, 300000);
    window[cb] = (code, out, err) => {
      clearTimeout(timer);
      delete window[cb];
      let data;
      for (const line of String(out).trim().split('\n').reverse()) {
        try { data = JSON.parse(line); break; } catch { /* Ignore shell diagnostics. */ }
      }
      if (Number(code) !== 0 || !data?.ok) {
        reject(new Error(data?.message || String(err || '').slice(0, 180) || '模块操作失败'));
      } else resolve(data);
    };
    try {
      window.ksu.exec('sh /data/adb/modules/ishtar_cn_addons/config.sh ' + op +
        (value ? ' ' + btoa(JSON.stringify(value)) : ''), cb);
    } catch (error) {
      clearTimeout(timer);
      delete window[cb];
      reject(error);
    }
  });
}
function changedFeatures() {
  return catalog.features.filter(f => draft[f.id].enabled !== status.desired[f.id].enabled ||
    draft[f.id].remove !== status.desired[f.id].remove);
}
function icon(feature) {
  const element = text('span', feature.name.slice(0, 1), 'icon');
  element.setAttribute('aria-hidden', 'true');
  const src = status.icons?.[feature.id];
  if (typeof src === 'string' && /^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(src)) {
    const img = document.createElement('img');
    img.alt = '';
    img.src = src;
    img.addEventListener('error', () => element.replaceChildren(document.createTextNode(feature.name.slice(0, 1))), {once: true});
    element.replaceChildren(img);
  }
  return element;
}
function chevron() {
  const element = text('span', '', 'chevron');
  element.setAttribute('aria-hidden', 'true');
  return element;
}
function showInfo(feature) {
  const native = feature.native;
  $('component-icon').replaceChildren(icon(feature));
  $('component-title').textContent = feature.name;
  $('component-description').textContent = feature.description;
  const units = feature.packages.map(packageName => catalog.units.find(unit => unit.package === packageName) || {package: packageName});
  const entries = units.map(unit => {
    const actual = status.actual[unit.package];
    const entry = text('div', '', 'package-entry');
    entry.append(text('strong', actual ? (actual.updated ? '已安装更新' : '系统组件') : '当前未加载'));
    entry.append(text('code', unit.package));
    if (unit.version) entry.append(text('p', '模块配套版本 ' + unit.version));
    if (actual) entry.append(text('p', '当前版本代码 ' + actual.versionCode));
    return entry;
  });
  $('component-packages').replaceChildren(...entries);
  $('component-update').textContent = native ?
    '由 ROM 管理，模块不替换或删除。Elite 改签应用可能无法接受官方更新，仅所选应用进程启用 CN 判断；云同步、支付等业务仍需实际验证。' :
      feature.update === 'official' ?
        '可尝试同包名、同签名且适配当前系统的官方商店更新，更新后优先运行商店版本。未来版本兼容性需实际验证。' :
        '此组件与系统版本配套，建议保留当前版本。仅兼容同包名、同签名的适配更新，不能保证未来版本可用。';
  $('component-info').showModal();
}
function featureCard(feature) {
  const native = feature.native;
  const on = draft[feature.id].enabled;
  const card = text('article', '', 'feature-card' + (native ? ' native-card' : '') + (on ? '' : ' is-off'));
  const top = text('div', '', 'card-top');
  const toggle = text('button', '', 'switch');
  toggle.type = 'button';
  toggle.id = 'switch-' + feature.id;
  toggle.setAttribute('role', 'switch');
  toggle.setAttribute('aria-checked', String(on));
  toggle.setAttribute('aria-label', feature.name);
  toggle.disabled = busy || status.applying || status.bootVerified === false;
  toggle.addEventListener('click', () => {
    if (on && native) {
      draft[feature.id] = {enabled: false, remove: true};
      render();
    } else if (on) {
      disabling = feature;
      $('disable-title').textContent = '关闭' + feature.name + '？';
      $('disable-description').textContent = '关闭该应用的地区适配。可选择从主用户卸载或保留应用；个人数据与其他用户所需的共享文件均保留。';
      document.querySelector('input[value=delete]').checked = true;
      $('disable').showModal();
    } else {
      draft[feature.id].enabled = true;
      render();
    }
  });
  top.append(icon(feature), toggle);
  const description = on ? feature.description : native ? '未适配 · 使用 ROM 原样' :
    (draft[feature.id].remove ? '已关闭 · 从主用户卸载' : '已关闭 · 保留应用');
  const details = text('button', '', 'card-detail');
  details.type = 'button';
  details.id = 'detail-' + feature.id;
  details.setAttribute('aria-label', '查看' + feature.name + '详情');
  details.append(text('span', '组件详情'), chevron());
  details.addEventListener('click', () => showInfo(feature));
  card.append(top, text('h3', feature.name, 'feature-title'),
    text('p', description, 'feature-description'), details);
  return card;
}
function render() {
  const focusId = document.activeElement?.id;
  const state = status.waitingScope ? 'scope' : status.result.ok === false ? 'error' : status.applying ? 'applying' : status.pending ? 'pending' : status.bootVerified === false ? 'checking' : 'applied';
  $('status-card').dataset.state = state;
  $('phase').textContent = {scope: '等待作用域授权', error: '应用失败', applying: '正在完成启动配置', pending: '等待手动重启', checking: '等待启动校验', applied: '配置已生效'}[state];
  $('retry-scope').hidden = !status.waitingScope;
  $('retry-scope').disabled = busy;
  if(status.waitingScope)document.querySelector('.status-details').open=true;
  $('result').textContent = state === 'checking' ? '本次启动尚未完成校验，解锁手机后请稍后刷新。' : status.result.message || '暂无运行详情';
  if(status.waitingScope)$('result').textContent+=' 若未出现通知，可点下方按钮重新请求，或在 LSPosed 中勾选推荐应用。';
  $('version').textContent = status.version + ' · ' + catalog.target;
  $('source-badge').textContent = status.sourceAvailable ? '离线源可用' : '离线安装源缺失';
  $('source-badge').classList.toggle('missing', !status.sourceAvailable);
  // The hero describes the saved configuration; drafts only affect the action dock.
  const enabled = catalog.features.filter(f => status.desired[f.id].enabled).length;
  $('enabled-count').textContent = enabled + ' / ' + catalog.features.length + ' 项配置开启';
  $('feature-count').textContent = catalog.features.filter(f => !f.native).length;
  $('native-count').textContent = catalog.features.filter(f => f.native).length;
  const priority = [ 'voice', 'portal', 'aiasst', 'yellow', 'nextpay', 'unionpay', 'payment', 'hybrid'];
  const ordered = catalog.features.filter(f => !f.native).sort((a, b) => priority.indexOf(a.id) - priority.indexOf(b.id));
  $('features').replaceChildren(...ordered.map(featureCard));
  $('features').setAttribute('aria-busy', 'false');
  $('native').replaceChildren(...catalog.features.filter(f => f.native).map(featureCard));
  const changes = changedFeatures().length;
  $('dirty').textContent = busy ? '正在处理…' : changes ? changes + ' 项更改未保存' :
    state === 'error' ? '请查看运行详情' : status.pending ? '已保存，等待重启' : state === 'checking' ? '等待本次启动校验' : status.applying ? '正在应用配置' : '配置已同步';
  $('save-hint').textContent = status.pending && !changes ? '请手动重启并解锁手机' : '保存后手动重启生效';
  $('save').textContent = busy ? '处理中…' : '保存配置';
  $('save').disabled = !changes || busy || status.applying || status.bootVerified === false;
  $('all').disabled = busy || status.applying || status.bootVerified === false;
  $('none').disabled = $('all').disabled;
  $('refresh').disabled = busy;
  document.querySelector('.action-dock').classList.toggle('has-changes', changes > 0);
  // Rebuilding the cards must not lose keyboard focus or jump the scroll position.
  if (focusId && $(focusId) && !$(focusId).disabled) $(focusId).focus({preventScroll: true});
}
async function refresh() {
  status = await bridge('status');
  draft = clone(status.desired);
  render();
}
function selectTab(tab) {
  for (const button of document.querySelectorAll('[role=tab]')) {
    const selected = button === tab;
    button.setAttribute('aria-selected', String(selected));
    button.tabIndex = selected ? 0 : -1;
    $(button.getAttribute('aria-controls')).hidden = !selected;
  }
}
for (const tab of document.querySelectorAll('[role=tab]')) {
  tab.addEventListener('click', () => selectTab(tab));
  tab.addEventListener('keydown', event => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const next = event.key === 'Home' ? $('tab-features') : event.key === 'End' ? $('tab-native') :
      $(tab.id === 'tab-features' ? 'tab-native' : 'tab-features');
    selectTab(next);
    next.focus();
  });
}
$('confirm').addEventListener('click', () => {
  draft[disabling.id] = {enabled: false, remove: document.querySelector('input[name=removal]:checked').value === 'delete'};
  $('disable').close();
  render();
});
$('cancel').addEventListener('click', () => $('disable').close());
$('close-info').addEventListener('click', () => $('component-info').close());
$('none').addEventListener('click', () => {
  for (const feature of catalog.features) draft[feature.id] = {enabled: false, remove: true};
  render();
  toast('已选择全部关闭，补装应用默认从主用户卸载；保存并重启后生效');
});
$('all').addEventListener('click', () => {
  for (const feature of catalog.features) draft[feature.id] = {enabled: true, remove: true};
  render();
});
$('refresh').addEventListener('click', async () => {
  if (busy) return;
  if (status && changedFeatures().length) { toast('请先保存更改后再刷新'); return; }
  busy = true;
  if (status) render();
  else $('refresh').disabled = true;
  try { await load(); } catch (error) { showConnectionError(error); }
  finally { busy = false; if (status) render(); else $('refresh').disabled = false; }
});
$('save').addEventListener('click', async () => {
  if (busy || !changedFeatures().length) return;
  busy = true;
  render();
  try {
    await bridge('save', draft);
    await refresh();
    toast('配置已保存，请手动重启并解锁');
  } catch (error) { toast(error.message); }
  finally { busy = false; render(); }
});
$('retry-scope').addEventListener('click', async () => {
  if(busy)return;
  busy=true;render();
  try {await bridge('retry');await refresh();}
  catch(error){toast(error.message);}
  finally{busy=false;render();}
});
function showConnectionError(error) {
  toast(error.message);
  if (status) return;
  $('status-card').dataset.state = 'error';
  $('phase').textContent = '无法连接模块';
  $('result').textContent = error.message;
  $('enabled-count').textContent = '请从 KernelSU 模块 WebUI 打开';
  $('features').replaceChildren(text('p', '连接后可管理组件，请尝试右上角刷新。', 'empty-state'));
  $('features').setAttribute('aria-busy', 'false');
  $('dirty').textContent = '尚未读取配置';
  $('refresh').disabled = false;
}
async function load() {
  if (!catalog) {
    const response = await fetch('catalog.json');
    if (!response.ok) throw new Error('组件清单读取失败');
    catalog = await response.json();
  }
  await refresh();
}
load().catch(showConnectionError);
