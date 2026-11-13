import React, {useCallback, useEffect, useRef, useState} from 'react';
import {motion, useReducedMotion} from 'framer-motion';
import {Activity, Check, RefreshCw, Pause, Play, ArrowDownUp} from 'lucide-react';
import CountryFlag from './CountryFlag';
import {getServerCountry} from '../services/servers';
import './ServersLive.css';

function serverName(name) {
  return String(name || '').replace(/[\uD83C][\uDDE6-\uDDFF][\uD83C][\uDDE6-\uDDFF]/g, '').replace(/^(DE|NL|SE|RU|EU)\s+/i, '').replace(/^[-–—•\s]+/, '').trim();
}

function useLivePing(servers, enabled) {
  const [samples, setSamples] = useState({});
  const [measuring, setMeasuring] = useState(false);
  const [error, setError] = useState('');
  const current = useRef(servers);
  const request = useRef(null);
  const active = useRef(false);
  const generation = useRef(0);
  current.current = servers;
  const signature = JSON.stringify(servers.map(server => [server.id, server.vless_uri]));
  const measure = useCallback(async () => {
    if (!active.current || document.hidden || window.__vihtUiBackground || request.current || !current.current.length) return;
    if (!window.electronAPI?.pingServers) { setError('Замер доступен в приложении Viht'); return; }
    const snapshot = current.current;
    const epoch = generation.current;
    const flight = {};
    request.current = flight;
    setMeasuring(true);
    try {
      const result = await window.electronAPI.pingServers(snapshot.map(server => ({...server, attempts:1, timeoutMs:900})));
      if (!active.current || epoch !== generation.current) return;
      const at = Date.now();
      setSamples(previous => Object.fromEntries(snapshot.map(server => {
        const value = result?.[server.id];
        const reachable = Number.isFinite(value) && value >= 0;
        const history = [...(previous[server.id]?.history || []), reachable ? value : null].slice(-12);
        return [server.id, {ping:reachable ? value : null, at, history}];
      })));
      setError('');
    } catch {
      if (active.current && epoch === generation.current) setError('Не удалось измерить задержку. Повторяем автоматически.');
    } finally {
      if (request.current === flight) request.current = null;
      if (active.current) setMeasuring(false);
    }
  }, []);
  useEffect(() => {
    active.current = enabled;
    generation.current += 1;
    setSamples(previous => Object.fromEntries(current.current.filter(server => previous[server.id]).map(server => [server.id, previous[server.id]])));
    setError('');
    setMeasuring(false);
    if (!enabled) return;
    void measure();
    const interval = setInterval(measure,1000);
    const resume = () => { if (!document.hidden && !window.__vihtUiBackground) void measure(); };
    document.addEventListener('visibilitychange',resume);
    window.addEventListener('focus',resume);
    return () => {
      active.current = false;
      generation.current += 1;
      clearInterval(interval);
      document.removeEventListener('visibilitychange',resume);
      window.removeEventListener('focus',resume);
    };
  }, [enabled, signature, measure]);
  return {samples, measuring, error, measure};
}

