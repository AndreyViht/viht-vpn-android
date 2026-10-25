(function () {
  'use strict';
  const callbacks = new Map();
  const listeners = new Map();
  const queued = new Map();
  const page = String(Date.now()) + '-' + Math.random().toString(36).slice(2);
  let sequence = 0;
  function call(method, args = [], timeout = 30000) {
    return new Promise((resolve, reject) => {
      const id = page + '-' + (++sequence);
      const timer = setTimeout(() => { callbacks.delete(id); reject(new Error('Android не ответил вовремя. Повторите действие.')); }, timeout);
      callbacks.set(id, { resolve, reject, timer });
      try {
        if (!window.VihtNative?.request) throw new Error('Нативная часть Android недоступна');
        window.VihtNative.request(id, method, JSON.stringify(args));
      } catch (error) { clearTimeout(timer); callbacks.delete(id); reject(error); }
    });
  }
  window.__vihtNativeCallback = function (id, result) {
    const pending = callbacks.get(String(id));
    if (!pending) return;
    clearTimeout(pending.timer); callbacks.delete(String(id)); pending.resolve(result);
  };
  function subscribe(type, callback) {
    if (!listeners.has(type)) listeners.set(type, new Set());
    const current = listeners.get(type); current.add(callback);
    if (queued.has(type)) queueMicrotask(() => {
      if (current.has(callback) && queued.has(type)) { const value = queued.get(type); queued.delete(type); callback(value); }
    });
    return () => current.delete(callback);
  }
  window.__vihtNativeEvent = function (type, data) {
    if (type === 'auth-success' && data.restore && localStorage.getItem('viht_desktop_token') === data.token) return;
    const targets = listeners.get(type);
    if (!targets?.size) { if (type === 'auth-success' || type === 'auth-error' || type === 'quick-connect' || type === 'open-updates') queued.set(type, data); return; }
    for (const callback of [...targets]) callback(data);
  };
  window.electronAPI = Object.freeze({
    openExternal: url => call('openExternal', [url]),
    getSystemInfo: () => call('getSystemInfo'), getAppVersion: () => call('getAppVersion'),
    getOwnerSession: options => call('getOwnerSession', [options || {}]),
    clearOwnerSession: () => { queued.delete('auth-success'); return call('clearOwnerSession'); },
    startAuthServer: provider => call('startAuthServer', [provider]), cancelAuthServer: () => call('cancelAuthServer'),
    onAuthSuccess: callback => subscribe('auth-success', callback), onAuthError: callback => subscribe('auth-error', callback),
    cabinetRequest: options => call('cabinetRequest', [options], 65000),
    fetchSubscription: token => call('fetchSubscription', [token]), syncServers: servers => call('syncServers', [servers]),
    pingServers: servers => call('pingServers', [servers], 7000),
    vpnConnect: (server, settings) => call('vpnConnect', [server, settings], 95000),
    vpnDisconnect: () => call('vpnDisconnect', [], 20000), vpnUpdateSettings: settings => call('vpnUpdateSettings', [settings], 60000),
    getVpnStatus: () => call('getVpnStatus'), onVpnStateChanged: callback => subscribe('vpn-state', callback), onVpnTrafficStats: callback => subscribe('vpn-traffic', callback),
    exportDebugLog: () => call('exportDebugLog'),
    getLoginItemSettings: () => call('getLoginItemSettings'), setLoginItemSettings: options => call('setLoginItemSettings', [options]),
    getDesktopPreferences: () => call('getDesktopPreferences'), setDesktopPreferences: options => call('setDesktopPreferences', [options]),
    addQuickTile: () => call('addQuickTile'),
    onQuickConnect: callback => subscribe('quick-connect', callback), onOpenUpdates: callback => subscribe('open-updates', callback),
    openVpnSettings: () => call('openVpnSettings'), openNotificationSettings: () => call('openNotificationSettings'),
    checkForUpdates: () => call('checkForUpdates'), getUpdateStatus: () => call('getUpdateStatus'),
    downloadUpdate: release => call('downloadUpdate', [release], 310000), applyUpdateAndRestart: release => call('applyUpdateAndRestart', [release]),
    onUpdateAvailable: callback => subscribe('update-available', callback), onUpdateProgress: callback => subscribe('update-progress', callback),
  });
  window.addEventListener('pagehide', () => {
    for (const pending of callbacks.values()) { clearTimeout(pending.timer); pending.reject(new Error('Экран приложения закрыт')); }
    callbacks.clear();
  }, { once: true });
})();
