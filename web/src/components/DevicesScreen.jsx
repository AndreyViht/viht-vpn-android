import './DevicesCompact.css';
import {OwnerInvite} from './SubscriptionInvite';
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Smartphone,
  Laptop,
  Monitor,
  Apple,
  RefreshCw,
  Edit2,
  Trash2,
  Power,
  Check,
  X,
  PlusCircle,
  ExternalLink,
  Shield,
  AlertCircle,
  Loader2,
} from 'lucide-react';
import { fetchActiveDevices, deviceAction } from '../services/api';
import { slotUsage, deviceState, deviceAppLabel } from '../services/devices';
import vihtLogo from '../assets/viht-logo.png';
import { t, fixMojibake } from '../utils/i18n';
import { springs, buttonPress } from '../utils/motion';

function DevicesScreen({ token, hwid, limits: propLimits, lang = 'ru', onBindingChanged }) {
  const [devices, setDevices] = useState([]);
  const [limits, setLimits] = useState(() => propLimits || { total_slots: 0, active_count: 0, slot_price: 0 });
  const [loading, setLoading] = useState(true);
  const [unidentified, setUnidentified] = useState([]);
  const [bindingDevice, setBindingDevice] = useState(null);
  const [bindingTarget, setBindingTarget] = useState('');
  const [bindingBusy, setBindingBusy] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [filter, setFilter] = useState('all'); // 'all' | 'online' | 'offline'

  // Renaming state
  const [editingId, setEditingId] = useState(null);
  const [editingName, setEditingName] = useState('');
  const [renameLoading, setRenameLoading] = useState(false);

  // Toast / notification
  const [toast, setToast] = useState(null);

  const showToast = (message, isError = false) => {
    setToast({ message, isError });
    setTimeout(() => setToast(null), 3500);
  };

  useEffect(()=>{const refresh=()=>void loadDevices(true);window.addEventListener('viht-devices-refresh',refresh);return()=>window.removeEventListener('viht-devices-refresh',refresh)},[token,hwid]);
  const responseEpoch = useRef(0);
  const devicesCountRef = useRef(devices.length);
  devicesCountRef.current = devices.length;

  const loadDevices = useCallback(async (silent = false) => {
    if (!token) return;
    const epoch = ++responseEpoch.current;
    if (!silent && devicesCountRef.current === 0) setLoading(true);
    else setRefreshing(true);

    try {
      const res = await fetchActiveDevices(token, hwid);
      if (epoch !== responseEpoch.current) return;
      if (res && res.ok) {
        const devList = res.devices || [];
        setDevices(devList);
        setUnidentified(res.unidentified_devices || []);

        if (res.limits) setLimits(res.limits);
      } else {
        if (!silent) showToast(res?.error || 'Не удалось загрузить устройства', true);
      }
    } catch (err) {
      console.error('Failed to load devices:', err);
      if (epoch === responseEpoch.current && !silent) showToast('Ошибка сети при загрузке устройств', true);
    } finally {
      if (epoch === responseEpoch.current) { setLoading(false); setRefreshing(false); }
    }
  }, [token, hwid]);

  useEffect(() => {
    let disposed = false;
    let inFlight = false;
    let timer;
    const poll = async (initial = false) => {
      if (disposed || inFlight) return;
      inFlight = true;
      try { if (document.visibilityState !== 'hidden') await loadDevices(!initial); }
      finally {
        inFlight = false;
        if (!disposed) { clearTimeout(timer); timer = setTimeout(() => poll(), 5000); }
      }
    };
    poll(true);
    const resume = () => { if (document.visibilityState === 'visible') { clearTimeout(timer); poll(); } };
    document.addEventListener('visibilitychange', resume);
    return () => { disposed = true; responseEpoch.current += 1; clearTimeout(timer); document.removeEventListener('visibilitychange', resume); };
  }, [loadDevices]);

  const linkToPhone = async () => {
    if (!bindingDevice || !bindingTarget) return;
    setBindingBusy(true);
    try {
      const result=await deviceAction(token,{command:'link',device_id:bindingDevice.device_id||bindingDevice.id,target_device_id:bindingTarget});
      if (!result?.ok) throw new Error(result?.error||'Не удалось привязать приложения');
      setBindingDevice(null); await loadDevices(true); showToast('Приложения привязаны к одному телефону. Занят один слот.');
    } catch(error) { showToast(error.message,true); }
    finally { setBindingBusy(false); }
  };
  const bindCurrentDevice = async device => {
    if (!window.confirm(`Подтвердите: сейчас Viht открыт на устройстве «${device.custom_name||device.device_model}». Его VPN-приложения должны занимать один слот.`)) return;
    const result=await deviceAction(token,{command:'bind-current',device_id:device.binding_device_id||device.device_id||device.id});
    if (!result?.ok) { showToast(result?.error||'Не удалось привязать Viht',true); return; }
    await loadDevices(true); onBindingChanged?.(); showToast('Viht привязан к этому устройству. Дополнительный слот не занят.');
  };
  const createBindingLink = async device => {
    try {
      const result=await deviceAction(token,{command:'pair',device_id:device.binding_device_id||device.device_id||device.id});
      if (!result?.ok) throw new Error(result?.error||'Не удалось создать ссылку');
      if (window.electronAPI?.copyText) { const copied=await window.electronAPI.copyText(result.binding_url); if (!copied?.ok) throw new Error(copied?.error||'Не удалось скопировать ссылку'); }
      else await navigator.clipboard.writeText(result.binding_url);
      showToast('Ссылка скопирована. Импортируйте её в другое VPN-приложение на этом телефоне в течение 10 минут.');
    } catch(error) { showToast(error.message,true); }
  };
  const separateClient = async client => {
    if (!window.confirm('Считать этот клиент отдельным телефоном? Потребуется отдельный свободный слот.')) return;
    const result=await deviceAction(token,{command:'unlink',device_id:client.device_id||client.id});
    if (!result?.ok) showToast(result?.error||'Не удалось отделить клиент',true);
    await loadDevices(true);
  };

  // Rename handler
  const handleStartRename = (device) => {
    setEditingId(device.physical_device_id || device.id || device.device_id);
    setEditingName(device.custom_name || device.device_model || device.name || '');
  };

  const handleSaveRename = async (device) => {
    if (!token || !device) return;
    const trimmed = editingName.trim();
    setRenameLoading(true);

    // Optimistic UI update
    setDevices((prev) =>
      prev.map((d) =>
        (d.id === device.id || (d.hwid && d.hwid === device.hwid))
          ? { ...d, custom_name: trimmed || null }
          : d
      )
    );

    try {
      const res = await deviceAction(token, {
        command: 'rename',
        device_id: device.device_id || device.id,
        hwid: device.hwid || null,
        device_model: device.raw_model || device.device_model || null,
        custom_name: trimmed,
      });

      if (res && res.ok) {
        showToast(trimmed ? `Имя устройства обновлено: «${trimmed}»` : 'Название сброшено');
        setEditingId(null);
      } else {
        showToast(res?.error || 'Не удалось сохранить имя', true);
        loadDevices(true);
      }
    } catch (e) {
      showToast('Ошибка сети при сохранении имени', true);
      loadDevices(true);
    } finally {
      setRenameLoading(false);
    }
  };

  // Toggle enable/disable
  const handleToggleDevice = async (device) => {
    if (!token || !device) return;
    const newDisabled = !device.disabled;
    const command = newDisabled ? 'disable' : 'enable';

    // Optimistic UI update
    setDevices((prev) =>
      prev.map((d) =>
        (d.id === device.id || (d.hwid && d.hwid === device.hwid))
          ? { ...d, disabled: newDisabled, online: false, online_known: newDisabled }
          : d
      )
    );

    try {
      const res = await deviceAction(token, {
        command,
        device_id: device.device_id || device.id,
        hwid: device.hwid || null,
      });

      if (res && res.ok) {
        showToast(newDisabled ? 'Устройство временно отключено' : 'Устройство снова активно');
        loadDevices(true);
      } else {
        showToast(res?.error || 'Не удалось изменить состояние', true);
        loadDevices(true);
      }
    } catch (e) {
      showToast('Ошибка сети', true);
      loadDevices(true);
    }
  };

  // Delete / revoke device with shrink animation
  const handleDeleteDevice = async (device) => {
    const devName = device.custom_name || device.device_model || 'устройство';
    if (!window.confirm(`Отвязать «${devName}» от подписки${device.client_count > 1 ? ` вместе со всеми ${device.client_count} VPN-приложениями` : ''}?`)) {
      return;
    }

    // Optimistic UI update (triggers Framer Motion exit animation)
    setDevices((prev) => prev.filter((d) => !(d.id === device.id || (d.hwid && d.hwid === device.hwid))));

    try {
      const res = await deviceAction(token, {
        command: 'delete',
        device_id: device.device_id || device.id,
        hwid: device.hwid || null,
      });

      if (res && res.ok) {
        showToast(`Устройство «${devName}» отвязано`);
        loadDevices(true);
      } else {
        showToast(res?.error || 'Не удалось удалить устройство', true);
        loadDevices(true);
      }
    } catch (e) {
      showToast('Ошибка сети', true);
      loadDevices(true);
    }
  };

  const getDeviceIcon = (platform, model = '') => {
    const p = (platform || '').toLowerCase();
    const m = (model || '').toLowerCase();
    if (p === 'windows' || m.includes('pc') || m.includes('windows')) return <Monitor size={18} />;
    if (p === 'ios' || p === 'macos' || m.includes('iphone') || m.includes('mac')) return <Apple size={18} />;
    if (p === 'android' || m.includes('android')) return <Smartphone size={18} />;
    return <Laptop size={18} />;
  };

  const filteredDevices = devices.filter((d) => {
    if (filter === 'online') return deviceState(d) === 'online';
    if (filter === 'offline') return deviceState(d) === 'offline';
    if (filter === 'unknown') return deviceState(d) === 'unknown';
    return true;
  });

  const isCurrentDevice = (d) => {
    if (d.is_current) return true;
    const curHwid = hwid || (typeof localStorage !== 'undefined' ? localStorage.getItem('viht_client_hwid') : null);
    if (curHwid && d.hwid && String(d.hwid).trim().toLowerCase() === String(curHwid).trim().toLowerCase()) {
      return true;
    }
    return false;
  };

  const getAppChipClass = (appName) => {
    const a = String(appName || '').toLowerCase();
    if (a.includes('viht')) return 'fluent-chip--viht';
    if (a.includes('happ')) return 'fluent-chip--happ';
    if (a.includes('incy')) return 'fluent-chip--incy';
    if (a.includes('v2ray')) return 'fluent-chip--v2ray';
    return 'fluent-chip--app';
  };

  const getAppChipLabel = deviceAppLabel;
  const usage = slotUsage(devices, limits);

  const openAddSlot = () => window.dispatchEvent(new CustomEvent('viht-open-tariffs', { detail: { product: 'slots' } }));

  return (
    <div className="fluent-page fluent-devices-page devices-compact">
      {/* Toast alert */}
      <AnimatePresence>
        {toast && (
          <motion.div
            className={`fluent-toast ${toast.isError ? 'fluent-toast--error' : 'fluent-toast--success'}`}
            initial={{ opacity: 0, y: -12, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.95 }}
            transition={springs.cushioned}
          >
            <span>{toast.message}</span>
          </motion.div>
        )}
      </AnimatePresence>

      <section className="compact-device-summary" aria-label="Слоты подписки">
        <div className="compact-device-heading"><span>Слоты <strong>{usage.used}<small> / {usage.total ?? '—'}</small></strong></span><button type="button" className="compact-refresh" onClick={()=>loadDevices(true)} disabled={refreshing} aria-label="Обновить устройства" title="Обновить"><RefreshCw size={17} className={refreshing?'spinning':''}/></button></div>
        <div className="compact-device-commands"><OwnerInvite compact onAdded={()=>loadDevices(true)}/><button type="button" className="compact-slot-buy" onClick={openAddSlot}><PlusCircle size={14}/><span>Слот · {limits.slot_price||300} ₽</span></button></div>
        <div className="fluent-slot-progress-bar"><motion.div className="fluent-slot-progress-fill" initial={false} animate={{width:usage.percent+'%'}} transition={springs.natural}/></div>
        <details className="compact-slot-note"><summary>Учитываются только приложения Viht</summary><p>Happ, Incy и V2RayTun временно не занимают слоты. Их прежний доступ сохранён.</p></details>
      </section>

      {unidentified.length > 0 && <div className="fluent-card" style={{padding:12,fontSize:12,color:'var(--text-secondary)'}}>Старые клиенты без идентификатора: {unidentified.length}. Слоты не занимают; определить физическое устройство для них нельзя.</div>}
      {bindingDevice && <div style={{position:'fixed',inset:0,zIndex:2000,display:'flex',alignItems:'center',justifyContent:'center',padding:16,background:'rgba(0,0,0,.6)'}} onClick={()=>!bindingBusy&&setBindingDevice(null)}><div className="fluent-card" role="dialog" aria-modal="true" aria-label="Привязка к устройству" style={{padding:18,display:'grid',gap:14,width:'100%',maxWidth:480}} onClick={event=>event.stopPropagation()}>
        <strong>Это то же физическое устройство</strong>
        <span style={{fontSize:13}}>Подтвердите, что приложения «{bindingDevice.custom_name||bindingDevice.device_model}» используются на выбранном ниже устройстве. Они займут один слот.</span>
        <select aria-label="Устройство" value={bindingTarget} onChange={event=>setBindingTarget(event.target.value)} style={{padding:12,borderRadius:12,background:'#101b29',color:'white'}}>
          <option value="">Выберите устройство</option>
          {devices.filter(d=>(d.physical_device_id||d.id)!==(bindingDevice.physical_device_id||bindingDevice.id)&&d.hwid).map(d=><option key={d.id} value={d.device_id||d.id}>{d.is_current?'Это устройство · ':''}{d.custom_name||d.device_model} · {d.app_name} · {d.hwid_short}</option>)}
        </select>
        <div style={{display:'flex',gap:10}}><button className="btn btn--primary" disabled={!bindingTarget||bindingBusy} onClick={linkToPhone}>{bindingBusy?'Привязка…':'Подтвердить'}</button><button className="btn btn--secondary" disabled={bindingBusy} onClick={()=>setBindingDevice(null)}>Отмена</button></div>
      </div></div>}

      {/* Filter Tabs with Sliding Active Indicator */}
      <div className="fluent-devices-toolbar">
        <div className="fluent-filter-pills">
          {[
            { id: 'all', label: `${lang==='en'?'All':'Все'} ${devices.length}` },
            { id: 'online', label: `${lang==='en'?'Online':'В сети'} ${devices.filter(d => deviceState(d) === 'online').length}` },
            { id: 'offline', label: `${lang==='en'?'Offline':'Не в сети'} ${devices.filter(d => deviceState(d) === 'offline').length}` },
            { id: 'unknown', label: `${lang === 'en' ? 'Unknown' : 'Неизвестно'} ${devices.filter(d => deviceState(d) === 'unknown').length}` },
          ].map((pill) => {
            const isActive = filter === pill.id;
            return (
              <motion.button data-motion-press="true"
                key={pill.id}
                type="button"
                className={`fluent-pill ${isActive ? 'active' : ''}`}
                onClick={() => setFilter(pill.id)}
                aria-pressed={isActive}
                whileTap={{ scale: 0.94 }}
                transition={springs.tactile}
              >
                {isActive && (
                  <motion.div
                    className="fluent-pill-active-bg"
                    layoutId="device-filter-pill"
                    transition={springs.pill}
                  />
                )}
                <span className="fluent-pill-label">{pill.label}</span>
              </motion.button>
            );
          })}
        </div>
      </div>

      {/* Device List with Smooth Exit & Entry */}
      <div className="fluent-devices-list">
        {loading ? (
          <div className="fluent-loading-box">
            <Loader2 size={32} className="spinning fluent-spinner-glow" />
            <span>{t('devices_loading', lang)}</span>
          </div>
        ) : filteredDevices.length === 0 ? (
          <div className="fluent-empty-box">
            <Smartphone size={36} className="fluent-empty-icon" />
            <h3>{t('devices_empty_title', lang)}</h3>
            <p>{t('devices_empty_desc', lang)}</p>
          </div>
        ) : (
          <AnimatePresence initial={false}>
            {filteredDevices.map((device) => {
              const isEditing = editingId === (device.physical_device_id || device.id || device.device_id);
              const isCurrent = isCurrentDevice(device);
              const isOnline = device.online && !device.disabled;
              const rawName = device.custom_name || device.device_model || device.name || t('device_fallback_name', lang);
              const displayName = fixMojibake(rawName);
              const isVihtApp = isCurrent || (device.app_name && device.app_name.toLowerCase().includes('viht'));

              return (
                <motion.div
                  key={device.physical_device_id || device.id || device.device_id || device.hwid}
                  layout="position"
                  className={`fluent-device-row ${device.disabled ? 'fluent-device-row--disabled' : ''}`}
                  initial={{ opacity: 0, y: 10, scale: 0.98 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{
                    opacity: 0,
                    scale: 0.92,
                    height: 0,
                    marginBottom: 0,
                    paddingTop: 0,
                    paddingBottom: 0,
                    overflow: 'hidden',
                    transition: { duration: 0.22, ease: [0.32, 0, 0.67, 0] },
                  }}
                  whileHover={{ y: -1, scale: 1.002 }}
                  transition={springs.natural}
                >
                  <div className={`fluent-device-avatar ${isVihtApp ? 'fluent-device-avatar--viht' : ''}`}>
                    {isVihtApp ? (
                      <img src={vihtLogo} alt="Viht" className="fluent-device-avatar-img" />
                    ) : String(device.app_name || '').toLowerCase().includes('incy') ? (
                      <Apple size={18} color="#6ee7b7" />
                    ) : (
                      getDeviceIcon(device.platform, device.device_model)
                    )}
                  </div>

                  <div className="fluent-device-info">
                    <div className="fluent-device-name-row">
                      <AnimatePresence mode="wait">
                        {isEditing ? (
                          <motion.div
                            key="editing-input"
                            className="fluent-inline-edit"
                            initial={{ opacity: 0, scale: 0.96 }}
                            animate={{ opacity: 1, scale: 1 }}
                            exit={{ opacity: 0, scale: 0.96 }}
                            transition={springs.tactile}
                          >
                            <input
                              type="text"
                              className="fluent-inline-input"
                              value={editingName}
                              onChange={(e) => setEditingName(e.target.value)}
                              placeholder={t('device_placeholder_name', lang)}
                              autoFocus
                              maxLength={32}
                              onKeyDown={(e) => {
                                if (e.key === 'Enter') handleSaveRename(device);
                                if (e.key === 'Escape') setEditingId(null);
                              }}
                            />
                            <motion.button data-motion-press="true"
                              type="button"
                              className="fluent-btn-icon fluent-btn-icon--save"
                              onClick={() => handleSaveRename(device)}
                              disabled={renameLoading}
                              whileTap={{ scale: 0.9 }}
                              transition={springs.tactile}
                            >
                              <Check size={14} />
                            </motion.button>
                            <motion.button data-motion-press="true"
                              type="button"
                              className="fluent-btn-icon fluent-btn-icon--cancel"
                              onClick={() => setEditingId(null)}
                              whileTap={{ scale: 0.9 }}
                              transition={springs.tactile}
                            >
                              <X size={14} />
                            </motion.button>
                          </motion.div>
                        ) : (
                          <motion.div
                            key="static-name"
                            className="fluent-device-name-wrap"
                            initial={{ opacity: 0 }}
                            animate={{ opacity: 1 }}
                            exit={{ opacity: 0 }}
                          >
                            <strong className="fluent-device-name">{displayName}</strong>
                            <motion.button data-motion-press="true"
                              type="button"
                              className="fluent-btn-icon fluent-btn-icon--edit"
                              onClick={() => handleStartRename(device)}
                              title={t('device_title_rename', lang)}
                              whileTap={{ scale: 0.9 }}
                              transition={springs.tactile}
                            >
                              <Edit2 size={13} />
                            </motion.button>
                          </motion.div>
                        )}
                      </AnimatePresence>



                      {device.disabled && (
                        <span className="fluent-chip fluent-chip--mute">⏸️ Отключено</span>
                      )}

                      <span className={`fluent-chip ${getAppChipClass(device.app_name)}`}>
                        {getAppChipLabel(device)}
                      </span>

                      {(device.hwid_short || device.hwid) && (
                        <span
                          className="fluent-chip fluent-chip--hwid"
                          title={`Аппаратный HWID: ${device.hwid || device.hwid_short}`}
                        >
                          ID: {device.hwid_short || (device.hwid && device.hwid.length > 10 ? `${device.hwid.slice(0, 4)}...${device.hwid.slice(-4)}` : device.hwid)}
                        </span>
                      )}
                    </div>

                    <div className="fluent-device-meta">
                      {isCurrent&&<span className="compact-current-device">Это устройство</span>}
                      <span className="fluent-device-model">
                        {device.custom_name && device.device_model ? `${device.device_model}` : (device.device_model || device.raw_model || 'Устройство')}
                      </span>
                      <span className="fluent-meta-bullet">•</span>
                      <span className="fluent-device-status">
                        {device.disabled ? (
                          <span className="status-text status-text--disabled">{t('device_status_disabled', lang)}</span>
                        ) : isOnline ? (
                          <span className="status-text status-text--online">
                            <span className="ping-status-dot green" /> {t('device_status_online', lang)}
                          </span>
                        ) : (
                          <span className="status-text status-text--offline">
                            <span className="ping-status-dot gray" /> {deviceState(device) === 'unknown' ? (device.last_active || 'Нет данных о подключении') : (device.last_active || t('devices_offline', lang))}
                          </span>
                        )}
                      </span>
                    </div>

                  </div>

                  <div className="fluent-device-actions">
                    <motion.button data-motion-press="true"
                      type="button"
                      className={`btn btn--icon btn--sm ${device.disabled ? 'btn--activate' : 'btn--pause'}`}
                      onClick={() => handleToggleDevice(device)}
                      title={device.disabled ? t('device_btn_enable', lang) : t('device_btn_disable', lang)}
                      whileTap={{ scale: 0.9 }}
                      transition={springs.tactile}
                    >
                      <Power size={14} />
                    </motion.button>

                    <motion.button data-motion-press="true"
                      type="button"
                      className="btn btn--icon btn--sm btn--delete"
                      onClick={() => handleDeleteDevice(device)}
                      title={t('device_btn_delete', lang)}
                      whileTap={{ scale: 0.9 }}
                      transition={springs.tactile}
                    >
                      <Trash2 size={14} />
                    </motion.button>
                  </div>
                    <details className="compact-device-details">
                      <summary>Подробнее{device.client_count>1?' · '+device.client_count+' приложения':''}</summary>
                      <div className="compact-device-detail-body"><strong>{displayName}</strong>
                        {(device.clients || [device]).map(client=><div key={client.device_id||client.id} style={{display:'grid',gap:5}}>
                          <span>{deviceAppLabel(client)} · ID: {client.hwid_short||'нет ID'} · {deviceState(client)==='online'?'В сети':client.last_active||'Нет данных'}</span>
                          {device.client_count>1 && <button className="btn btn--secondary btn--sm" onClick={()=>separateClient(client)}>Это отдельное устройство</button>}
                        </div>)}
                        {!device.is_current && !device.disabled && device.hwid && <button className="btn btn--secondary btn--sm" onClick={()=>bindCurrentDevice(device)}>Использую Viht на этом устройстве</button>}
                        {!device.disabled && device.hwid && <button className="btn btn--secondary btn--sm" onClick={()=>createBindingLink(device)}>Добавить VPN-приложение на это устройство</button>}
                        {device.hwid && devices.length>1 && <button className="btn btn--secondary btn--sm" onClick={()=>{setBindingTarget(devices.find(d=>d.is_current&&d.id!==device.id)?.device_id||'');setBindingDevice(device);}}>Это другое приложение того же устройства</button>}
                      </div>
                    </details>
                </motion.div>
              );
            })}
          </AnimatePresence>
        )}
      </div>
    </div>
  );
}

export default React.memo(DevicesScreen);

