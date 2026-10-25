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
import vihtLogo from '../assets/viht-logo.png';
import { t, fixMojibake } from '../utils/i18n';
import { springs, buttonPress } from '../utils/motion';

function DevicesScreen({ token, hwid, limits: propLimits, lang = 'ru' }) {
  const [devices, setDevices] = useState([]);
  const [limits, setLimits] = useState(() => propLimits || { total_slots: 0, active_count: 0, slot_price: 0 });
  const [loading, setLoading] = useState(true);
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

  const devicesCountRef = useRef(devices.length);
  devicesCountRef.current = devices.length;

  const loadDevices = useCallback(async (silent = false) => {
    if (!token) return;
    if (!silent && devicesCountRef.current === 0) setLoading(true);
    else setRefreshing(true);

    try {
      const res = await fetchActiveDevices(token, hwid);
      if (res && res.ok) {
        const devList = res.devices || [];
        setDevices(devList);

        if (res.limits) setLimits(res.limits);
      } else {
        if (!silent) showToast(res?.error || 'Не удалось загрузить устройства', true);
      }
    } catch (err) {
      console.error('Failed to load devices:', err);
      if (!silent) showToast('Ошибка сети при загрузке устройств', true);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [token, hwid]);

  useEffect(() => {
    loadDevices();
    const interval = setInterval(() => loadDevices(true), 15000);
    return () => clearInterval(interval);
  }, [loadDevices]);

  // Rename handler
  const handleStartRename = (device) => {
    setEditingId(device.id || device.device_id);
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
          ? { ...d, disabled: newDisabled, online: !newDisabled }
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
    if (!window.confirm(`Вы уверены, что хотите отвязать «${devName}» от подписки?`)) {
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
    if (filter === 'online') return d.online && !d.disabled;
    if (filter === 'offline') return !d.online || d.disabled;
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

  const getAppChipLabel = (device) => {
    const a = String(device.app_name || '').toLowerCase();
    const ver = device.app_version ? ` v${device.app_version}` : '';
    if (a.includes('viht')) return `💠 Viht Android${ver}`;
    if (a.includes('happ')) return `🟣 Happ${ver}`;
    if (a.includes('incy')) return `🍏 Incy${ver}`;
    if (a.includes('v2rayng')) return `🚀 v2rayNG${ver}`;
    if (a.includes('v2raytun')) return `⚡ V2RayTun${ver}`;
    return device.app_name ? `${device.app_name}${ver}` : 'VPN Клиент';
  };

  const openAddSlot = () => {
    const url = 'https://anviht.ru/lk?modal=slot';
    if (window.electronAPI?.openExternal) window.electronAPI.openExternal(url);
    else window.open(url, '_blank');
  };

  return (
    <div className="fluent-page fluent-devices-page">
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

      {/* Page Header */}
      <div className="fluent-page-header">
        <motion.button
          type="button"
          className="btn btn--secondary btn--sm"
          onClick={() => loadDevices(true)}
          disabled={refreshing}
          title={t('device_title_refresh', lang)}
          {...buttonPress}
        >
          <RefreshCw size={14} className={refreshing ? 'spinning' : ''} />
          <span>{refreshing ? t('devices_refreshing', lang) : t('devices_refresh', lang)}</span>
        </motion.button>
      </div>

      {/* Slot Limit Card */}
      <div className="fluent-card fluent-slot-card">
        <div className="fluent-slot-card__top">
          <div className="fluent-slot-card__label-box">
            <span className="fluent-slot-title">{t('devices_slots_used', lang)}</span>
            <strong className="fluent-slot-counter">
              {devices.length} {t('devices_slots_of', lang)} {limits.total_slots || 16}
            </strong>
          </div>

          <motion.button
            type="button"
            className="btn btn--secondary btn--sm fluent-btn-add-slot"
            onClick={openAddSlot}
            title={t('device_slot_manage_tooltip', lang)}
            {...buttonPress}
          >
            <PlusCircle size={14} />
            <span>{t('devices_add_slot', lang)} (+{limits.slot_price || 300} ₽)</span>
            <ExternalLink size={12} />
          </motion.button>
        </div>

        {/* Smooth animated progress bar */}
        <div className="fluent-slot-progress-bar">
          <motion.div
            className="fluent-slot-progress-fill"
            initial={{ width: 0 }}
            animate={{
              width: `${Math.min(100, Math.round((devices.length / (limits.total_slots || 4)) * 100))}%`,
            }}
            transition={springs.natural}
          />
        </div>
      </div>

      {/* Filter Tabs with Sliding Active Indicator */}
      <div className="fluent-devices-toolbar">
        <div className="fluent-filter-pills">
          {[
            { id: 'all', label: `${t('devices_all', lang)} (${devices.length})` },
            { id: 'online', label: `${t('devices_online', lang)} (${devices.filter(d => d.online && !d.disabled).length})` },
            { id: 'offline', label: `${t('devices_offline', lang)} (${devices.filter(d => !d.online || d.disabled).length})` },
          ].map((pill) => {
            const isActive = filter === pill.id;
            return (
              <motion.button
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
              const isEditing = editingId === (device.id || device.device_id);
              const isCurrent = isCurrentDevice(device);
              const isOnline = device.online && !device.disabled;
              const rawName = device.custom_name || device.device_model || device.name || t('device_fallback_name', lang);
              const displayName = fixMojibake(rawName);
              const isVihtApp = isCurrent || (device.app_name && device.app_name.toLowerCase().includes('viht'));

              return (
                <motion.div
                  key={device.id || device.device_id || device.hwid}
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
                            <motion.button
                              type="button"
                              className="fluent-btn-icon fluent-btn-icon--save"
                              onClick={() => handleSaveRename(device)}
                              disabled={renameLoading}
                              whileTap={{ scale: 0.9 }}
                              transition={springs.tactile}
                            >
                              <Check size={14} />
                            </motion.button>
                            <motion.button
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
                            <motion.button
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

                      {isCurrent && (
                        <span className="fluent-chip fluent-chip--current">{t('devices_this_device', lang)}</span>
                      )}

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
                            <span className="ping-status-dot gray" /> {t('device_status_was', lang)} {device.last_active || t('device_status_recently', lang)}
                          </span>
                        )}
                      </span>
                    </div>
                  </div>

                  <div className="fluent-device-actions">
                    <motion.button
                      type="button"
                      className={`btn btn--icon btn--sm ${device.disabled ? 'btn--activate' : 'btn--pause'}`}
                      onClick={() => handleToggleDevice(device)}
                      title={device.disabled ? t('device_btn_enable', lang) : t('device_btn_disable', lang)}
                      whileTap={{ scale: 0.9 }}
                      transition={springs.tactile}
                    >
                      <Power size={14} />
                    </motion.button>

                    <motion.button
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

