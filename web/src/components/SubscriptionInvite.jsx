import {createPortal} from 'react-dom';
import {motion,AnimatePresence} from 'framer-motion';
import {Smartphone,Monitor,Apple,ChevronRight,ArrowLeft,Loader2,Copy,Check,X} from 'lucide-react';
import React,{useEffect,useState,useRef} from 'react';
import './SubscriptionInvite.css';
export function OwnerInvite({compact=false,onAdded}) {
  const [open,setOpen] = useState(false);
  const [mode,setMode] = useState('choice');
  const [code,setCode] = useState('');
  const [expiry,setExpiry] = useState(0);
  const [now,setNow] = useState(Date.now);
  const [error,setError] = useState('');
  const [busy,setBusy] = useState(false);
  const [pair,setPair] = useState(null);
  const [copied,setCopied] = useState(false);
  const dialog = useRef(null);
  const requestBusy = useRef(false);

  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement;
    dialog.current?.showModal();
    const update = () => { if (!document.hidden && !window.__vihtUiBackground) setNow(Date.now()); };
    const timer = setInterval(update,1000);
    document.addEventListener('visibilitychange',update);
    return () => { clearInterval(timer);document.removeEventListener('visibilitychange',update);previous?.focus?.(); };
  }, [open]);

  function close() { if (!requestBusy.current) setOpen(false); }
  function show() { setMode('choice');setError('');setCode('');setPair(null);setCopied(false);setOpen(true); }
  async function request(action,body) {
    const result = await window.electronAPI.cabinetRequest({action,method:'POST',body});
    if (!result?.ok || result.data?.ok === false) throw Error(result?.error || result?.data?.error || 'Не удалось выполнить запрос. Повторите попытку.');
    return result.data;
  }
  async function create() {
    if (requestBusy.current) return;
    requestBusy.current=true;setBusy(true);setMode('invite');setError('');setCode('');setCopied(false);
    try {
      const result = await request('create-invite',{use_pin:true});
      const expires = Date.parse(result.expires_at);
      if (!/^\d{6}$/.test(result.code) || !Number.isFinite(expires)) throw Error('Не удалось создать код. Повторите попытку.');
      setCode(result.code);setExpiry(expires);setNow(Date.now());
    } catch (failure) { setError(failure.message); }
    finally { requestBusy.current=false;setBusy(false); }
  }
  async function preview() {
    if (requestBusy.current || code.length !== 6) return;
    requestBusy.current=true;setBusy(true);setError('');
    try { setPair(await request('pair-preview',{pairing_token:code}));setMode('confirm'); }
    catch (failure) { setError(failure.message); }
    finally { requestBusy.current=false;setBusy(false); }
  }
  async function approve() {
    if (requestBusy.current) return;
    requestBusy.current=true;setBusy(true);setError('');
    try { await request('pair-approve',{pairing_token:code});setMode('approved');onAdded?.(); }
    catch (failure) { setError(failure.message); }
    finally { requestBusy.current=false;setBusy(false); }
  }
  async function copyCode() {
    try {
      if (window.electronAPI?.copyText) {
        const result = await window.electronAPI.copyText(code);
        if (!result?.ok) throw Error('Не удалось скопировать код');
      } else await navigator.clipboard.writeText(code);
      setCopied(true);
    } catch (failure) { setError(failure.message || 'Не удалось скопировать код'); }
  }
  const left = Math.max(0,Math.ceil((expiry-now)/1000));
  const title = {choice:'Добавить устройство',invite:'Код подключения',enter:'Подключить компьютер',confirm:'Разрешить подключение?',approved:'Устройство добавлено'}[mode];

  return <>
    <button type="button" className={compact?'invite-primary invite-primary--compact':'invite-primary'} onClick={show}>＋ Добавить клиента</button>
    {open && createPortal(<dialog ref={dialog} className="device-invite-dialog" aria-labelledby="device-invite-title" onClose={()=>{if(requestBusy.current)dialog.current?.showModal();else setOpen(false);}} onCancel={event=>{event.preventDefault();close();}} onClick={event=>{if(event.target===event.currentTarget)close();}}>
      <motion.section className="invite-card device-invite-card" initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{duration:.22}} aria-labelledby="device-invite-title">
        <button type="button" className="invite-close" onClick={close} disabled={busy} aria-label="Закрыть"><X size={20}/></button>
        {mode!=='choice' && mode!=='approved' && <button type="button" className="device-invite-back" disabled={busy} onClick={()=>{setMode('choice');setError('');}}><ArrowLeft size={16}/>Назад</button>}
        <h2 id="device-invite-title">{title}</h2>
        <AnimatePresence initial={false} mode="wait">
          <motion.div key={mode} initial={{opacity:0,y:6}} animate={{opacity:1,y:0}} exit={{opacity:0,y:-4}} transition={{duration:.16}}>
            {mode==='choice' && <div className="device-invite-options">
              <button type="button" className="device-invite-option" onClick={create}><Smartphone size={23}/><span><strong>Телефон Android</strong><small>Создать одноразовый код для Viht VPN</small></span><ChevronRight size={16}/></button>
              <button type="button" className="device-invite-option" onClick={()=>{setCode('');setMode('enter');}}><Monitor size={23}/><span><strong>Компьютер</strong><small>Ввести код с экрана Viht VPN на компьютере</small></span><ChevronRight size={16}/></button>
              <button type="button" className="device-invite-option" onClick={()=>{setOpen(false);window.dispatchEvent(new Event('viht-ios-invite'));}}><Apple size={23}/><span><strong>iPhone или iPad</strong><small>Подключить через VPNET или Incy</small></span><ChevronRight size={16}/></button>
              <p>Новое устройство занимает один слот. Его доступом управляете вы.</p>
            </div>}
            {mode==='invite' && <>
              <p>На другом телефоне откройте Viht VPN → «Подключиться к чужой подписке» и введите код.</p>
              {busy ? <div className="device-invite-loading" role="status"><Loader2 className="spinning" size={25}/>Создаём код…</div> : code && left>0 ? <>
                <strong className="invite-pin" aria-label={'Код '+code.split('').join(' ')}>{code}</strong>
                <p className="device-invite-expiry">Действует {Math.floor(left/60)}:{String(left%60).padStart(2,'0')} · одно использование</p>
                <button type="button" className="invite-primary" onClick={copyCode}><Copy size={16}/>{copied?'Код скопирован':'Скопировать код'}</button>
              </> : !error && <p role="status">Время действия кода истекло.</p>}
              <button type="button" className="invite-secondary" onClick={create} disabled={busy}>{code?'Создать новый код':'Повторить'}</button>
            </>}
            {mode==='enter' && <form onSubmit={event=>{event.preventDefault();void preview();}}>
              <p>Откройте Viht VPN на компьютере и введите шесть цифр с его экрана подключения.</p>
              <label className="device-invite-label">Код с компьютера<input className="invite-pin-input" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} onChange={event=>{setCode(event.target.value.replace(/\D/g,''));setError('');}} placeholder="000000" autoFocus/></label>
              <button type="submit" className="invite-primary" disabled={busy||code.length!==6}>{busy&&<Loader2 size={16} className="spinning"/>}{busy?'Проверяем…':'Продолжить'}</button>
            </form>}
            {mode==='confirm' && <>
              <strong className="invite-owner">{pair?.device_name || 'Компьютер'}</strong>
              <p>{pair?.replaces_existing?'Подключение этого компьютера обновится. Дополнительный слот не понадобится.':'Будет занят один свободный слот.'}</p>
              <p>На компьютере будет доступ к VPN. Подпиской и устройствами управляете вы.</p>
              <button type="button" className="invite-primary" disabled={busy} onClick={approve}>{busy&&<Loader2 size={16} className="spinning"/>}{busy?'Подключаем…':'Разрешить подключение'}</button>
            </>}
            {mode==='approved' && <><span className="device-invite-success"><Check size={29}/></span><p>Доступ подтверждён. Подписка появится на компьютере автоматически.</p><button type="button" className="invite-primary" onClick={close}>Готово</button></>}
          </motion.div>
        </AnimatePresence>
        {error && <p className="device-invite-error" role="alert">{error}</p>}
      </motion.section>
    </dialog>,document.body)}
  </>;
}


