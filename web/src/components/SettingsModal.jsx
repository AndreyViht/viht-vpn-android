import React, { useState, useEffect } from 'react';
import {
  X,
  ChevronLeft,
  ChevronRight,
  Sliders,
  HelpCircle,
  LogOut,
  ExternalLink,
  Check,
  Monitor,
  RefreshCw,
  ArrowUpCircle,
  Sparkles,
  RotateCcw,
  Loader2,
} from 'lucide-react';
import { getAuthProviderInfo } from './TitleBar';
import SocialIcon from './SocialIcon';

export default function SettingsModal({
  user,
  hwid,
  settings,
  onUpdateSetting,
  onLogout,
  onSwitchProvider,
  onClose,
}) {
  // Navigation stack: 'main' | 'system'
  const [navStack, setNavStack] = useState(['main']);

  // Seamless Auto-Updater State
  const [clientVersion, setClientVersion] = useState('1.0.0');
  const [updateStatus, setUpdateStatus] = useState('idle'); // 'idle' | 'checking' | 'available' | 'downloading' | 'ready' | 'up-to-date' | 'error'
  const [updateInfo, setUpdateInfo] = useState(null);
  const [updateProgress, setUpdateProgress] = useState({ percent: 0, downloadedMb: '0', totalMb: '38.4', speedMb: '0' });
  const [updateError, setUpdateError] = useState('');

  useEffect(() => {
    if (window.electronAPI?.getAppVersion) {
      window.electronAPI.getAppVersion().then((v) => {
        if (v) setClientVersion(v);
      }).catch(console.error);
    }

    if (window.electronAPI?.onUpdateProgress) {
      window.electronAPI.onUpdateProgress((prog) => {
        if (prog) {
          setUpdateProgress(prog);
          if (prog.percent >= 100) {
            setUpdateStatus('ready');
          }
        }
      });
    }
  }, []);

  const handleCheckUpdates = async () => {
    setUpdateStatus('checking');
    setUpdateError('');
    try {
      if (window.electronAPI?.checkForUpdates) {
        const res = await window.electronAPI.checkForUpdates();
        if (res.hasUpdate) {
          setUpdateInfo(res.releaseInfo);
          setUpdateStatus('available');
        } else {
          setUpdateStatus('up-to-date');
          setTimeout(() => setUpdateStatus('idle'), 4000);
        }
      } else {
        // Fallback simulate
        setTimeout(() => {
          setUpdateStatus('up-to-date');
          setTimeout(() => setUpdateStatus('idle'), 3000);
        }, 800);
      }
    } catch (err) {
      console.error('Update check error:', err);
      setUpdateStatus('error');
      setUpdateError('Не удалось связаться с сервером обновлений');
    }
  };

  const handleDownloadUpdate = async () => {
    if (!updateInfo) return;
    setUpdateStatus('downloading');
    setUpdateProgress({ percent: 0, downloadedMb: '0', totalMb: '38.4', speedMb: '4.8' });
    try {
      if (window.electronAPI?.downloadUpdate) {
        const res = await window.electronAPI.downloadUpdate(updateInfo);
        if (res?.ok) {
          setUpdateStatus('ready');
        } else {
          setUpdateStatus('error');
          setUpdateError(res?.error || 'Ошибка при загрузке обновления');
        }
      }
    } catch (err) {
      console.error('Download update error:', err);
      setUpdateStatus('error');
      setUpdateError('Ошибка сети при загрузке обновления');
    }
  };

  const handleApplyUpdateAndRestart = async () => {
    if (window.electronAPI?.applyUpdateAndRestart) {
      await window.electronAPI.applyUpdateAndRestart(updateInfo);
    }
  };

  const handleResetForTesting = async () => {
    if (window.electronAPI?.resetVersionForTesting) {
      await window.electronAPI.resetVersionForTesting('1.0.0');
    }
  };

  const currentPage = navStack[navStack.length - 1];

  const pushPage = (pageName) => {
    setNavStack((prev) => [...prev, pageName]);
  };

  const popPage = () => {
    if (navStack.length > 1) {
      setNavStack((prev) => prev.slice(0, -1));
    }
  };

  const provider = getAuthProviderInfo(user) || {
    type: 'vk',
    label: 'VK ID',
    badge: 'VK',
    account: user?.vk_name || (user?.vk_id ? `id${user.vk_id}` : 'VK ID'),
  };

  const openCabinet = () => {
    if (window.electronAPI?.openExternal) {
      window.electronAPI.openExternal('https://anviht.ru/lk');
    } else {
      window.open('https://anviht.ru/lk', '_blank');
    }
  };

  const openSupport = () => {
    if (window.electronAPI?.openExternal) {
      window.electronAPI.openExternal('https://t.me/VihtVPN_Support');
    } else {
      window.open('https://t.me/VihtVPN_Support', '_blank');
    }
  };

  const getPageTitle = () => {
    switch (currentPage) {
      case 'system':
        return 'Системные настройки';
      default:
        return 'Настройки VihtVPN';
    }
  };

  const formatSubTime = (u) => {
    if (!u) return '493 дн. 18 ч.';
    if (u.subscription_expires_at) {
      const diff = new Date(u.subscription_expires_at).getTime() - Date.now();
      if (diff > 0) {
        const totalHours = Math.floor(diff / (1000 * 60 * 60));
        const d = Math.floor(totalHours / 24);
        const h = totalHours % 24;
        return `${d} дн. ${h} ч.`;
      }
    }
    const d = u.subscription_days_left ?? 493;
    const h = u.subscription_hours_left ?? 18;
    return `${d} дн. ${h} ч.`;
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-sheet modal-sheet--settings" onClick={(e) => e.stopPropagation()}>
        {/* Navigation Header */}
        <div className="modal-header">
          {currentPage !== 'main' ? (
            <button className="modal-back-btn" onClick={popPage}>
              <ChevronLeft size={16} />
              <span>Назад</span>
            </button>
          ) : (
            <div className="modal-title-wrap">
              <Sliders size={17} className="modal-title-icon" />
              <h3 className="modal-title">{getPageTitle()}</h3>
            </div>
          )}

          {currentPage !== 'main' && (
            <span className="modal-subnav-title">{getPageTitle()}</span>
          )}

          <button className="modal-close-btn" onClick={onClose} title="Закрыть">
            <X size={17} />
          </button>
        </div>

        {/* ─── PAGE 1: MAIN SETTINGS MENU ─── */}
        {currentPage === 'main' && (
          <div className="settings-body settings-page-anim">
            {/* User Profile Card with Clear Provider Identity */}
            <div className="settings-user-card">
              <div className="settings-user-avatar-wrap">
                <div className="settings-user-avatar">
                  <span>{(provider.account || 'U').charAt(0).toUpperCase()}</span>
                </div>
                <SocialIcon
                  type={provider.type}
                  size={16}
                  shape="circle"
                  className="settings-avatar-badge-icon"
                />
              </div>

              <div className="settings-user-info">
                <div className="settings-user-top">
                  <span className="settings-user-name">{provider.account}</span>
                  <span className={`settings-provider-tag settings-provider-tag--${provider.type}`}>
                    {provider.label}
                  </span>
                </div>
                <span className="settings-user-sub">
                  Подписка активна • {formatSubTime(user)}
                </span>
              </div>

              <button className="settings-cabinet-link" onClick={openCabinet} title="Личный кабинет">
                <ExternalLink size={16} />
              </button>
            </div>

            {/* Linked Accounts Switcher (VK ID / Yandex ID / Telegram) */}
            {Array.isArray(user?.authorizations) && user.authorizations.length > 1 && (
              <div className="settings-linked-section">
                <span className="settings-linked-title">Привязанные аккаунты:</span>
                <div className="settings-linked-pills">
                  {user.authorizations
                    .filter((a) => a.provider === 'vk' || a.provider === 'yandex' || a.provider === 'telegram')
                    .map((a, idx) => {
                      const isCurrent =
                        (provider.type === 'vk' && a.provider === 'vk') ||
                        (provider.type === 'yandex' && a.provider === 'yandex') ||
                        (provider.type === 'tg' && a.provider === 'telegram');
                      const pClass = a.provider === 'vk' ? 'vk' : a.provider === 'yandex' ? 'yandex' : 'tg';
                      return (
                        <button
                          key={`auth-${a.provider}-${idx}`}
                          type="button"
                          className={`settings-linked-chip ${isCurrent ? 'active' : ''} settings-linked-chip--${pClass}`}
                          onClick={() => onSwitchProvider && onSwitchProvider(a.provider)}
                          title={`Выбрать ${a.title || a.name}`}
                        >
                          <SocialIcon type={pClass} size={16} shape="squircle" />
                          <span className="settings-linked-name">{a.name || a.identifier}</span>
                          {isCurrent && <Check size={12} className="settings-linked-check" />}
                        </button>
                      );
                    })}
                </div>
              </div>
            )}

            {/* Section: System Submenu */}
            <div className="settings-group">
              <span className="settings-group-label">Система</span>

              <div className="settings-menu-list">
                <div className="settings-nav-item" onClick={() => pushPage('system')}>
                  <div className="settings-nav-item__icon-wrap settings-nav-item__icon-wrap--system">
                    <Monitor size={16} />
                  </div>
                  <div className="settings-nav-item__text">
                    <span className="settings-nav-item__title">Системные настройки</span>
                    <span className="settings-nav-item__desc">Автозапуск, трей, Kill Switch, WinTun, обновления</span>
                  </div>
                  <ChevronRight size={17} className="settings-nav-item__arrow" />
                </div>
              </div>
            </div>

            {/* Section: Support & Actions */}
            <div className="settings-actions">
              <button className="settings-btn-support" onClick={openSupport}>
                <HelpCircle size={15} />
                <span>Техподдержка в Telegram</span>
              </button>

              <button className="settings-btn-logout" onClick={onLogout}>
                <LogOut size={15} />
                <span>Выйти из аккаунта</span>
              </button>
            </div>
          </div>
        )}

        {/* ─── PAGE 2: СИСТЕМНЫЕ НАСТРОЙКИ ─── */}
        {currentPage === 'system' && (
          <div className="settings-body settings-page-anim">
            <div className="settings-group">
              <span className="settings-group-label">Параметры приложения</span>

              <div className="settings-menu-list">
                {/* Автозапуск */}
                <div
                  className="settings-row"
                  onClick={() => onUpdateSetting('autoLaunch', !settings.autoLaunch)}
                >
                  <div className="settings-row__text">
                    <span className="settings-row__title">Автозапуск с Windows</span>
                    <span className="settings-row__desc">Запускать свернутым при старте системы</span>
                  </div>
                  <button type="button" className={`settings-switch ${settings.autoLaunch ? 'active' : ''}`}>
                    <div className="settings-switch-thumb" />
                  </button>
                </div>

                {/* Сворачивать в трей */}
                <div
                  className="settings-row"
                  onClick={() => onUpdateSetting('minimizeToTray', !settings.minimizeToTray)}
                >
                  <div className="settings-row__text">
                    <span className="settings-row__title">Сворачивать в трей</span>
                    <span className="settings-row__desc">При закрытии крестиком прятать к часам</span>
                  </div>
                  <button type="button" className={`settings-switch ${settings.minimizeToTray ? 'active' : ''}`}>
                    <div className="settings-switch-thumb" />
                  </button>
                </div>

                {/* Kill Switch */}
                <div
                  className="settings-row"
                  onClick={() => onUpdateSetting('killSwitch', !settings.killSwitch)}
                >
                  <div className="settings-row__text">
                    <span className="settings-row__title">Kill Switch</span>
                    <span className="settings-row__desc">Блокировать интернет при случайном обрыве VPN</span>
                  </div>
                  <button type="button" className={`settings-switch ${settings.killSwitch ? 'active' : ''}`}>
                    <div className="settings-switch-thumb" />
                  </button>
                </div>
              </div>
            </div>

            {/* TUN Драйвер и протокол */}
            <div className="settings-group">
              <span className="settings-group-label">Сетевой драйвер</span>

              <div className="settings-menu-list">
                <div
                  className="settings-row"
                  onClick={() => onUpdateSetting('wintunDriver', settings.wintunDriver === false ? true : false)}
                >
                  <div className="settings-row__text">
                    <span className="settings-row__title">Драйвер WinTun (Рекомендуется)</span>
                    <span className="settings-row__desc">
                      Высокопроизводительный драйвер уровня ядра для Windows
                    </span>
                  </div>
                  <button type="button" className={`settings-switch ${settings.wintunDriver !== false ? 'active' : ''}`}>
                    <div className="settings-switch-thumb" />
                  </button>
                </div>

                <div
                  className="settings-row"
                  onClick={() => onUpdateSetting('systemProxyFallback', !settings.systemProxyFallback)}
                >
                  <div className="settings-row__text">
                    <span className="settings-row__title">Системный прокси (Fallback)</span>
                    <span className="settings-row__desc">
                      Резервный режим через системные настройки прокси Windows
                    </span>
                  </div>
                  <button type="button" className={`settings-switch ${settings.systemProxyFallback ? 'active' : ''}`}>
                    <div className="settings-switch-thumb" />
                  </button>
                </div>
              </div>
            </div>

            {/* Seamless Auto-Updater Panel */}
            <div className="settings-group">
              <span className="settings-group-label">Центр обновлений VihtVPN</span>

              <div className="settings-update-card">
                <div className="settings-update-top">
                  <div className="settings-update-badge-col">
                    <span className="settings-update-app-title">VihtVPN для Windows</span>
                    <div className="settings-update-ver-row">
                      <span className="settings-update-current-ver">Текущая версия: v{clientVersion}</span>
                      {updateStatus === 'up-to-date' && (
                        <span className="settings-update-chip settings-update-chip--ok">
                          <Check size={11} /> Актуальная версия
                        </span>
                      )}
                      {updateStatus === 'available' && (
                        <span className="settings-update-chip settings-update-chip--new">
                          Доступна v{updateInfo?.version}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="settings-update-btn-wrap">
                    {updateStatus === 'idle' || updateStatus === 'up-to-date' || updateStatus === 'error' ? (
                      <button
                        type="button"
                        className="btn btn--secondary btn--sm settings-check-update-btn"
                        onClick={handleCheckUpdates}
                      >
                        <RefreshCw size={13} />
                        <span>Проверить</span>
                      </button>
                    ) : updateStatus === 'checking' ? (
                      <button type="button" className="btn btn--secondary btn--sm" disabled>
                        <Loader2 size={13} className="spinning" />
                        <span>Проверка...</span>
                      </button>
                    ) : null}
                  </div>
                </div>

                {/* State: Update Available */}
                {updateStatus === 'available' && (
                  <div className="settings-update-alert settings-update-alert--available">
                    <div className="settings-update-alert__header">
                      <ArrowUpCircle size={18} className="settings-update-alert__icon" />
                      <div className="settings-update-alert__meta">
                        <span className="settings-update-alert__title">
                          Доступна новая версия v{updateInfo?.version}!
                        </span>
                        <span className="settings-update-alert__notes">
                          {updateInfo?.releaseNotes || 'Повышена стабильность подключения и оптимизирована скорость.'}
                        </span>
                      </div>
                    </div>
                    <button
                      type="button"
                      className="btn btn--primary btn--full btn--glow settings-update-dl-btn"
                      onClick={handleDownloadUpdate}
                    >
                      <Sparkles size={14} />
                      <span>Обновить сейчас (бесшовно)</span>
                    </button>
                  </div>
                )}

                {/* State: Downloading with Progress */}
                {updateStatus === 'downloading' && (
                  <div className="settings-update-download-box">
                    <div className="settings-update-progress-meta">
                      <span className="settings-update-dl-label">Загрузка обновления v{updateInfo?.version}...</span>
                      <span className="settings-update-dl-percent">{updateProgress.percent}%</span>
                    </div>
                    <div className="settings-update-progress-bar">
                      <div
                        className="settings-update-progress-fill"
                        style={{ width: `${updateProgress.percent}%` }}
                      />
                    </div>
                    <div className="settings-update-progress-stats">
                      <span>{updateProgress.downloadedMb} MB / {updateProgress.totalMb} MB</span>
                      <span>Скорость: {updateProgress.speedMb} MB/s</span>
                    </div>
                  </div>
                )}

                {/* State: Ready to install */}
                {updateStatus === 'ready' && (
                  <div className="settings-update-alert settings-update-alert--ready">
                    <div className="settings-update-ready-header">
                      <Check size={18} className="settings-update-ready-icon" />
                      <span className="settings-update-ready-title">Обновление готово к установке!</span>
                    </div>
                    <p className="settings-update-ready-desc">
                      Пакет обновления v{updateInfo?.version} скачан. Перезапустите приложение для применения.
                    </p>
                    <button
                      type="button"
                      className="btn btn--primary btn--full btn--glow settings-update-apply-btn"
                      onClick={handleApplyUpdateAndRestart}
                    >
                      <Sparkles size={15} />
                      <span>Перезапустить и применить (v{updateInfo?.version})</span>
                    </button>
                  </div>
                )}

                {updateError && (
                  <p className="settings-update-error">{updateError}</p>
                )}

                {/* Test toggle to reset back to 1.0.0 */}
                {clientVersion !== '1.0.0' && updateStatus === 'idle' && (
                  <div className="settings-update-test-row">
                    <button
                      type="button"
                      className="settings-btn-test-reset"
                      onClick={handleResetForTesting}
                      title="Сбросить версию для повторного тестирования обновления"
                    >
                      <RotateCcw size={12} />
                      <span>Сбросить на v1.0.0 (для повторного теста обновления)</span>
                    </button>
                  </div>
                )}
              </div>
            </div>

            {/* Device & Hardware Info */}
            <div className="settings-info-box">
              <div className="settings-info-row">
                <span className="settings-info-label">ID Устройства (HWID):</span>
                <span className="settings-info-val">
                  {hwid ? `${hwid.slice(0, 8)}...${hwid.slice(-6)}` : 'D5276D88...D4AD8A'}
                </span>
              </div>
              <div className="settings-info-row">
                <span className="settings-info-label">Версия клиента:</span>
                <span className="settings-info-val">v{clientVersion} Desktop (64-bit)</span>
              </div>
              <div className="settings-info-row">
                <span className="settings-info-label">Сетевой драйвер:</span>
                <span className="settings-info-val">WinTun Virtual Adapter</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
