import React,{useEffect,useState,useRef,useMemo} from 'react';

import {ArrowLeft,Loader2,AppWindow} from 'lucide-react';
import './BypassEditor.css';
const icons=new Map();let activeIcons=0;const iconQueue=[];
function pumpIcons(){while(activeIcons<3&&iconQueue.length){const job=iconQueue.shift();if(job.cancelled)continue;activeIcons++;Promise.resolve().then(()=>window.electronAPI?.getAppIcon?.(job.pkg)).then(value=>{if(typeof value==='string'){icons.set(job.pkg,value);if(icons.size>400)icons.delete(icons.keys().next().value);job.done(value)}}).catch(()=>{}).finally(()=>{activeIcons--;pumpIcons()})}}
function AppIcon({pkg}){const [icon,setIcon]=useState(()=>icons.get(pkg));useEffect(()=>{if(icon)return;let alive=true;const job={pkg,done:value=>alive&&setIcon(value)};iconQueue.push(job);pumpIcons();return()=>{alive=false;job.cancelled=true}},[pkg]);return icon?<img className="bypass-app-icon" src={icon} alt="" width="32" height="32"/>:<AppWindow className="bypass-app-icon" size={32}/>;}


function BypassForm({settings,onUpdateSetting}) {
  const [site,setSite]=useState(''),[error,setError]=useState(''),[apps,setApps]=useState([]),[showApps,setShowApps]=useState(true),[loading,setLoading]=useState(false);
  const [search,setSearch]=useState(''),[scroll,setScroll]=useState(0);
  const persisted={domains:settings.bypassRules?.domains||settings.excludedDomains||[],apps:settings.bypassRules?.apps||settings.excludedApps||[]};
  const [domains,setDomains]=useState(persisted.domains),[excluded,setExcluded]=useState(persisted.apps),[saving,setSaving]=useState(false);
  const dirty=JSON.stringify(domains)!==JSON.stringify(persisted.domains)||JSON.stringify(excluded)!==JSON.stringify(persisted.apps);
  const apply=async()=>{setSaving(true);try{const result=await onUpdateSetting('bypassRules',{domains,apps:excluded});setError(result?.ok===false?result.error:'');}catch(error){setError(error.message||'Не удалось сохранить исключения')}finally{setSaving(false)}};
  useEffect(()=>{if(!showApps)return;let active=true;setLoading(true);window.electronAPI?.getInstalledApps?.().then(result=>{if(active){if(Array.isArray(result))setApps(result);else setError(result?.error||'Не удалось получить приложения')}}).catch(e=>active&&setError(e.message)).finally(()=>active&&setLoading(false));return()=>{active=false}},[showApps]);
  const addSite=()=>{
    try {
      const value=site.trim().replace(/^\*\./,'');const url=new URL(value.includes('://')?value:'https://'+value);
      if(!['https:','http:'].includes(url.protocol)||url.username||url.password||!url.hostname.includes('.'))throw Error('Введите домен, например example.com');
      const domain=url.hostname.replace(/\.$/,'').toLowerCase();
      setDomains([...new Set([...domains,domain])]);setSite('');setError('');
    }catch(e){setError(e.message)}
  };
  const matches=useMemo(()=>apps.filter(app=>(app.label+' '+app.packageName).toLowerCase().includes(search.trim().toLowerCase())),[apps,search]);
  const start=Math.max(0,Math.floor(scroll/68)-3),end=Math.min(matches.length,start+14),visible=matches.slice(start,end);
  return <div className="bypass-form">
    <label className="bypass-russia-control"><span>Российские сервисы напрямую</span><input type="checkbox" checked={settings.bypassRussia!==false} onChange={e=>onUpdateSetting('bypassRussia',e.target.checked)}/></label>
    <span style={{fontSize:12,color:'var(--text-secondary)'}}>Добавленные сайты и приложения работают без VPN. При включённой настройке «Российские сервисы напрямую» российские домены, Госуслуги, VK, MAX и Rutube также используют прямое соединение.</span>
    <label htmlFor="bypass-site">Сайт вне VPN</label>
    <div style={{display:'flex',gap:8}}><input id="bypass-site" value={site} onChange={e=>setSite(e.target.value)} onKeyDown={e=>{if(e.key==='Enter')addSite()}} placeholder="example.com" style={{minWidth:0,flex:1,padding:12,borderRadius:12,background:'#101b29',color:'white',border:'1px solid #ffffff22'}}/><button className="btn btn--secondary btn--sm" onClick={addSite}>Добавить</button></div>
    {domains.map(domain=><div key={domain} style={{display:'flex',alignItems:'center',gap:10}}><span style={{flex:1,overflowWrap:'anywhere'}}>{domain}</span><button className="btn btn--secondary btn--sm" aria-label={'Удалить '+domain} onClick={()=>setDomains(domains.filter(item=>item!==domain))}>×</button></div>)}
    <button className="btn btn--secondary" onClick={()=>setShowApps(!showApps)}>{showApps?'Скрыть список':'Выбрать приложения'} · {excluded.length}</button>
    {showApps&&<><input className="bypass-search" aria-label="Найти приложение" placeholder="Название или пакет приложения" value={search} onChange={e=>{setSearch(e.target.value);setScroll(0)}}/><small className="bypass-app-count">Найдено {matches.length} · выбрано {excluded.length} · включая системные</small>{loading?<span className="bypass-loading"><Loader2 className="spinning" size={18}/> Загружаем приложения…</span>:<div key={search} className="bypass-app-list" aria-label="Приложения на телефоне" onScroll={e=>setScroll(e.currentTarget.scrollTop)}><div style={{height:start*68}}/>{visible.map(app=><label className="bypass-app-row" key={app.packageName}><AppIcon pkg={app.packageName}/><span><strong>{app.label}</strong><small>{app.packageName}{app.system?' · системное':''}</small></span><input type="checkbox" aria-label={app.label} disabled={saving} checked={excluded.includes(app.packageName)} onChange={e=>setExcluded(e.target.checked?[...new Set([...excluded,app.packageName])]:excluded.filter(pkg=>pkg!==app.packageName))}/></label>)}<div style={{height:Math.max(0,(matches.length-end)*68)}}/>{!matches.length&&<p>Приложения не найдены</p>}</div>}</>}
    <button className="btn btn--primary" disabled={!dirty||saving} onClick={apply}>{saving?'Применение…':dirty?'Применить исключения':'Исключения применены'}</button>
    {error&&<span role="alert" style={{color:'#ff8299',fontSize:12}}>{error}</span>}
    <span style={{fontSize:11,color:'var(--text-secondary)'}}>При изменении исключений активный VPN переподключится.</span>
  </div>;
}

export default function BypassEditor({settings,onUpdateSetting,onBack}) {
  return <section className="viht-bypass-page" aria-labelledby="bypass-page-title">
    <button type="button" className="viht-settings-back" onClick={onBack}><ArrowLeft size={18}/> Настройки</button>
    <h1 id="bypass-page-title">Раздельное туннелирование</h1>
    <BypassForm settings={settings} onUpdateSetting={onUpdateSetting}/>
  </section>;
}
