import {useCallback,useEffect,useLayoutEffect,useRef,useState} from 'react';
import {createPortal} from 'react-dom';
import {AnimatePresence,motion,useReducedMotion} from 'framer-motion';
import {Bell,Check,X,ChevronRight} from 'lucide-react';
import {newsPopoverLayout} from '../utils/newsPopoverLayout.mjs';
import './AppAnnouncements.css';

export default function AppAnnouncements({accountId,enabled,onUnreadChange}){
 const [items,setItems]=useState([]),[open,setOpen]=useState(false),[selected,setSelected]=useState(null),[loading,setLoading]=useState(false),[error,setError]=useState('');
 const busy=useRef(false),alive=useRef(true),requested=useRef(null);
 const sheet=useRef(null),reducedMotion=useReducedMotion();
 const [position,setPosition]=useState(null);
 const measure=useCallback(()=>{
  const view=window.visualViewport;
  const width=view?.width||window.innerWidth,height=view?.height||window.innerHeight;
  const anchor=document.querySelector('.titlebar-news')?.getBoundingClientRect()||{left:width-54,right:width-18,bottom:45};
  setPosition(newsPopoverLayout(anchor,{width,height,left:view?.offsetLeft||0,top:view?.offsetTop||0}));
 },[]);
 useLayoutEffect(()=>{
  if(!open)return;
  measure();window.addEventListener('resize',measure);window.visualViewport?.addEventListener('resize',measure);window.visualViewport?.addEventListener('scroll',measure);
  return()=>{window.removeEventListener('resize',measure);window.visualViewport?.removeEventListener('resize',measure);window.visualViewport?.removeEventListener('scroll',measure)};
 },[open,measure]);
 const load=useCallback(async()=>{
  if(busy.current||(document.hidden || window.__vihtUiBackground === true)||!window.electronAPI?.getAnnouncements)return;
  busy.current=true;setLoading(true);
  try{const result=await window.electronAPI.getAnnouncements();if(!alive.current)return;if(!result?.ok){setError('Новости пока недоступны. Повторите позже.');return;}
   const list=result.announcements||[];setItems(list);setError('');
   if(requested.current){const wanted=list.find(item=>item.id===requested.current);setSelected(wanted||null);setOpen(true);if(!wanted)setError('Объявление уже снято с публикации.');requested.current=null;}
   else{const latest=list.find(item=>!item.read);if(enabled&&latest&&!document.querySelector('dialog[open],.billing-shade,.fluent-modal-overlay')){const key='viht_news_auto:'+accountId+':'+latest.id;if(localStorage.getItem(key)!==latest.updated_at){localStorage.setItem(key,latest.updated_at);setSelected(latest);setOpen(true);}}}
  }catch{if(alive.current)setError('Не удалось загрузить новости.');}finally{busy.current=false;if(alive.current)setLoading(false)}
 },[accountId,enabled]);
 useEffect(()=>{alive.current=true;void load();const timer=setInterval(()=>{void load()},30000);const resume=()=>{void load()};const show=()=>{setSelected(null);setOpen(true);void load()};const off=window.electronAPI?.onAnnouncementOpen?.(data=>{requested.current=data.id;setSelected(null);setOpen(true);void load()});const resumed=window.electronAPI?.onAppResumed?.(resume);window.addEventListener('viht-open-news',show);return()=>{alive.current=false;clearInterval(timer);off?.();resumed?.();window.removeEventListener('viht-open-news',show)}},[load]);
 useEffect(()=>{onUnreadChange(items.filter(item=>!item.read).length)},[items,onUnreadChange]);
 useEffect(()=>{if(!open)return;const previous=document.activeElement,overflow=document.body.style.overflow;document.body.style.overflow='hidden';sheet.current?.focus({preventScroll:true});
  const back=e=>{e.preventDefault();setOpen(false)};window.addEventListener('viht-back',back);
  const keyboard=e=>{if(e.key==='Escape'){e.preventDefault();setOpen(false)}else if(e.key==='Tab'){
   const buttons=sheet.current?.querySelectorAll('button:not([disabled]),a[href],[tabindex="0"]');if(!buttons?.length){e.preventDefault();return}
   const first=buttons[0],last=buttons[buttons.length-1];if(e.shiftKey&&(document.activeElement===first||document.activeElement===sheet.current)){e.preventDefault();last.focus()}else if(!e.shiftKey&&(document.activeElement===last||document.activeElement===sheet.current)){e.preventDefault();first.focus()}
  }};window.addEventListener('keydown',keyboard);
  return()=>{document.body.style.overflow=overflow;window.removeEventListener('viht-back',back);window.removeEventListener('keydown',keyboard);if(previous?.isConnected)previous.focus?.({preventScroll:true})};
 },[open]);
 const read=async item=>{const response=await window.electronAPI?.readAnnouncement?.(item.id,item.updated_at);if(response?.ok){setItems(old=>old.map(p=>p.id===item.id?{...p,read:true}:p));setOpen(false)}else setError('Не удалось отметить прочитанным. Попробуйте ещё раз.')};
 return createPortal(<AnimatePresence>{open&&<motion.div className="fluent-modal-overlay app-news-overlay" initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} transition={{duration:reducedMotion?0:.16}} onClick={e=>{if(e.target===e.currentTarget)setOpen(false)}}><motion.section ref={sheet} tabIndex={-1} className="app-news-sheet" style={position||{visibility:'hidden'}} initial={{opacity:0,scale:reducedMotion?1:.94,y:reducedMotion?0:-8}} animate={{opacity:1,scale:1,y:0}} exit={{opacity:0,scale:reducedMotion?1:.97,y:reducedMotion?0:-4}} transition={{duration:reducedMotion?0:.2,ease:[.2,.8,.2,1]}} role="dialog" aria-modal="true" aria-labelledby="app-news-title"><header><div><span>VIHT VPN</span><h2 id="app-news-title">{selected?'Объявление':'Новости'}</h2></div><button className="fluent-modal-close" aria-label="Закрыть новости" onClick={()=>setOpen(false)}><X size={19}/></button></header>{error&&<p className="app-news-error" role="status">{error}</p>}{loading&&!items.length?<p className="app-news-empty">Загружаем новости…</p>:selected?<article><time>{new Date(selected.published_at).toLocaleDateString('ru-RU')}</time><h3>{selected.title}</h3><p className="app-news-body">{selected.body}</p><button className="app-news-read" onClick={()=>read(selected)}><Check size={17}/>Прочитал</button><button className="app-news-all" onClick={()=>setSelected(null)}>Все объявления</button></article>:items.length?<div className="app-news-list">{items.map(item=><button key={item.id} onClick={()=>setSelected(item)}><span className={item.read?'':'is-unread'}><strong>{item.title}</strong><small>{new Date(item.published_at).toLocaleDateString('ru-RU')}{!item.read?' · Новое':''}</small></span><ChevronRight size={17}/></button>)}</div>:<div className="app-news-empty"><Bell size={29}/><p>Новых объявлений пока нет</p></div>}</motion.section></motion.div>}</AnimatePresence>,document.body);
}