export function JoinInviteButton(){return <button className="btn-auth" onClick={()=>window.dispatchEvent(new Event("viht-scan-invite"))}><span>▦</span><span className="btn-auth__text"><span className="btn-auth__title">Подключиться к чужой подписке</span><span className="btn-auth__hint">Ввести одноразовый код</span></span></button>;}
export function JoinSubscription({signedIn=false,beforeJoin}){
 const current=useRef({signedIn,beforeJoin});current.current={signedIn,beforeJoin};const scans=useRef(new Set()),previewEpoch=useRef(0);
 const [open,setOpen]=useState(false),[text,setText]=useState(''),[owner,setOwner]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 useEffect(()=>{const off=window.electronAPI?.onInviteOpen?.(data=>{setOpen(true);void preview(data.invite)});const offScan=window.electronAPI?.onGuestScan?.(data=>{if(scans.current.has(data.id))return;scans.current.add(data.id);setOpen(true);void preview(data.content);void window.electronAPI.ackScanResult(data.id).catch(()=>{})});const openScanner=()=>{setOpen(true);setOwner('');setText('');setError('')};window.addEventListener('viht-scan-invite',openScanner);return()=>{off?.();offScan?.();window.removeEventListener('viht-scan-invite',openScanner)}},[]);
 async function preview(raw){const epoch=++previewEpoch.current;setText(raw);setOwner('');setBusy(true);setError('');try{const r=await window.electronAPI.guestInvite('preview',raw);if(!r.ok)throw Error(r.error);if(epoch===previewEpoch.current)setOwner(r.owner_label)}catch(e){if(epoch===previewEpoch.current)setError(e.message)}finally{if(epoch===previewEpoch.current)setBusy(false)}}
 async function scan(){setOpen(true);setError('');try{const r=await window.electronAPI.scanInvite({purpose:'guest'});if(r.ok&&!r.delivered)await preview(r.content);else if(!r.cancelled)throw Error(r.error)}catch(e){setError(e.message)}}
 async function accept(){setBusy(true);setError('');try{if(current.current.signedIn)await current.current.beforeJoin?.();const r=await window.electronAPI.guestInvite('accept',text);if(!r.ok)throw Error(r.error);setOpen(false)}catch(e){setError(e.message)}finally{setBusy(false)}}
 return <>{open&&<div className="invite-overlay"><section className="invite-card device-invite-card" role="dialog" aria-modal="true" aria-label="Подключиться к подписке"><button className="invite-close" onClick={()=>setOpen(false)} aria-label="Закрыть">×</button><span className="invite-eyebrow">VIHT · ГОСТЕВОЙ ДОСТУП</span><h2>{owner?'Подтвердите подключение':'Введите код подписки'}</h2>{owner?<><p>Владелец подписки</p><strong className="invite-owner">{owner}</strong><p>Этот телефон займёт один слот. Владелец сможет отключить его доступ.</p>{signedIn&&<p>Вы переключитесь на эту подписку. Ваша собственная подписка останется в вашем аккаунте.</p>}<button className="invite-primary" disabled={busy} onClick={accept}>{busy?'Подключаем…':signedIn?'Переключиться и подключиться':'Подключиться'}</button></>:<><label>Одноразовый код владельца<input className="invite-pin-input" value={text} onChange={e=>setText(e.target.value.replace(/\D/g,''))} placeholder="000000" inputMode="numeric" autoComplete="one-time-code" maxLength={6}/></label><button className="invite-primary" disabled={busy||text.length!==6} onClick={()=>preview(text)}>Продолжить</button></>}{error&&<p role="alert">{error}</p>}</section></div>}</>;
}
export function GuestMembership({user,onLogout}){
 const [confirm,setConfirm]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 async function leave(){setBusy(true);setError('');try{await window.electronAPI.vpnDisconnect();const r=await window.electronAPI.cabinetRequest({action:'guest-leave',method:'POST',body:{}});if(!r.ok&&!r.unauthorized)throw Error(r.error||'Не удалось покинуть подписку');await onLogout(true)}catch(e){setError(e.message)}finally{setBusy(false)}}
 return <div className="fluent-page"><section className="invite-card invite-page"><span className="invite-eyebrow">ГОСТЕВОЙ ДОСТУП</span><h2>Вы подключены к чужой подписке</h2><p>Владелец</p><strong className="invite-owner">{user.owner_label}</strong><p>Оплатой, слотами и подключёнными людьми управляет владелец. Вы можете завершить доступ только этого телефона.</p>{confirm?<><p>Отключить VPN и покинуть подписку? Этот код повторно использовать нельзя.</p><button className="invite-danger" disabled={busy} onClick={leave}>{busy?'Отключаем…':'Да, покинуть подписку'}</button><button className="invite-secondary" disabled={busy} onClick={()=>setConfirm(false)}>Остаться</button></>:<button className="invite-danger" onClick={()=>setConfirm(true)}>Покинуть подписку</button>}{error&&<p role="alert">{error}</p>}</section></div>;
}

