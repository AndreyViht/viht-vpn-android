import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronRight, X } from 'lucide-react';
import './BypassEditor.css';

function BypassForm({ settings, onUpdateSetting }) {
  const savedDomains = settings.bypassRules?.domains || settings.excludedDomains || [];
  const savedApps = settings.bypassRules?.apps || settings.excludedApps || [];
  const [domains, setDomains] = useState(savedDomains);
  const [excluded, setExcluded] = useState(savedApps);
  const [directRussia, setDirectRussia] = useState(settings.tvBypassRussia === true);
  const [site, setSite] = useState('');
  const [error, setError] = useState('');
  const [apps, setApps] = useState([]);
  const [appsError, setAppsError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const rulesChanged = JSON.stringify(domains) !== JSON.stringify(savedDomains) ||
    JSON.stringify(excluded) !== JSON.stringify(savedApps);
  const directChanged = directRussia !== (settings.tvBypassRussia === true);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setAppsError('');
      try {
        const result = await window.electronAPI?.getInstalledApps?.();
        if (!Array.isArray(result)) throw Error(result?.error || 'Не удалось получить приложения');
        if (active) setApps(result);
      } catch (failure) {
        if (active) setAppsError(failure.message);
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [attempt]);

  async function apply() {
    setSaving(true);
    setError('');
    try {
      const result = await onUpdateSetting('bypassRules', { domains, apps: excluded, directRussia });
      if (!result?.ok) throw Error(result?.error || 'Не удалось сохранить исключения');
    } catch (failure) {
      setError(failure.message);
    } finally {
      setSaving(false);
    }
  }

  function addSite(event) {
    event.preventDefault();
    try {
      const value = site.trim().replace(/^\*\./, '');
      const url = new URL(value.includes('://') ? value : 'https://' + value);
      if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password || !url.hostname.includes('.')) {
        throw Error('Введите домен, например example.com');
      }
      setDomains(items => [...new Set([...items, url.hostname.replace(/\.$/, '').toLowerCase()])]);
      setSite('');
      setError('');
    } catch (failure) {
      setError(failure.message);
    }
  }

  const installed = new Set(apps.map(app => app.packageName));
  const choices = [...apps, ...excluded.filter(pkg => !installed.has(pkg)).map(pkg => ({ packageName: pkg, label: pkg }))];
  return <>
    <div className="bypass-form" data-tv-scroll>
      <button type="button" className="bypass-option" aria-pressed={directRussia} disabled={saving} onClick={() => setDirectRussia(value => !value)}>
        <span>Российские сервисы напрямую</span><span>{directRussia ? 'Вкл.' : 'Выкл.'}</span>
      </button>
      <section aria-labelledby="bypass-app-title">
        <h3 id="bypass-app-title">Приложения вне VPN</h3>
        {loading && <p role="status">Загрузка приложений…</p>}
        {appsError && <button type="button" className="btn btn--secondary" onClick={() => setAttempt(value => value + 1)}>Повторить загрузку</button>}
        {appsError && <p role="alert">{appsError}</p>}
        {!loading && !appsError && !choices.length && <p>Приложения для выбора не найдены.</p>}
        {choices.map(app => {
          const selected = excluded.includes(app.packageName);
          return <button key={app.packageName} type="button" className="bypass-option" aria-pressed={selected} disabled={saving}
            onClick={() => setExcluded(items => selected ? items.filter(pkg => pkg !== app.packageName) : [...items, app.packageName])}>
            <span>{app.label}</span><span className="bypass-check" aria-hidden="true">{selected && <Check size={17} />}</span>
          </button>;
        })}
      </section>
      <section aria-labelledby="bypass-site-title">
        <h3 id="bypass-site-title">Сайты вне VPN</h3>
        <form className="bypass-site" onSubmit={addSite}>
          <input aria-label="Адрес сайта" value={site} onChange={event => setSite(event.target.value)} placeholder="example.com" disabled={saving} />
          <button className="btn btn--secondary" type="submit" disabled={saving}>Добавить</button>
        </form>
        {domains.map(domain => <button type="button" className="bypass-option" key={domain} disabled={saving} aria-label={'Удалить ' + domain}
          onClick={() => setDomains(items => items.filter(item => item !== domain))}><span>{domain}</span><X size={17} /></button>)}
      </section>
    </div>
    <footer className="bypass-footer">
      <div>{error ? <p role="alert">{error}</p> : <span>Вне VPN · приложений: {excluded.length}, сайтов: {domains.length}</span>}</div>
      <button className="btn btn--primary" data-tv-apply disabled={saving || !(rulesChanged || directChanged)} onClick={apply}>
        {saving ? 'Применение…' : 'Применить'}
      </button>
    </footer>
  </>;
}

export default function BypassEditor({ settings, onUpdateSetting }) {
  const [open, setOpen] = useState(false);
  const trigger = useRef(null);
  const count = (settings.bypassRules?.domains || settings.excludedDomains || []).length +
    (settings.bypassRules?.apps || settings.excludedApps || []).length;

  function close() {
    setOpen(false);
    requestAnimationFrame(() => trigger.current?.focus({ preventScroll: true }));
  }

  return <>
    <button type="button" className="bypass-settings-row" ref={trigger} aria-haspopup="dialog" onClick={() => setOpen(true)}>
      <strong>Раздельное туннелирование</strong>
      <span>{count ? count + ' искл.' : settings.tvBypassRussia ? 'Включено' : 'Выкл.'}</span>
      <ChevronRight size={17} />
    </button>
    {open && createPortal(
      <div className="fluent-modal-overlay bypass-overlay" onClick={event => { if (event.target === event.currentTarget) close(); }}>
        <section className="bypass-dialog" role="dialog" aria-modal="true" aria-labelledby="bypass-title">
          <header className="bypass-dialog-header">
            <h2 id="bypass-title">Раздельное туннелирование</h2>
            <button type="button" className="fluent-modal-close" aria-label="Закрыть" onClick={close}><X size={20} /></button>
          </header>
          <BypassForm settings={settings} onUpdateSetting={onUpdateSetting} />
        </section>
      </div>, document.body
    )}
  </>;
}
