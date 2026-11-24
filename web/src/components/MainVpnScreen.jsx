import React, {useEffect, useState} from 'react';
import {motion, AnimatePresence, useReducedMotion} from 'framer-motion';
import {Power, ShieldCheck, ArrowDown, ArrowUp, ArrowUpRight, Loader2, CreditCard, AlertTriangle, RotateCw} from 'lucide-react';
import {SessionTimer, TransferValue, PhoneTrafficDetails} from './LiveVpnValues';
import './MinimalHome.css';

export default function MainVpnScreen({accessDisabled=false, user, currentServer, connectionState, connectedAt, connectionError, reconnecting = false, onToggleConnection, onRenew, lang='ru'}) {
  const reduced = useReducedMotion();
  const connected = connectionState === 'connected';
  const pending = ['connecting', 'disconnecting'].includes(connectionState);
  const english = lang === 'en';
  const label = connected ? (english ? 'Connected' : 'Подключено')
    : connectionState === 'connecting' ? (reconnecting ? (english ? 'Reconnecting' : 'Переподключение') : (english ? 'Connecting' : 'Подключение'))
    : connectionState === 'disconnecting' ? (english ? 'Disconnecting' : 'Отключение')
    : connectionState === 'error' ? (english ? 'Try again' : 'Повторить')
    : (english ? 'Connect' : 'Подключиться');


  const [, refreshExpiry] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => refreshExpiry(value => value + 1), 60000);
    return () => clearInterval(timer);
  }, []);
  const expires = user?.subscription_expires_at ? new Date(user.subscription_expires_at).getTime() : null;
  const hours = expires ? Math.max(0, Math.floor((expires - Date.now()) / 3600000)) : Number(user?.subscription_days_left || 0) * 24 + Number(user?.subscription_hours_left || 0);
  const validity = hours > 0 ? Math.floor(hours / 24) + (english ? ' d ' : ' дн. ') + hours % 24 + (english ? ' h' : ' ч.') : (english ? 'Expired' : 'Истекла');


  return <section className="viht-minimal-home">
    {accessDisabled && <p className="viht-home-notice" role="alert">Доступ отключён владельцем подписки</p>}
    <div className="viht-home-stage">
      <motion.button type="button"
        className={`viht-connect-orb state-${connectionState}`}
        disabled={pending || accessDisabled || (!connected && !currentServer)}
        onClick={() => { void window.electronAPI?.vpnHaptic?.()?.catch?.(() => {}); onToggleConnection?.(); }}
        aria-label={connected ? (english ? 'Disconnect VPN' : 'Отключить VPN') : label}
        aria-busy={pending}
        whileTap={reduced ? undefined : {scale: .96}}
        transition={{type:'spring', stiffness:300, damping:26}}>
        <span className="viht-orb-halo" aria-hidden="true"/>
        <span className="viht-orb-rim" aria-hidden="true"/>
        <span className="viht-orb-content">
          <AnimatePresence initial={false} mode="wait">
            <motion.span className="viht-orb-icon" key={reconnecting && pending ? 'reconnecting' : connectionState}
              initial={{opacity:0, y:reduced ? 0 : 8}} animate={{opacity:1, y:0}}
              exit={{opacity:0, y:reduced ? 0 : -6}} transition={{duration:reduced ? 0 : .18}}>
              {pending ? (reconnecting ? <RotateCw className="viht-home-spinner" size={30}/> : <Loader2 className="viht-home-spinner" size={30}/>) : connected ? <ShieldCheck size={32}/> : connectionState === 'error' ? <AlertTriangle size={30}/> : <Power size={32}/>}
            </motion.span>
          </AnimatePresence>
          <span className="viht-orb-label" aria-live="polite">{label}</span>
          <span className="viht-orb-timer">
            {connected ? <SessionTimer connectedAt={connectedAt}/> : pending ? '•••' : 'VPN'}
          </span>
          <span className={`viht-orb-speeds ${connected ? 'is-visible' : ''}`}>
            <span><ArrowDown size={12}/>{connected ? <TransferValue field="speedDown"/> : '—'}</span>
            <span><ArrowUp size={12}/>{connected ? <TransferValue field="speedUp"/> : '—'}</span>
          </span>
          {connected && <small className="viht-traffic-scope">{english ? 'Through VPN' : 'Через VPN'}</small>}
          {currentServer?.name && <small className="viht-orb-location">{currentServer.name}</small>}
        </span>
      </motion.button>
      {connected && <PhoneTrafficDetails/>}
      {connectionState === 'error' && <div className="viht-home-error"><p role="alert">{connectionError || 'Не удалось подключить VPN.'}</p>{window.electronAPI?.checkVpnConnection && <button type="button" onClick={()=>window.dispatchEvent(new Event('viht-vpn-diagnostics'))}>Проверить подключение</button>}</div>}
      {!currentServer && !connected && <p className="viht-home-hint">{english ? 'Choose a server in Servers' : 'Выберите сервер во вкладке «Серверы»'}</p>}
    </div>
    <div className="viht-home-subscription">
      <div><span>{english ? 'Subscription valid for' : 'Срок действия подписки'}</span><strong>{validity}</strong></div>
      <button type="button" className="viht-home-renew" onClick={onRenew} disabled={!user || user.is_guest}>
        <CreditCard size={15}/>{user?.is_guest ? (english ? 'Owner subscription' : 'Подписка владельца') : (english ? 'Renew subscription' : 'Продлить подписку')}
      </button>
    </div>
  </section>;
}