export function OwnerPairConfirmation({onOpen,onAdded}){
 const [open,setOpen]=useState(false),[data,setData]=useState(null),[token,setToken]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[approved,setApproved]=useState(false),seen=useRef(new Set()),previewVersion=useRef(0),callbacks=useRef({onOpen,onAdded});callbacks.current={onOpen,onAdded};
 async function received(result){if(seen.current.has(result.id))return;seen.current.add(result.id);const version=++previewVersion.current;setOpen(true);setData(null);setApproved(false);setError('');setBusy(true);callbacks.current.onOpen?.();void window.electronAPI?.ackScanResult?.(result.id).catch(()=>{});try{const uri=new URL(result.content.trim());if(uri.protocol!=='https:'||uri.hostname!=='anviht.ru'||uri.pathname!=='/pair'||!/^[a-f0-9]{64}$/.test(uri.hash.slice(1)))throw Error('Отсканируйте QR из окна подключения на компьютере');const value=uri.hash.slice(1);const response=await window.electronAPI.cabinetRequest({action:'pair-preview',method:'POST',body:{pairing_token:value}});if(!response.ok)throw Error(response.error||'Запрос подключения недоступен');if(version===previewVersion.current){setToken(value);setData(response.data)}}catch(e){if(version===previewVersion.current)setError(e.message)}finally{if(version===previewVersion.current)setBusy(false)}}
 useEffect(()=>{const off=window.electronAPI?.onOwnerPairScan?.(received);const legacy=e=>void received(e.detail);window.addEventListener('viht-owner-pair-scan',legacy);return()=>{off?.();window.removeEventListener('viht-owner-pair-scan',legacy)}},[]);
 async function approve(){setBusy(true);setError('');try{const response=await window.electronAPI.cabinetRequest({action:'pair-approve',method:'POST',body:{pairing_token:token}});if(!response.ok)throw Error(response.error||'Не удалось подтвердить доступ');setApproved(true);callbacks.current.onAdded?.()}catch(e){setError(e.message)}finally{setBusy(false)}}
 if(!open)return null;
 return <div className="invite-overlay"><section className="invite-card device-invite-card" role="dialog" aria-modal="true" aria-label="Подключение компьютера"><button className="invite-close" onClick={()=>setOpen(false)} aria-label="Закрыть">×</button><span className="invite-eyebrow">VIHT · ПОДТВЕРЖДЕНИЕ</span><h2>{approved?'Компьютер добавлен':'Подключить компьютер?'}</h2>{approved?<><p>Доступ подтверждён. Подписка появится на компьютере автоматически.</p><button className="invite-primary" onClick={()=>setOpen(false)}>Готово</button></>:<>{busy&&!data&&<p>Проверяем QR-запрос…</p>}{data&&<><strong className="invite-owner">{data.device_name}</strong><p>{data.replaces_existing?'Прежнее подключение этого ПК заменится без второго слота.':'Будет занят один свободный слот вашей подписки.'} Компьютер сможет пользоваться VPN, но не управлять подпиской.</p><button className="invite-primary" disabled={busy} onClick={approve}>{busy?'Подключаем…':'Разрешить подключение'}</button></>}{error&&<p role="alert">{error}</p>}</>}</section></div>;
}
