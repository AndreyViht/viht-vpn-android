import React, {useEffect, useState} from 'react';
import {AndroidActionSheet} from './AndroidActionSheet';

const steps = {network:'Сеть телефона',dns:'DNS для anviht.ru',server:'TCP до выбранного сервера',tunnel:'Передача через VPN'};
const outcomes = {ok:'Работает',failed:'Нет ответа',skipped:'Не проверено'};

export default function VpnDiagnosticsPanel({server}) {
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [attempt, retry] = useState(0);
  const [open, setOpen] = useState(false);
  useEffect(() => {
    const show = () => { setResult(null); setError(''); setOpen(true); retry(value => value + 1); };
    window.addEventListener('viht-vpn-diagnostics', show);
    return () => window.removeEventListener('viht-vpn-diagnostics', show);
  }, []);
  useEffect(() => {
    if (!open) return;
    let active = true;
    setResult(null);
    setError('');
    Promise.resolve(window.electronAPI?.checkVpnConnection?.({host:server?.host, port:server?.port})).then(data => {
      if (!active) return;
      if (!data?.network) throw Error('Не удалось запустить проверку. Повторите попытку.');
      setResult(data);
    }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [open, attempt, server?.host, server?.port]);
  if (!open) return null;
  return <AndroidActionSheet title="Проверка подключения" onClose={() => setOpen(false)}>
    <p role="status">{error || (!result ? 'Проверяем сеть, сервер и туннель…' : 'TCP-проверка использует физическую сеть телефона. Проверка туннеля доступна при включённом VPN.')}</p>
    {result && <dl className="viht-diagnostic-results">{Object.entries(steps).map(([key,label])=><div key={key}><dt>{label}</dt><dd className={'result-'+result[key]}>{outcomes[result[key]]}</dd></div>)}</dl>}
    <div className="viht-sheet-actions"><button type="button" onClick={()=>setOpen(false)}>Закрыть</button>{(result || error) && <button type="button" className="viht-sheet-primary" onClick={()=>retry(value=>value+1)}>Повторить</button>}</div>
  </AndroidActionSheet>;
}
