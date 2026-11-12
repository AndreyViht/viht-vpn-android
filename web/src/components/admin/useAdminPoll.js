import {useEffect, useRef} from 'react';

// Schedule after completion: slow responses never stack up background requests.
export default function useAdminPoll(load, delay = 15000, enabled = true) {
  const latest = useRef(load);
  useEffect(() => { latest.current = load; }, [load]);
  useEffect(() => {
    if (!enabled) return;
    let stopped = false, running = false, timer;
    const tick = async () => {
      clearTimeout(timer);
      if (stopped || running) return;
      running = true;
      try { if (!document.hidden) await latest.current(); }
      catch (error) { console.error('Admin refresh failed', error?.message); }
      finally { running = false; if (!stopped) timer = setTimeout(tick, delay); }
    };
    const resume = () => { if (!document.hidden) void tick(); };
    timer = setTimeout(tick, delay);
    document.addEventListener('visibilitychange', resume);
    window.addEventListener('online', resume);
    window.addEventListener('pageshow', resume);
    window.addEventListener('focus', resume);
    return () => { stopped = true; clearTimeout(timer); document.removeEventListener('visibilitychange', resume); window.removeEventListener('online', resume);window.removeEventListener('pageshow',resume);window.removeEventListener('focus',resume); };
  }, [delay, enabled]);
}
