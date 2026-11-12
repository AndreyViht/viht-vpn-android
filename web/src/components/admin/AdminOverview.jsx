import {useCallback,useEffect,useRef,useState} from 'react';
import {Activity,ArrowDownUp,RefreshCw,Users,Wallet,Server} from 'lucide-react';
import {useAdminFetch} from './AdminFetchContext.jsx';
import './AdminOverview.css';

const fmt=v=>v==null?'—':Number(v).toLocaleString('ru-RU',{maximumFractionDigits:1});
function Trend({values=[],times=[],label,color='#a8eed7',unit=''}) {
  const [selection,setSelection]=useState(null);
  const points=values.flatMap((value,index)=>value==null?[]:[{value,index,time:times[index]}]);
  if(!points.length)return <div className="overview-chart-empty">Данные появятся после измерений</div>;

  const max=Math.max(1,...points.map(point=>point.value));
  const x=index=>12+index*576/Math.max(1,values.length-1);
  const y=value=>136-value/max*116;
  const path=points.map(({index,value})=>(index>0&&values[index-1]!=null?'L':'M')+x(index)+','+y(value)).join(' ');
  const selected=points.find(point=>point.time===selection?.time&&point.index===selection?.index)
    ?? (selection?.time ? points.find(point=>point.time===selection.time) : null);

  function selectAt(event) {
    const bounds=event.currentTarget.getBoundingClientRect();
    if(!bounds.width)return;
    const target=12+576*(event.clientX-bounds.left)/bounds.width;
    setSelection(points.reduce((nearest,point)=>Math.abs(x(point.index)-target)<Math.abs(x(nearest.index)-target)?point:nearest));
  }

  function onKeyDown(event) {
    if(event.key==='Escape'){setSelection(null);return;}
    if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;
    event.preventDefault();
    const index=points.indexOf(selected);
    const next=event.key==='Home'?0:event.key==='End'?points.length-1:
      index<0?0:Math.max(0,Math.min(points.length-1,index+(event.key==='ArrowLeft'?-1:1)));
    setSelection(points[next]);
  }

  const date=selected?.time;
  const dateLabel=date?.length===10
    ?new Date(date+'T00:00:00Z').toLocaleDateString('ru-RU',{day:'numeric',month:'long',timeZone:'UTC'})
    :date?new Date(date).toLocaleTimeString('ru-RU',{timeZone:'Europe/Moscow'})+' МСК':'';
  const valueLabel=selected?[fmt(selected.value),unit].filter(Boolean).join(' '):'';

  return <div className="overview-trend" tabIndex={0} aria-label={label+'. Для выбора точки используйте стрелки.'}
    onKeyDown={onKeyDown} onBlur={()=>setSelection(null)}>
    <svg className="overview-chart" viewBox="0 0 600 155" aria-hidden="true">
      {[20,78,136].map(level=><path key={level} d={`M12 ${level}H588`} stroke="currentColor" opacity=".08"/>)}
      <path d={path} fill="none" stroke={color} strokeWidth="2.7" strokeLinecap="round" strokeLinejoin="round"/>
      {points.map(point=><circle key={point.index} cx={x(point.index)} cy={y(point.value)} r="3" fill={color}/>)}
      {selected&&<g pointerEvents="none">
        <path d={`M${x(selected.index)} 20V136`} stroke={color} strokeDasharray="4 4" opacity=".65"/>
        <circle cx={x(selected.index)} cy={y(selected.value)} r="6" fill="#111c29" stroke={color} strokeWidth="2.5"/>
      </g>}
      <rect x="12" y="0" width="576" height="155" fill="transparent" className="overview-trend-hit"
        onPointerMove={event=>{if(event.pointerType==='mouse')selectAt(event);}}
        onPointerLeave={event=>{if(event.pointerType==='mouse')setSelection(null);}}
        onClick={selectAt}/>
    </svg>
    <div className="overview-trend-tooltip" role="status" style={{'--trend-color':color}} hidden={!selected}>
      <span>{dateLabel}</span><strong>{valueLabel}</strong>
    </div>
  </div>;
}
function Metric({title,value,hint,icon:Icon}){return <div className="overview-metric admin-panel-card"><span><Icon size={17}/>{title}</span><strong>{value}</strong><small>{hint}</small></div>;}