export default React.memo(function ServersScreen({accessDisabled=false,servers=[],currentServer,connectionState,onSelectServer,onRefreshKeys,refreshingKeys,lang='ru'}) {
  const [live,setLive] = useState(true);
  const [syncResult,setSyncResult] = useState('');
  const [syncTime,setSyncTime] = useState(null);
  const [syncing,setSyncing] = useState(false);
  const mounted = useRef(true);
  const syncFlight = useRef(false);
  const reduced = useReducedMotion();
  const {samples,measuring,error} = useLivePing(servers,live && !accessDisabled);
  const english = lang === 'en';
  useEffect(() => {mounted.current=true;return()=>{mounted.current=false;};},[]);
  const synchronize = async () => {
    if (syncFlight.current) return;
    syncFlight.current=true;
    setSyncing(true);
    setSyncResult('');
    try {
      const result = await onRefreshKeys();
      if (!mounted.current) return;
      if (Array.isArray(result)) {
        setSyncTime(Date.now());
        setSyncResult(english ? 'Server list updated' : 'Список серверов обновлён');
      } else setSyncResult(english ? 'Sync failed. Try again.' : 'Не удалось обновить список. Повторите синхронизацию.');
    } catch {
      if (mounted.current) setSyncResult(english ? 'Sync failed. Try again.' : 'Не удалось обновить список. Повторите синхронизацию.');
    } finally {
      syncFlight.current=false;
      if (mounted.current) setSyncing(false);
    }
  };
  const busy = syncing || refreshingKeys;
  return <section className="fluent-page viht-server-page">
    <header className="viht-server-heading">
      <h1>{english ? 'Servers' : 'Серверы'}</h1>
      <p>{english ? 'Choose your connection location' : 'Выберите, через какую локацию подключаться'}</p>
    </header>
    {accessDisabled && <p role="alert" className="guest-access-notice">Доступ отключён владельцем подписки</p>}
    <div className="viht-server-actions">
      <button type="button" className="viht-server-action" aria-pressed={live} disabled={accessDisabled} onClick={()=>setLive(value=>!value)}>
        <span className="viht-server-action-icon"><Activity size={19}/></span>
        <span><strong>{english ? 'Live ping' : 'Живой пинг'}</strong><small>{live ? (english ? 'Every second · TCP' : 'Каждую секунду · TCP') : (english ? 'Paused' : 'Приостановлен')}</small></span>
        {live ? <Pause size={14}/> : <Play size={14}/>}
      </button>
      <button type="button" className="viht-server-action" disabled={busy} aria-busy={busy} onClick={synchronize}>
        <span className="viht-server-action-icon"><RefreshCw size={19} className={busy?'spinning':''}/></span>
        <span><strong>{busy ? (english ? 'Updating…' : 'Обновляем…') : (english ? 'Sync' : 'Синхронизация')}</strong><small>{syncTime ? new Date(syncTime).toLocaleTimeString(english?'en-GB':'ru-RU',{hour:'2-digit',minute:'2-digit'}) : (english ? 'From your subscription' : 'Из вашей подписки')}</small></span>
      </button>
    </div>
    {syncResult && <p className="viht-server-feedback" role="status">{syncResult}</p>}
    {error && <p className="viht-server-feedback" role="status">{error}</p>}
    <div className="viht-server-list">
      {servers.map((server,index)=>{
        const selected=server.id===currentServer?.id;
        const sample=samples[server.id];
        const ping=sample?.ping;
        const reachable=Number.isFinite(ping);
        const quality=!sample?'pending':!reachable?'unreachable':ping<80?'fast':ping<160?'normal':'slow';
        const status=selected ? connectionState==='connected' ? (english?'Connected':'Подключено') : connectionState==='connecting' ? (english?'Connecting…':'Подключаемся…') : (english?'Selected':'Выбран') : (english?'Tap to select':'Нажмите для выбора');
        const history=sample?.history || [];
        const maximum=Math.max(100,...history.filter(Number.isFinite));
        return <motion.button type="button" className={`viht-server-item ${selected?'is-selected':''}`} key={server.id} aria-pressed={selected} disabled={accessDisabled} onClick={()=>onSelectServer(server)}
          initial={{opacity:0,y:reduced?0:10}} animate={{opacity:1,y:0}} transition={{duration:reduced?0:.28,delay:reduced?0:Math.min(index*.04,.2)}} whileTap={reduced?undefined:{scale:.985}}>
          <span className="viht-server-flag"><CountryFlag country={getServerCountry(server)}/></span>
          <span className="viht-server-info"><strong>{serverName(server.name)}</strong><small>{status}</small>
            {(server.isLte || server.isYouTubeNoAds) && <span className="viht-server-feature">{server.isLte?'LTE':''}{server.isLte&&server.isYouTubeNoAds?' · ':''}{server.isYouTubeNoAds?'YouTube':''}</span>}
          </span>
          <span className={`viht-server-latency quality-${quality}`}>
            <strong>{sample ? reachable ? <>{ping}<small>{english?'ms':'мс'}</small></> : (english?'No response':'Нет ответа') : '—'}</strong>
            <span className="viht-ping-history" aria-hidden="true">{Array.from({length:12},(_,i)=>{
              const value=history[i-(12-history.length)];
              return <i key={i} style={{height:value==null?'3px':Math.max(4,Math.round(value/maximum*18))+'px',opacity:value == null ? .2 : .8}}/>;
            })}</span>
            <small>{!live ? (english?'Paused':'Пауза') : !sample ? (english?'Measuring…':'Измеряем…') : reachable ? ping<80?(english?'Low latency':'Быстрый отклик'):ping<160?(english?'Normal latency':'Обычный отклик'):(english?'High latency':'Высокая задержка') : (english?'Checking again':'Проверяем снова')}</small>
          </span>
          <span className="viht-server-check" aria-hidden="true">{selected&&<Check size={15}/>}</span>
        </motion.button>;
      })}
    </div>
    {!servers.length && <div className="viht-server-empty"><ArrowDownUp size={26}/><strong>{english?'No available servers':'Нет доступных серверов'}</strong><p>{english?'Sync to load your subscription.':'Нажмите «Синхронизация», чтобы получить серверы из подписки.'}</p></div>}
    {!!servers.length && <p className="viht-server-footnote">{english?'TCP measures server connection latency.':'TCP-пинг показывает задержку соединения с сервером.'}{live&&measuring?' · '+(english?'Measuring':'Измеряем'):''}</p>}
  </section>;
});

