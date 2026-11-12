import {useCallback,useEffect,useId,useLayoutEffect,useRef,useState} from 'react';
import {createPortal} from 'react-dom';

function FeedbackDialog({open,onDismiss,onAfterClose,children,labelId,className=''}){
 const ref=useRef(null),generation=useRef(0),after=useRef(onAfterClose);useLayoutEffect(()=>{after.current=onAfterClose},[onAfterClose]);
 useLayoutEffect(()=>{
  const dialog=ref.current,epoch=++generation.current;if(!dialog)return;
  if(open){dialog.classList.remove('is-closing');if(!dialog.open)dialog.showModal();dialog.querySelector('[data-default-focus]')?.focus({preventScroll:true});return;}
  if(!dialog.open)return;
  const finish=()=>{if(epoch!==generation.current)return;dialog.close();dialog.classList.remove('is-closing');after.current?.()};
  if(window.matchMedia('(prefers-reduced-motion: reduce)').matches){finish();return;}
  dialog.classList.add('is-closing');const animation=dialog.animate([{opacity:1,transform:'translate3d(0,0,0) scale(1)'},{opacity:0,transform:'translate3d(0,8px,0) scale(.98)'}],{duration:150,easing:'ease-in',fill:'forwards'});
  animation.finished.then(finish).catch(()=>{});return()=>animation.cancel();
 },[open]);
 useEffect(()=>()=>{generation.current++;ref.current?.close()},[]);
 return createPortal(<dialog ref={ref} className={'admin-feedback-dialog clients-glass-feedback '+className} aria-labelledby={labelId} onCancel={event=>{event.preventDefault();onDismiss()}} onKeyDown={event=>{if(event.key==='Escape')event.stopPropagation()}} onClick={event=>{if(event.target!==event.currentTarget)return;const box=event.currentTarget.getBoundingClientRect();if(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom)onDismiss()}}>{children}</dialog>,document.body);
}

export function useAdminConfirmation(){
 const [request,setRequest]=useState(null),[last,setLast]=useState(null),resolver=useRef(null),decision=useRef(null),id=useId();

 const ask=useCallback(options=>{if(resolver.current)return Promise.resolve(false);decision.current=null;setLast(options);return new Promise(resolve=>{resolver.current=resolve;setRequest(options)})},[]);
 const settle=value=>{if(!resolver.current||decision.current!==null)return;decision.current=value;setRequest(null)};
 const closed=()=>{const resolve=resolver.current;resolver.current=null;resolve?.(decision.current===true)};
 useEffect(()=>()=>{resolver.current?.(false);resolver.current=null},[]);
 const content=last;
 const dialog=<FeedbackDialog open={!!request} onDismiss={()=>settle(false)} onAfterClose={closed} labelId={id} className={content?.danger?'is-danger':''}>{content&&<>
  <div className="admin-feedback-mark" aria-hidden="true">{content.danger?'−':'+'}</div><span className="admin-feedback-eyebrow">ПОДТВЕРЖДЕНИЕ ДЕЙСТВИЯ</span><h2 id={id}>{content.title}</h2><p>{content.description}</p>
  <div className="admin-feedback-receipt"><span>Клиент</span><strong>{content.client}</strong>{content.value&&<><span>Изменение</span><strong>{content.value}</strong></>}</div>
  {content.note&&<small>{content.note}</small>}
  <button className="admin-feedback-primary" disabled={!request} onClick={()=>settle(true)}>{content.confirmLabel||'Подтвердить'}</button><button className="admin-feedback-secondary" data-default-focus disabled={!request} onClick={()=>settle(false)}>Отмена</button>
 </>}</FeedbackDialog>;
 return {ask,dialog};
}

export function AdminResultDialog({data,onClose}){
 const [last,setLast]=useState(data),id=useId();if(data&&data!==last)setLast(data);const result=data||last;
 const title=!result?'':result.type==='slots'?'Лимит устройств обновлён':result.type==='disable'?'Подписка обнулена':result.type==='add'?'Дни зачислены':'Дни списаны';
 const value=!result?'':result.type==='slots'?`${result.totalSlots} сл.`:result.type==='disable'?'0 дней':`${result.type==='add'?'+':'−'}${result.days} дней`;
 return <FeedbackDialog open={!!data} onDismiss={onClose} labelId={id} className="admin-feedback-result">{result&&<>
  <div className="admin-feedback-check" aria-hidden="true"><svg viewBox="0 0 40 40"><circle cx="20" cy="20" r="17"/><path d="m11 20 6 6 12-13"/></svg></div><span className="admin-feedback-eyebrow">ИЗМЕНЕНИЯ СОХРАНЕНЫ</span><h2 id={id}>{title}</h2><div className="admin-feedback-value">{value}</div>
  <div className="admin-feedback-receipt"><span>Клиент</span><strong>{result.clientName}</strong>{result.expiresAt&&<><span>Действует до</span><strong>{new Date(result.expiresAt).toLocaleString('ru-RU',{dateStyle:'medium',timeStyle:'short'})}</strong></>}<span>Telegram</span><strong>{result.notified?'Уведомление отправлено':result.notificationQueued?'Уведомление отправляется':result.notificationRequested?'Доставка не подтверждена':'Без уведомления'}</strong></div>
  <button className="admin-feedback-primary" data-default-focus onClick={onClose}>Готово</button>
 </>}</FeedbackDialog>;
}
