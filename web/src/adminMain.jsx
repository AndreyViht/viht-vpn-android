import React, { useCallback, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { LayoutDashboard, LogOut, Users } from 'lucide-react';
import { AdminFetchContext } from './components/admin/AdminFetchContext.jsx';
import AdminClients from './components/admin/AdminClients.jsx';
import AdminOverview from './components/admin/AdminOverview.jsx';
import './components/admin/AdminClientDialog.css';
import './components/admin/AdminClientPolish.css';
import './components/admin/MobileAdmin.css';
import './adminStandalone.css';

async function request(action, extra = {}) {
  try {
    const response = await fetch('/api/admin-api', {
      method: 'POST', credentials: 'same-origin', redirect: 'error', cache: 'no-store',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...extra, action }),
    });
    return { ...await response.json(), statusCode: response.status };
  } catch {
    return { ok: false, error: 'Нет связи с сервером. Проверьте результат перед повтором действия.' };
  }
}

function App() {
  const [session, setSession] = useState(null);
  const [telegramId, setTelegramId] = useState('');
  const [pending, setPending] = useState(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [tab, setTab] = useState('dashboard');
  useEffect(() => { request('admin_web_me').then(result => setSession(result.ok === true)); }, []);
  useEffect(() => {
    document.body.classList.add('viht-mobile-admin');
    const notice = event => setMessage(event.detail?.text || '');
    const back = event => {
      const dialog = document.querySelector('.admin-cm-overlay, dialog[open]');
      if (!dialog) return;
      event.preventDefault();
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    };
    window.addEventListener('viht-admin-notice', notice);
    window.addEventListener('viht-back', back);
    return () => {
      window.removeEventListener('viht-admin-notice', notice);
      window.removeEventListener('viht-back', back);
    };
  }, []);
  const adminFetch = useCallback(async (action, extra) => {
    const result = await request(action, extra);
    if ([401, 403].includes(result.statusCode)) {
      setSession(false);
      setMessage('Войдите в аккаунт администратора.');
    }
    return result;
  }, []);
  useEffect(() => {
    if (!pending) return;
    let cancelled = false;
    let timer;
    async function poll() {
      const result = await request('admin_web_poll', { request_id: pending.request_id, poll_token: pending.poll_token });
      if (cancelled) return;
      if (result.status === 'approved') {
        const me = await request('admin_web_me');
        if (cancelled) return;
        setPending(null);
        setSession(me.ok === true);
        setMessage(me.ok ? '' : 'Сервер не подтвердил сессию.');
      } else if (result.status === 'pending' || (!result.statusCode && Date.now() < Date.parse(pending.expires_at))) {
        timer = setTimeout(poll, 2000);
      } else {
        setPending(null);
        setMessage('Вход не подтверждён. Отправьте новый запрос.');
      }
    }
    timer = setTimeout(poll, 1000);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [pending]);

  async function login(event) {
    event.preventDefault();
    setBusy(true);
    setMessage('');
    const result = await request('admin_web_start', { telegram_id: telegramId });
    setBusy(false);
    if (result.request_id && result.poll_token) setPending(result);
    setMessage(result.ok ? 'Подтвердите запрос в Telegram.' : result.error || 'Не удалось начать вход.');
  }

  async function logout() {
    const result = await request('admin_web_logout');
    if (result.ok) {
      setSession(false);
      setMessage('');
    } else setMessage('Не удалось завершить сессию. Повторите выход.');
  }

  return <div className="viht-admin-app">
    <header className="standalone-header"><strong>Viht Admin</strong>{session && <button type="button" aria-label="Выйти" onClick={logout}><LogOut size={20}/></button>}</header>
    {message && <p className="standalone-notice" role="status">{message}</p>}
    {session === null ? <p className="standalone-notice">Проверяем вход…</p> : session ? <>
      <main className="mobile-admin"><AdminFetchContext.Provider value={adminFetch}>
        {tab === 'clients' ? <AdminClients/> : <AdminOverview/>}
      </AdminFetchContext.Provider></main>
      <nav className="standalone-nav" aria-label="Основная навигация">
        <button aria-current={tab === 'dashboard' ? 'page' : undefined} onClick={() => setTab('dashboard')}><LayoutDashboard size={20}/>Дашборд</button>
        <button aria-current={tab === 'clients' ? 'page' : undefined} onClick={() => setTab('clients')}><Users size={20}/>Клиенты</button>
      </nav>
    </> : <form className="standalone-login" onSubmit={login}>
      <h1>Вход администратора</h1>
      <label htmlFor="telegram-id">Telegram ID</label>
      <input id="telegram-id" inputMode="numeric" pattern="[0-9]+" required value={telegramId} onChange={event => setTelegramId(event.target.value)} disabled={busy || !!pending}/>
      <button disabled={busy || !!pending}>Подтвердить через Telegram</button>
      {pending && <button type="button" onClick={() => { setPending(null); setMessage(''); }}>Отменить</button>}
    </form>}
  </div>;
}

createRoot(document.getElementById('root')).render(<App/>);