export default function AdminOverview(){
 const api=useAdminFetch(),busy=useRef(false);
 const [live,setLive]=useState(null),[cohorts,setCohorts]=useState(null),[samples,setSamples]=useState([]),[liveError,setLiveError]=useState(''),[historyError,setHistoryError]=useState('');
 const loadLive=useCallback(async()=>{
  if(busy.current)return;
  busy.current=true;
  try{
   const data=await api('overview_live');
   if(!data?.ok)throw new Error(data?.error||'нет связи');
   setLive(data);
   const rates=(data.servers||[]).map(server=>server.rate).filter(rate=>rate!=null);
   setSamples(old=>[...old.slice(-119),{
    time:data.updated_at||new Date().toISOString(),
    rate:rates.length?rates.reduce((sum,rate)=>sum+rate,0):null,
    online:data.presence_available?data.clients.online_clients:null,
    devices:data.presence_available?data.clients.online_devices:null,
   }]);
   setLiveError([!data.presence_available&&'Нет данных об активности VPN',!data.servers&&'Нет данных о серверах'].filter(Boolean).join(' · '));
  }catch(error){setLiveError('Живые данные: '+error.message);}
  finally{busy.current=false;}
 },[api]);
 const loadHistory=useCallback(async()=>{
  try{
   const data=await api('overview_timeseries');
   if(!data?.ok)throw new Error(data?.error||'нет связи');
   setCohorts(data);setHistoryError('');
  }catch(error){setHistoryError('История: '+error.message);}
 },[api]);
 const load=useCallback(()=>{void loadLive();void loadHistory();},[loadLive,loadHistory]);
 useEffect(()=>{
  load();
  const liveTimer=setInterval(()=>{if(!document.hidden)void loadLive()},3000);
  const historyTimer=setInterval(()=>{if(!document.hidden)void loadHistory()},30000);
  return()=>{clearInterval(liveTimer);clearInterval(historyTimer);};
 },[load,loadLive,loadHistory]);
 const servers=live?.servers||[],users=live?.clients,daily=cohorts?.daily||[];
 const loads=servers.map(s=>s.load).filter(value=>value!=null);
 const meanLoad=loads.length?loads.reduce((sum,value)=>sum+value,0)/loads.length:null;
 const traffic=live?.traffic_bytes==null?null:live.traffic_bytes/1073741824;
 const totalRate=samples.at(-1)?.rate,online=live?.presence_available?users?.online_clients:null;
 const devices=live?.presence_available?users?.online_devices:null;
 const warnings=[liveError,historyError].filter(Boolean);
 const revenue=cohorts?daily.reduce((sum,day)=>sum+Number(day.revenue||0),0):null;
 return <div className="admin-page admin-overview">
  <header className="overview-heading"><div><span className="admin-eyebrow">ОБЗОР СЕРВИСА</span><h1>Дашборд</h1><p>{live?.updated_at?'Последний ответ '+new Date(live.updated_at).toLocaleTimeString('ru-RU')+' · обновление каждые 3 с':'Получаем данные сервиса'}</p></div><button className="admin-action" onClick={load} aria-label="Обновить дашборд"><RefreshCw size={17}/><span>Обновить</span></button></header>
  {!!warnings.length&&<div className="admin-feedback" role="status">{warnings.join(' · ')}</div>}
  <div className="overview-metrics">
   <Metric icon={Users} title="Всего аккаунтов Viht" value={fmt(users?.total)} hint="Все записи аккаунтов в базе Viht"/>
   <Metric icon={Users} title="С действующей подпиской" value={fmt(users?.active)} hint="Уникальные Viht ID, включая пробные"/>
   <Metric icon={Wallet} title="С непробной подпиской" value={fmt(users?.paid)} hint="Уникальные Viht ID с действующим доступом"/>
   <Metric icon={Wallet} title="Оплатили сами" value={fmt(users?.self_paid)} hint="Начисление с самым поздним действующим сроком"/>
   <Metric icon={Wallet} title="Доступ выдал администратор" value={fmt(users?.admin_paid)} hint="Ручное начисление с самым поздним действующим сроком"/>
   <Metric icon={Activity} title="Клиенты в сети" value={online==null?'—':fmt(online)+' из '+fmt(users?.active)} hint={live?'Уникальные Viht ID с активностью за '+live.window_seconds+' с':'Ожидаем данные VPN'}/>
   <Metric icon={Activity} title="Устройства с известной активностью" value={fmt(devices)} hint={devices==null?'Ожидаем данные VPN':fmt(users.online_device_clients)+' клиентов · '+fmt(users.online_shared_keys)+' активных общих ключей без разбивки по устройствам'}/>
   <Metric icon={Wallet} title="Источник оплаты неизвестен" value={users?fmt(users.paid-users.self_paid-users.admin_paid):"—"} hint="Действующие непробные подписки без подходящей записи в журнале"/>
   <Metric icon={Users} title="Новые за 30 дней" value={fmt(cohorts?.new_clients_30d)} hint="По дате регистрации"/>
   <Metric icon={Users} title="Постоянные" value={fmt(cohorts?.recurring_clients)} hint="Две и более оплаты"/>
   <Metric icon={Wallet} title="Поступления за 30 дней" value={revenue==null?'—':fmt(revenue)+' ₽'} hint="По журналу платежей"/>
  </div>
  <div className="overview-metrics overview-metrics--network">
   <Metric icon={Activity} title="Нагрузка на ядро" value={fmt(meanLoad)} hint={'Load average по '+loads.length+' нодам'}/>
   <Metric icon={ArrowDownUp} title="Сеть серверов" value={totalRate==null?'—':fmt(totalRate)+' Мбит/с'} hint="Входящий и исходящий трафик интерфейсов"/>
   <Metric icon={Server} title="Учтённый трафик" value={traffic==null?'—':fmt(traffic)+' ГиБ'} hint="Счётчики VPN-ключей без служебного транзита"/>
  </div>
  <div className="overview-chart-grid"><section className="admin-panel-card overview-chart-card"><header><h2>Новые клиенты</h2><span>30 дней</span></header><Trend times={daily.map(day=>day.date)} values={daily.map(d=>d.new_clients)} label="Регистрации клиентов за 30 дней"/><footer>{daily[0]?.date} <span>{daily.at(-1)?.date}</span></footer></section><section className="admin-panel-card overview-chart-card"><header><h2>Поступления</h2><span>₽ · 30 дней</span></header><Trend times={daily.map(day=>day.date)} values={daily.map(d=>d.revenue)} label="Поступления по дням" color="#c7b1f4" unit="₽"/><footer>{daily[0]?.date}<span>{daily.at(-1)?.date}</span></footer></section></div>
  <section className="admin-panel-card overview-chart-card"><header><h2>Нагрузка сети</h2><span>Живые измерения · Мбит/с</span></header><Trend times={samples.map(sample=>sample.time)} values={samples.map(sample=>sample.rate)} label="Суммарная скорость сети серверов" color="#9fc9f1" unit="Мбит/с"/><p className="admin-fine">До 120 последних ответов мониторинга после открытия страницы.</p></section>
  <section className="admin-panel-card overview-chart-card"><header><h2>Клиенты в сети</h2><span>Живая активность · Viht ID</span></header><Trend times={samples.map(sample=>sample.time)} values={samples.map(sample=>sample.online)} label="Клиенты с недавней активностью"/><header><h2>Устройства с известной активностью</h2></header><Trend times={samples.map(sample=>sample.time)} values={samples.map(sample=>sample.devices)} label="Устройства с недавней активностью" color="#c7b1f4"/></section>
  <section className="admin-panel-card overview-servers"><header><h2>Серверы</h2></header>{servers.length?servers.map(server=><div className="overview-server" key={server.name}><span className={'overview-dot '+(server.ok?'is-ok':'')}/><strong>{server.name}</strong><span>Нагрузка {fmt(server.load)}</span><span>RAM {server.ram==null?'—':fmt(server.ram)+'%'}</span><span>{server.rate==null?'—':fmt(server.rate)+' Мбит/с'}</span></div>):<p className="admin-fine">Источники мониторинга не настроены или ещё не ответили.</p>}</section>
 </div>;
}
