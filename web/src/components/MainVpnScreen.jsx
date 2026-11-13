import React from 'react';
import {motion, AnimatePresence, useReducedMotion} from 'framer-motion';
import {Power, ShieldCheck, ArrowDown, ArrowUp, ArrowUpRight, Loader2} from 'lucide-react';
import {SessionTimer, TransferValue} from './LiveVpnValues';
import './MinimalHome.css';

export default function MainVpnScreen({accessDisabled=false, user, currentServer, connectionState, connectedAt, onToggleConnection, lang='ru'}) {
  const reduced = useReducedMotion();
  const connected = connectionState === 'connected';
  const pending = ['connecting', 'disconnecting'].includes(connectionState);
  const english = lang === 'en';
  const label = connected ? (english ? 'Connected' : 'Подключено')
    : connectionState === 'connecting' ? (english ? 'Connecting' : 'Подключение')
    : connectionState === 'disconnecting' ? (english ? 'Disconnecting' : 'Отключение')
    : connectionState === 'error' ? (english ? 'Try again' : 'Повторить')
    : (english ? 'Connect' : 'Подключиться');


  const renew = () => {
    if (!user?.is_guest) window.dispatchEvent(new Event('viht-open-tariffs'));
  };

  return <section className="viht-minimal-home">
    {accessDisabled && <p className="viht-home-notice" role="alert">Доступ отключён владельцем подписки</p>}
    <div className="viht-home-stage">
      <motion.button type="button"
        className={`viht-connect-orb state-${connectionState}`}
        disabled={pending || accessDisabled || (!connected && !currentServer)}
        onClick={onToggleConnection}
        aria-label={connected ? (english ? 'Disconnect VPN' : 'Отключить VPN') : label}
        aria-busy={pending}
        whileTap={reduced ? undefined : {scale: .96}}
        transition={{type:'spring', stiffness:300, damping:26}}>
        <span className="viht-orb-halo" aria-hidden="true"/>
        <span className="viht-orb-rim" aria-hidden="true"/>
        <span className="viht-orb-content">
          <AnimatePresence initial={false} mode="wait">
            <motion.span className="viht-orb-icon" key={pending ? 'pending' : connected ? 'connected' : 'off'}
              initial={{opacity:0, y:reduced ? 0 : 8}} animate={{opacity:1, y:0}}
              exit={{opacity:0, y:reduced ? 0 : -6}} transition={{duration:reduced ? 0 : .18}}>
              {pending ? <Loader2 className="viht-home-spinner" size={30}/> : connected ? <ShieldCheck size={32}/> : <Power size={32}/>}
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
        </span>
      </motion.button>
      {!currentServer && !connected && <p className="viht-home-hint">{english ? 'Choose a server in Servers' : 'Выберите сервер во вкладке «Серверы»'}</p>}
    </div>
    {!user?.is_guest && <button type="button" className="viht-home-renew" onClick={renew}>
      {english ? 'Renew subscription' : 'Продлить подписку'}<ArrowUpRight size={15}/>
    </button>}
  </section>;
}

