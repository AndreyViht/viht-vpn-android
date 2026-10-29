import React,{useEffect,useRef,useState} from 'react';
import {createPortal} from 'react-dom';
import './IosDeviceAccess.css';

export default function IosDeviceAccess({enabled,request,qrForLink,onChanged}){
 const latest=useRef({request,qrForLink,onChanged});latest.current={request,qrForLink,onChanged};
 const [view,setView]=useState(''),[invite,setInvite]=useState(null),[qr,setQr]=useState(''),[selected,setSelected]=useState(null),[pending,setPending]=useState([]),[busy,setBusy]=useState(false),[error,setError]=useState(''),[copied,setCopied]=useState(false),[now,setNow]=useState(Date.now()),[result,setResult]=useState('');
 const seen=useRef(new Set()),viewRef=useRef(''),inviteRef=useRef(null),selectionRef=useRef(null),generation=useRef(0),dialog=useRef(null),closeButton=useRef(null);
 viewRef.current=view;inviteRef.current=invite;selectionRef.current=selected;
 async function ask(action,body){const r=await latest.current.request(action,body);if(!r?.ok)throw Error(r?.error||'Не удалось выполнить запрос');return r;}
 function close(){generation.current++;setView('');setError('');setBusy(false);}
 async function create(){
  const epoch=++generation.current;setView('loading');setError('');setBusy(true);setCopied(false);setQr('');setSelected(null);
  try{const r=await ask('ios-create',{});if(generation.current!==epoch)return;setInvite(r);setNow(Date.now());setView('invite');
   const image=await latest.current.qrForLink(r.link);if(generation.current===epoch)setQr(typeof image==='string'?image:image.dataUrl);
  }catch(e){if(generation.current===epoch){setError(e.message);setView('error')}}finally{if(generation.current===epoch)setBusy(false)}
 }
 useEffect(()=>{
  if(!enabled){setView('');setPending([]);return;}
  let alive=true,running=false;
  async function poll(){
   if(running||document.hidden)return;running=true;
   try{const r=await ask('ios-requests');if(!alive)return;
    const rows=r.requests||[],waiting=rows.filter(row=>row.state==='pending'&&Date.parse(row.expires_at)>Date.now());setPending(waiting);
    const own=rows.find(row=>row.id===inviteRef.current?.id),current=rows.find(row=>row.id===selectionRef.current?.id);
    if((own?.state==='approved'&&viewRef.current==='invite')||(current?.state==='approved'&&viewRef.current==='confirm')){setResult('iPhone подключён');setView('success');latest.current.onChanged?.();return;}
    const next=waiting.find(row=>!seen.current.has(row.id));
    if(next&&!['loading','success'].includes(viewRef.current)){seen.current.add(next.id);setSelected(next);setError('');setView('confirm');}
   }catch{/* Polling errors never log the owner out or replace an existing device list. */}finally{running=false;}
  }
  const open=()=>void create();window.addEventListener('viht-ios-invite',open);document.addEventListener('visibilitychange',poll);void poll();const timer=setInterval(poll,4000);
  return()=>{alive=false;clearInterval(timer);window.removeEventListener('viht-ios-invite',open);document.removeEventListener('visibilitychange',poll)};
 },[enabled]);
 useEffect(()=>{if(view!=='invite')return;const timer=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(timer)},[view]);
 useEffect(()=>{
  if(!view)return;const before=document.body.style.overflow,previous=document.activeElement;document.body.style.overflow='hidden';closeButton.current?.focus();
  const key=e=>{if(e.key==='Escape'&&!busy){e.preventDefault();close()}if(e.key==='Tab'){const nodes=[...(dialog.current?.querySelectorAll('button:not(:disabled),a[href],input')||[])].filter(node=>node.getClientRects().length);if(!nodes.length)return;const first=nodes[0],last=nodes.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus()}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus()}}};
  window.addEventListener('keydown',key);return()=>{document.body.style.overflow=before;window.removeEventListener('keydown',key);previous?.focus?.()};
 },[!!view,busy]);
 async function decide(approve){
  setBusy(true);setError('');try{await ask(approve?'ios-approve':'ios-reject',{id:selected.id});setPending(rows=>rows.filter(row=>row.id!==selected.id));setResult(approve?'iPhone подключён':'Запрос отклонён');setView('success');latest.current.onChanged?.()}catch(e){setError(e.message)}finally{setBusy(false)}
 }
 async function cancel(){setBusy(true);setError('');try{await ask('ios-cancel',{id:invite.id});close()}catch(e){setError(e.message)}finally{setBusy(false)}}
 async function copy(){try{await navigator.clipboard.writeText(invite.link);setCopied(true)}catch{setError('Не удалось скопировать. Используйте QR-код.')}}
 const left=Math.max(0,Math.ceil((Date.parse(invite?.expires_at||'')-now)/1000)||0);
 if(!enabled)return null;
 return <>
  {!view&&pending.length>0&&<button className="viht-ios-pending" onClick={()=>{setSelected(pending[0]);setError('');setView('confirm')}}>iOS · Ожидает подтверждения ({pending.length})</button>}
  {view&&createPortal(<div className="viht-ios-shade"><section ref={dialog} className="viht-ios-dialog" role="dialog" aria-modal="true" aria-labelledby="viht-ios-title">
   <button ref={closeButton} className="viht-ios-close" onClick={close} disabled={busy} aria-label="Закрыть">×</button>
   <span className="viht-ios-eyebrow">VIHT · IPHONE И IPAD</span>
   <h2 id="viht-ios-title">{view==='confirm'?'Новое подключение iOS':view==='success'?result:'Подключить iPhone'}</h2>
   {view==='loading'&&<p>Создаём личное приглашение…</p>}
   {view==='invite'&&<><p>Сканируйте QR в Incy или откройте приглашение на iPhone. Затем подтвердите устройство в кабинете Viht.</p>
    {left>0?<>{qr?<img className="viht-ios-qr" src={qr} alt="Одноразовое приглашение iPhone в Incy"/>:<p>Подготавливаем QR…</p>}<div className="viht-ios-timer">Один iPhone · {Math.floor(left/60)}:{String(left%60).padStart(2,'0')}</div><a className="viht-ios-primary" href={invite.link}>Открыть в Incy</a><button className="viht-ios-secondary" onClick={copy}>{copied?'Скопировано':'Скопировать приглашение'}</button><button className="viht-ios-text" onClick={cancel} disabled={busy}>Отменить приглашение</button></>:<><p>Срок приглашения истёк.</p><button className="viht-ios-primary" onClick={create} disabled={busy}>Создать новое</button></>}
    <small>Только Incy на iPhone и iPad. Если запрос не появляется, включите отправку HWID в настройках Incy и обновите подписку.</small><a className="viht-ios-text" href="https://apps.apple.com/us/app/incy/id6756943388" target="_blank" rel="noopener noreferrer">Установить Incy</a></>}
   {view==='confirm'&&selected&&<><p>Устройство запрашивает доступ к вашей подписке.</p><strong className="viht-ios-model">{selected.device_model||'iPhone'}</strong><dl><div><dt>Приложение</dt><dd>Incy {selected.app_version||''}</dd></div><div><dt>Система</dt><dd>iOS {selected.os_version||''}</dd></div><div><dt>Запрос</dt><dd>{new Date(selected.requested_at).toLocaleString('ru-RU')}</dd></div></dl><details><summary>Подробнее об устройстве</summary><p>HWID, переданный Incy</p><code>{selected.bound_hwid}</code></details><p>Будет занят один свободный слот. Оплата и управление подпиской останутся у вас.</p><button className="viht-ios-primary" onClick={()=>decide(true)} disabled={busy}>{busy?'Обрабатываем…':'Принять подключение'}</button><button className="viht-ios-secondary" onClick={()=>decide(false)} disabled={busy}>Отклонить</button></>}
   {view==='success'&&<><p>{result==='iPhone подключён'?'Доступ подтверждён. Обновите подписку в Incy, чтобы получить серверы.':'Рабочие ключи этому устройству не выдавались.'}</p><button className="viht-ios-primary" onClick={close}>Готово</button></>}
   {view==='error'&&<button className="viht-ios-primary" onClick={create} disabled={busy}>Повторить</button>}
   {error&&<p className="viht-ios-error" role="alert">{error}</p>}
  </section></div>,document.body)}
 </>;
}
