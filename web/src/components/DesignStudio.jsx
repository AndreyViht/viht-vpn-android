import React, {useId, useState} from 'react';
import {Activity, CreditCard, Globe, Settings, ShieldCheck} from 'lucide-react';
import groups from './designOptions';
import './DesignStudio.css';

export default function DesignStudio({design, onChange, lang}) {
  const english = lang === 'en';
  const [open, setOpen] = useState('home');
  const panelId = useId();
  const values = design || {};
  const reset = () => onChange({});
  const update = (key, value) => {
    const next = {...values};
    if (value) next[key] = value;
    else delete next[key];
    onChange(next);
  };
  return <section className="viht-design-studio" aria-labelledby={panelId}>
    <div className="viht-design-heading">
      <h4 id={panelId}>{english ? 'Customize design' : 'Настроить дизайн'}</h4>
      <button type="button" disabled={!Object.keys(values).length} onClick={reset}>
        {english ? 'Reset design' : 'Сбросить оформление'}
      </button>
    </div>
    <div className="viht-design-preview" aria-hidden="true">
      <div className="viht-connect-orb state-connected">
        <span className="viht-orb-halo"/><span className="viht-orb-rim"/>
        <span className="viht-orb-content">
          <ShieldCheck className="viht-orb-icon" size={25}/>
          <span className="viht-orb-label">{english ? 'Connected' : 'Подключено'}</span>
          <span className="viht-orb-timer">08:42</span>
        </span>
      </div>
      <div className="viht-design-preview-detail">
        <div className="viht-server-item is-selected">
          <span className="viht-server-flag"><Globe size={23}/></span>
          <span className="viht-server-info"><strong>{english ? 'Stockholm' : 'Стокгольм'}</strong><small>{english ? 'Connected' : 'Подключено'}</small></span>
          <span className="viht-server-latency"><strong>28 <small>{english ? 'ms' : 'мс'}</small></strong>
            <span className="viht-ping-history">{[5,9,6,11,7].map((height,index) => <i key={index} style={{height}}/>)}</span>
          </span>
        </div>
        <div className="viht-design-preview-actions">
          <span className="viht-server-action"><Activity size={15}/>{english ? 'Ping' : 'Пинг'}</span>
          <span className="viht-home-subscription"><span className="viht-home-renew"><CreditCard size={15}/>{english ? 'Renew' : 'Продлить'}</span></span>
        </div>
        <div className="ios-bottom-bar__island viht-design-preview-nav">
          <span className="ios-tab-item ios-tab-item--active"><ShieldCheck className="ios-tab-item__icon"/><span className="ios-tab-item__label">{english ? 'Home' : 'Главная'}</span></span>
          <span className="ios-tab-item"><Settings className="ios-tab-item__icon"/><span className="ios-tab-item__label">{english ? 'Settings' : 'Настройки'}</span></span>
        </div>
      </div>
    </div>
    {groups.map(([id, russian, translation, options]) => {
      const expanded = open === id;
      const controls = panelId + '-' + id;
      return <div className="viht-design-group" key={id}>
        <button type="button" className="viht-design-group-toggle" aria-expanded={expanded}
          aria-controls={controls} onClick={() => setOpen(expanded ? null : id)}>
          {english ? translation : russian}
        </button>
        <div id={controls} hidden={!expanded}>
          {expanded && options.map(([key, name, translated, ...variants]) =>
            <label className="viht-design-option" key={key}>
              <span>{english ? translated : name}</span>
              <select value={values[key] === 1 || values[key] === 2 ? values[key] : 0}
                onChange={event => update(key, Number(event.target.value))}>
                <option value={0}>{english ? 'Default' : 'По умолчанию'}</option>
                {variants.map(([variant, equivalent], index) =>
                  <option key={variant} value={index + 1}>{english ? equivalent : variant}</option>)}
              </select>
            </label>)}
        </div>
      </div>;
    })}
  </section>;
}
