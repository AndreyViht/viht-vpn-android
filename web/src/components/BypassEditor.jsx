import React,{useEffect,useState,useRef} from 'react';
import {createPortal} from 'react-dom';
import {ChevronRight,X} from 'lucide-react';
import './BypassEditor.css';

function BypassForm({settings,onUpdateSetting}) {
  const [site,setSite]=useState(''),[error,setError]=useState(''),[apps,setApps]=useState([]),[search,setSearch]=useState(''),[showApps,setShowApps]=useState(false),[loading,setLoading]=useState(false);
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
  const visible=apps.filter(app=>(app.label+' '+app.packageName).toLowerCase().includes(search.toLowerCase()));
  return <div className="bypass-form">
    <label className="bypass-russia-control"><span>Обход российских сайтов</span><input type="checkbox" checked={settings.bypassRussia!==false} onChange={e=>onUpdateSetting('bypassRussia',e.target.checked)}/></label>
    <span style={{fontSize:12,color:'var(--text-secondary)'}}>Добавленные сайты и приложения работают напрямую. При включённом обходе РФ российские домены, Госуслуги, VK, MAX и Rutube уже идут вне VPN.</span>
    <label htmlFor="bypass-site">Сайт вне VPN</label>
    <div style={{display:'flex',gap:8}}><input id="bypass-site" value={site} onChange={e=>setSite(e.target.value)} onKeyDown={e=>{if(e.key==='Enter')addSite()}} placeholder="example.com" style={{minWidth:0,flex:1,padding:12,borderRadius:12,background:'#101b29',color:'white',border:'1px solid #ffffff22'}}/><button className="btn btn--secondary btn--sm" onClick={addSite}>Добавить</button></div>
    {domains.map(domain=><div key={domain} style={{display:'flex',alignItems:'center',gap:10}}><span style={{flex:1,overflowWrap:'anywhere'}}>{domain}</span><button className="btn btn--secondary btn--sm" aria-label={'Удалить '+domain} onClick={()=>setDomains(domains.filter(item=>item!==domain))}>×</button></div>)}
    <button className="btn btn--secondary" onClick={()=>setShowApps(!showApps)}>{showApps?'Скрыть список':'Выбрать приложения'} · {excluded.length}</button>
    {showApps&&<><input aria-label="Поиск приложений" placeholder="Найти приложение" value={search} onChange={e=>setSearch(e.target.value)} style={{padding:12,borderRadius:12,background:'#101b29',color:'white',border:'1px solid #ffffff22'}}/>{loading?<span>Загрузка приложений…</span>:<div style={{maxHeight:330,overflowY:'auto',display:'grid',gap:4}}>{visible.map(app=><label key={app.packageName} style={{padding:'10px 0',display:'flex',gap:10,alignItems:'center'}}><input type="checkbox" disabled={saving} checked={excluded.includes(app.packageName)} onChange={e=>setExcluded(e.target.checked?[...excluded,app.packageName]:excluded.filter(pkg=>pkg!==app.packageName))}/><span>{app.label}<small style={{display:'block',fontSize:10,color:'var(--text-secondary)'}}>{app.packageName}</small></span></label>)}</div>}</>}
    <button className="btn btn--primary" disabled={!dirty||saving} onClick={apply}>{saving?'Применение…':dirty?'Применить исключения':'Исключения применены'}</button>
    {error&&<span role="alert" style={{color:'#ff8299',fontSize:12}}>{error}</span>}
    <span style={{fontSize:11,color:'var(--text-secondary)'}}>При изменении исключений активный VPN переподключится.</span>
  </div>;
}

export default function BypassEditor({settings,onUpdateSetting}) {
  const [open,setOpen]=useState(false);const dialog=useRef(null),trigger=useRef(null);
  useEffect(()=>{if(!open)return;const node=dialog.current;node.showModal();const previous=document.body.style.overflow;document.body.style.overflow='hidden';const close=()=>setOpen(false);node.addEventListener('close',close);return()=>{node.removeEventListener('close',close);if(node.open)node.close();document.body.style.overflow=previous;trigger.current?.focus()};},[open]);
  const count=(settings.bypassRules?.domains||settings.excludedDomains||[]).length+(settings.bypassRules?.apps||settings.excludedApps||[]).length;
  return <><button type="button" className="bypass-settings-row" ref={trigger} aria-haspopup="dialog" onClick={()=>setOpen(true)}><strong>Раздельное туннелирование</strong><span>{count?count+' искл.':settings.bypassRussia!==false?'Включено':'Выкл.'}</span><ChevronRight size={17}/></button>{open&&createPortal(<dialog className="bypass-dialog" ref={dialog} aria-labelledby="bypass-dialog-title" onClick={e=>{if(e.target===e.currentTarget){const r=e.currentTarget.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)setOpen(false)}}}><header className="bypass-dialog-header"><h2 id="bypass-dialog-title">Раздельное туннелирование</h2><button type="button" aria-label="Закрыть настройки туннелирования" onClick={()=>setOpen(false)}><X size={20}/></button></header><BypassForm settings={settings} onUpdateSetting={onUpdateSetting}/></dialog>,document.body)}</>;
}
