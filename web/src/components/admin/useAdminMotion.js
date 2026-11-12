import {useCallback,useEffect,useLayoutEffect,useRef} from 'react';

const reduced=()=>window.matchMedia('(prefers-reduced-motion: reduce)').matches;

// Move the existing selection appearance; do not introduce a new visual theme.
export function useAdminSlidingPill(selection,selector){
 const containerRef=useRef(null),indicatorRef=useRef(null),appearance=useRef(null);
 const measure=useCallback(()=>{
  const container=containerRef.current,pill=indicatorRef.current,active=container?.querySelector(selector);
  if(!container||!pill||!active||!container.getClientRects().length||!active.offsetWidth)return;
  if(!appearance.current){const css=getComputedStyle(active);appearance.current=Object.fromEntries(['backgroundColor','backgroundImage','borderColor','borderStyle','borderWidth','borderRadius','boxShadow'].map(key=>[key,css[key]]));Object.assign(pill.style,appearance.current);}
  Object.assign(pill.style,{width:active.offsetWidth+'px',height:active.offsetHeight+'px',transform:`translate3d(${active.offsetLeft}px,${active.offsetTop}px,0)`});
  container.classList.add('admin-motion-ready');
 },[selector]);
 useLayoutEffect(()=>{measure()},[selection,measure]);
 useEffect(()=>{
  const container=containerRef.current;if(!container)return;
  const observer=typeof ResizeObserver==='function'?new ResizeObserver(measure):null;observer?.observe(container);
  window.addEventListener('resize',measure,{passive:true});
  document.fonts?.addEventListener('loadingdone',measure);
  return()=>{observer?.disconnect();window.removeEventListener('resize',measure);document.fonts?.removeEventListener('loadingdone',measure)};
 },[measure]);
 return {containerRef,indicatorRef};
}

function adminScrollTarget(){
 return document.body.classList.contains('admin-ios-viewport') ? document.querySelector('.admin-shell') || window : window;
}

export function useAdminPageMotion(pathname,navigate){
 const pageRef=useRef(null),previous=useRef(pathname),positions=useRef(new Map()),restoring=useRef(false),restoreCleanup=useRef(null);
 const currentPath=useRef(pathname);useLayoutEffect(()=>{currentPath.current=pathname},[pathname]);
 useEffect(()=>{
  const prior=window.history.scrollRestoration;window.history.scrollRestoration='manual';
  const save=()=>{const target=adminScrollTarget();if(!restoring.current)positions.current.set(currentPath.current,target===window?window.scrollY:target.scrollTop)};
  window.addEventListener('scroll',save,{passive:true,capture:true});
  return()=>{window.removeEventListener('scroll',save,true);window.history.scrollRestoration=prior;restoreCleanup.current?.();};
 },[]);
 useLayoutEffect(()=>{
  if(previous.current===pathname)return;previous.current=pathname;restoreCleanup.current?.();
  const element=pageRef.current;if(!element)return;
  if(!reduced())element.animate?.([{opacity:.4,transform:'translate3d(0,7px,0)'},{opacity:1,transform:'translate3d(0,0,0)'}],{duration:240,easing:'cubic-bezier(.2,.8,.2,1)'});
  const target=positions.current.get(pathname)||0;restoring.current=true;
  let observer,timer,frame,stopped=false;
  const stop=()=>{if(stopped)return;stopped=true;restoring.current=false;observer?.disconnect();clearTimeout(timer);cancelAnimationFrame(frame);window.removeEventListener('pointerdown',stop);window.removeEventListener('wheel',stop);window.removeEventListener('keydown',stop)};
  const restore=()=>{if(stopped)return;const scroll=adminScrollTarget();scroll.scrollTo({top:target,behavior:'instant'});const max=scroll===window?document.documentElement.scrollHeight-window.innerHeight:scroll.scrollHeight-scroll.clientHeight;if(target<=Math.max(0,max)+1)stop()};
  frame=requestAnimationFrame(restore);
  if(target>0&&typeof ResizeObserver==='function'){observer=new ResizeObserver(restore);observer.observe(element)}
  timer=setTimeout(stop,3000);window.addEventListener('pointerdown',stop,{passive:true});window.addEventListener('wheel',stop,{passive:true});window.addEventListener('keydown',stop);restoreCleanup.current=stop;
 },[pathname]);
 const go=useCallback((event,to,{replace=false,closeCurrent}={})=>{
  if(event.defaultPrevented||event.button!==0||event.metaKey||event.ctrlKey||event.shiftKey||event.altKey)return;
  event.preventDefault();
  if(to===pathname){if(closeCurrent)closeCurrent();else adminScrollTarget().scrollTo({top:0,behavior:reduced()?'instant':'smooth'});return;}
  const scroll=adminScrollTarget();positions.current.set(pathname,scroll===window?window.scrollY:scroll.scrollTop);
  navigate(to,{replace});
 },[pathname,navigate]);
 return {pageRef,go};
}

export function useAdminViewport(){
 useEffect(()=>{
  const mode=window.matchMedia('(display-mode: standalone)'),viewport=window.visualViewport;let frame,baseline=window.innerHeight;
  const ios=/iPhone|iPad|iPod/.test(navigator.userAgent)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
  const body=document.body;
  const update=()=>{
   cancelAnimationFrame(frame);frame=requestAnimationFrame(()=>{
    const app=mode.matches||navigator.standalone===true;
    document.documentElement.classList.toggle('is-admin-app',app);
    const fit=ios&&!!viewport&&!app&&Math.abs(viewport.scale-1)<.01;
    body.classList.toggle('admin-ios-viewport',fit);
    if(fit){
     body.style.setProperty('--admin-visible-top', Math.max(0,viewport.offsetTop)+'px');
     body.style.setProperty('--admin-visible-height', viewport.height+'px');
     body.style.setProperty('--admin-visible-bottom', Math.max(0,window.innerHeight-viewport.offsetTop-viewport.height)+'px');
    }
    const editing=document.activeElement?.matches('input:not([type=checkbox]):not([type=radio]),textarea,[contenteditable=true]');
    const height=viewport?.height||window.innerHeight;if(!editing)baseline=Math.max(height,window.innerHeight);
    document.body.classList.toggle('admin-keyboard-open',!!editing&&Math.max(baseline,window.innerHeight)-height>140);
   });
  };
  update();mode.addEventListener('change',update);viewport?.addEventListener('resize',update);viewport?.addEventListener('scroll',update);window.addEventListener('resize',update,{passive:true});document.addEventListener('focusin',update);document.addEventListener('focusout',update);
  return()=>{cancelAnimationFrame(frame);mode.removeEventListener('change',update);viewport?.removeEventListener('resize',update);viewport?.removeEventListener('scroll',update);body.classList.remove('admin-ios-viewport');for(const name of ['top','height','bottom'])body.style.removeProperty('--admin-visible-'+name);window.removeEventListener('resize',update);document.removeEventListener('focusin',update);document.removeEventListener('focusout',update);document.documentElement.classList.remove('is-admin-app');document.body.classList.remove('admin-keyboard-open')};
 },[]);
}
