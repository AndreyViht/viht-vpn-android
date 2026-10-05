(function() {
  if (window.electronAPI) return;

  const pendingCallbacks = new Map();
  let callbackId = 0;

  window.__vihtNativeCallback = function(id, result) {
    const key = String(id);
    const cb = pendingCallbacks.get(key);
    if (cb) {
      pendingCallbacks.delete(key);
      if (typeof result === 'string') {
        try {
          cb(JSON.parse(result));
          return;
        } catch (e) {}
      }
      cb(result);
    }
  };

  function callNativeAsync(method, ...args) {
    return new Promise((resolve) => {
      const id = String(++callbackId);
      pendingCallbacks.set(id, resolve);
      if (window.VihtNative && window.VihtNative[method]) {
        try {
          window.VihtNative[method](id, ...args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a ?? '')));
        } catch (err) {
          console.error('[VihtBridge] Error calling ' + method + ':', err);
          resolve({ ok: false, error: err.message });
        }
      } else {
        console.warn('[VihtBridge] Method not found on VihtNative:', method);
        resolve({ ok: false, error: 'Method not available' });
      }
    });
  }

  window.electronAPI = {
    // Window controls
    minimizeWindow: () => { if (window.VihtNative) window.VihtNative.minimizeWindow(); },
    closeWindow: () => { if (window.VihtNative) window.VihtNative.closeWindow(); },
    openExternal: (url) => { if (window.VihtNative) window.VihtNative.openExternal(url); else window.open(url, '_blank'); },

    // System & Version
    getSystemInfo: () => callNativeAsync('getSystemInfo'),
    getAppVersion: () => callNativeAsync('getAppVersion'),
    getLoginItemSettings: async () => ({ openAtLogin: false, openAsHidden: false }),
    setLoginItemSettings: async () => true,
    checkForeignVpn: async () => ({ hasForeignVpn: false }),

    // Auth
    startAuthServer: (provider) => callNativeAsync('startAuthServer', provider),
    onAuthSuccess: (callback) => { window.__onAuthSuccess = callback; },

    // Credentials
    saveCredentials: (key, value) => callNativeAsync('saveCredentials', key, value),
    getCredentials: (key) => callNativeAsync('getCredentials', key),
    clearCredentials: () => callNativeAsync('clearCredentials'),

    // Ping
    pingServers: (servers) => callNativeAsync('pingServers', servers),

    // VPN Engine
    vpnConnect: (server, settings) => callNativeAsync('vpnConnect', server, settings),
    vpnDisconnect: () => callNativeAsync('vpnDisconnect'),
    vpnUpdateSettings: (settings) => callNativeAsync('vpnUpdateSettings', settings),
    getVpnStatus: () => callNativeAsync('getVpnStatus'),
    onVpnStateChanged: (callback) => { window.__onVpnStateChanged = callback; },
    onVpnTrafficStats: (callback) => { window.__onVpnTrafficStats = callback; },
    onSingleInstanceAlert: (callback) => {},
    onVpnConflict: (callback) => {},
    updateTrayStatus: (data) => {},
    onTraySelectServer: (callback) => {},
    onTrayToggleVpn: (callback) => {},
    onNavigateTo: (callback) => {},

    // Updates
    checkForUpdates: async () => ({ hasUpdate: false }),
    downloadUpdate: async () => {},
    applyUpdateAndRestart: async () => {},
    resetVersionForTesting: async () => {},
    onUpdateProgress: (cb) => {}
  };

  console.log('[VihtBridge] window.electronAPI successfully initialized on Android');
})();
