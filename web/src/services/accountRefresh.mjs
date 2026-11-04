// Shared behavior with Android: resume promptly, poll while visible, coalesce requests.
export function observeAccount({refresh,token,interval=30000,bridge,window:win=window,document:doc=document,now=Date.now,setTimer=setInterval,clearTimer=clearInterval}) {
 let stopped=false,running=false,last=-Infinity;
 const run=async event=>{
  if(stopped||running||doc.hidden||win.__vihtUiBackground===true||event?.detail?.token&&event.detail.token!==token||now()-last<1000)return;
  running=true;last=now();
  try{await refresh()}catch{}finally{running=false}
 };
 const visible=()=>{if(!doc.hidden)void run()};
 for(const event of ['focus','online','viht-account-refresh'])win.addEventListener(event,run);
 doc.addEventListener('visibilitychange',visible);
 const off=bridge?.onAppResumed?.(run),timer=setTimer(run,interval);
 return()=>{stopped=true;clearTimer(timer);off?.();for(const event of ['focus','online','viht-account-refresh'])win.removeEventListener(event,run);doc.removeEventListener('visibilitychange',visible)};
}
export function accountRevision(profile) {
 return JSON.stringify([profile?.id,profile?.status,profile?.expires_at||profile?.subscription_expires_at,profile?.subscription_token,profile?.limits]);
}
