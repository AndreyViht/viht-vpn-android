import SubscriptionBilling from './components/SubscriptionBilling';
import React, { useState, useEffect, useCallback, useRef } from 'react';
import TitleBar from './components/TitleBar';
import MainVpnScreen from './components/MainVpnScreen';
import ServersScreen from './components/ServersScreen';
import DevicesScreen from './components/DevicesScreen';
import ProfileScreen from './components/ProfileScreen';
import SettingsScreen from './components/SettingsScreen';
import LoginScreen from './components/LoginScreen';
import NoSubscriptionScreen from './components/NoSubscriptionScreen';
import IosNotificationBanner from './components/IosNotificationBanner';
import ForeignVpnModal from './components/ForeignVpnModal';
import IosBottomNav from './components/IosBottomNav';
import { isMobile, isAndroid } from './utils/platform';
import { parseApiClients, fetchCabinetProfile, fetchSubscriptionClients } from './services/api';
import { Loader2 } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { springs, pageVariants } from './utils/motion';
import './App.css';
import './liquid-glass.css';
import './android.css';
import ProfileErrorScreen from './components/ProfileErrorScreen';
import { clearAccountStorage, normalizeProfile, subscriptionIsActive } from './services/account';
import { publishTelemetry } from './services/telemetry';
import LiquidBackdrop, { useGlassPointer } from './components/LiquidBackdrop';

export default function App() {
  const glassRef = useGlassPointer();
  const pageMotionRef=useRef(null);
  const preparePageMotion=useCallback(()=>{if(pageMotionRef.current)pageMotionRef.current.style.willChange='transform, opacity, filter'},[]);
  const releasePageMotion=useCallback(()=>{if(pageMotionRef.current)pageMotionRef.current.style.willChange='auto'},[]);

  const [isWindowDragging, setIsWindowDragging] = useState(false);
  useEffect(() => window.electronAPI?.onWindowDragState?.(setIsWindowDragging), []);
  const [token, setToken] = useState(() => {
    const savedToken = localStorage.getItem('viht_desktop_token') || '';
    if (savedToken.startsWith('tg:')) {
      for (const key of ['viht_desktop_token', 'viht_chosen_provider', 'viht_subscription_token', 'viht_servers_cache', 'viht_devices_cache']) {
        localStorage.removeItem(key);
      }
      return '';
    }
    return savedToken;
  });
  const [loading, setLoading] = useState(true);
  const [profileError, setProfileError] = useState('');
  const [profileRetry, setProfileRetry] = useState(0);
  const [user, setUser] = useState(null);
  const [hwid, setHwid] = useState('');
  const [appVersion, setAppVersion] = useState('1.0.0');
  const [manualConnectOnly, setManualConnectOnly] = useState(true);
  const [toast, setToast] = useState(null); // { id, message, type: 'success' | 'ping' | 'error' }
  const [foreignVpnWarning, setForeignVpnWarning] = useState(null);

  // Navigation tab: 'home' | 'servers' | 'devices' | 'profile' | 'settings'
  const [activeTab, setActiveTab] = useState('home');
  useEffect(() => { const back=e=>{ if(!e.defaultPrevented&&!e.detail?.modal&&activeTab!=='home'){e.preventDefault();setActiveTab('home');} };window.addEventListener('viht-back',back);return()=>window.removeEventListener('viht-back',back); },[activeTab]);

  // Connection keys come exclusively from the current subscription.
  const [servers, setServers] = useState([]);
  const [quickConnectRequested, setQuickConnectRequested] = useState(false);
  const [availableUpdate, setAvailableUpdate] = useState(null);
  const [focusUpdates, setFocusUpdates] = useState(false);
  const [currentServer, setCurrentServer] = useState(null);
  const serversRef = useRef(servers);
  serversRef.current = servers;
  const tokenRef = useRef(token);
  tokenRef.current = token;
  const keySyncRequestRef = useRef(0);
  const keySyncInFlightRef = useRef(null);
  const settingsChangeRef = useRef(false);
  const lastKeySyncAtRef = useRef(0);

  useEffect(() => {
    let active=true;
    window.electronAPI?.getUiSnapshot?.().then(result=>{
      if(!active || !result?.snapshot?.user) return;
      const cached=result.snapshot;
      if (!tokenRef.current || (cached._owner && cached._owner!==decodeJwt(tokenRef.current)?.sub)) return;
      setUser(previous=>previous||cached.user);
      setProfileError('');
      if(Array.isArray(cached.servers) && cached.servers.length) {
        setServers(previous=>previous.length?previous:cached.servers);
        setCurrentServer(previous=>previous||cached.servers.find(server=>server.id===result.vpn?.connectionId)||cached.servers.find(server=>server.id===cached.currentServerId)||cached.servers[0]);
      }
      setLoading(false);
    }).catch(()=>{});
    return ()=>{active=false};
  },[]);
  useEffect(()=>{
    if(user && token) void window.electronAPI?.cacheUiSnapshot?.({_owner:decodeJwt(token)?.sub,user,servers,currentServerId:currentServer?.id}).catch(()=>{});
  },[user,servers,currentServer,token]);

  // Loading states for actions
  const [refreshingKeys, setRefreshingKeys] = useState(false);
  const [refreshingPing, setRefreshingPing] = useState(false);

  // VPN Connection State
  const [connectionState, setConnectionState] = useState('disconnected'); // 'disconnected' | 'connecting' | 'connected' | 'disconnecting' | 'error'
  const connectionStateRef = useRef(connectionState);
  connectionStateRef.current = connectionState;
  const [nativeServerName,setNativeServerName] = useState('');
  const [connectedAt, setConnectedAt] = useState(null);

  // Settings
  const [settings, setSettings] = useState(() => {
    try {
      const s = localStorage.getItem('viht_settings');
      if (s) return JSON.parse(s);
    } catch {}
    return {
      theme: 'emerald',
      language: 'ru',
      autoConnect: false,
      autoReconnect: true,
      autoLaunch: true,
      startMinimized: false,
      startInTray: false,
      minimizeToTray: true,
      notifyConnected: true,
      notifyDisconnected: true,
      notifyLost: true,
      notifyUpdate: true,
      killSwitch: true,
      allowLan: true,
      bypassRussia: true,
      dnsAdblock: true,
    };
  });

  // Reconnection & AutoConnect state tracking
  const RECONNECT_DELAYS = [2000, 5000, 10000, 20000, 30000];
  const autoConnectAttemptedRef = useRef(false);
  const manualDisconnectRef = useRef(false);
  const reconnectTimerRef = useRef(null);
  const reconnectAttemptsRef = useRef(0);
  const hadConnectedSessionRef = useRef(false);
  const stableConnectionTimerRef = useRef(null);

  const settingsRef = useRef(settings);
  settingsRef.current = settings;
  const currentServerRef = useRef(currentServer);
  currentServerRef.current = currentServer;
  const userRef = useRef(user);
  userRef.current = user;

  const applyServerList = useCallback((clients) => {
    const previousKeys = new Map(serversRef.current.map(server => [server.vless_uri, server]));
    const updated = parseApiClients(clients).map(server => {
      const previous = previousKeys.get(server.vless_uri);
      return previous && Date.now() - (previous.pingMeasuredAt || 0) < 180000
        ? { ...server, ping: previous.ping, pingStatus: previous.pingStatus, pingMeasuredAt: previous.pingMeasuredAt, isRecommended: previous.isRecommended }
        : server;
    });
    lastKeySyncAtRef.current = Date.now();
    serversRef.current = updated;
    void window.electronAPI?.syncServers?.(updated).catch(() => {});
    setServers(updated);
    setCurrentServer(previous => {
      const selected = updated.find(server => server.vless_uri === previous?.vless_uri) || updated[0] || null;
      currentServerRef.current = selected;
      return selected;
    });
    return updated;
  }, []);


  const handleSelectServerRef = useRef();
  const handleToggleConnectionRef = useRef();

  const showToast = (message, type = 'success') => {
    const id = Date.now();
    setToast({ id, message, type });
    setTimeout(() => {
      setToast((prev) => (prev?.id === id ? null : prev));
    }, type === 'error' ? 8000 : 3200);
  };

  // Helper to trigger automated VPN reconnection on unexpected drop
  const triggerAutoReconnect = (sourceState, eventData) => {
    if (isAndroid) return; // Android foreground service owns recovery, including while UI is closed.
    if (eventData?.retryable === false || !hadConnectedSessionRef.current || manualDisconnectRef.current || reconnectTimerRef.current) return;
    const s = settingsRef.current;
    if (s.autoReconnect === false) {
      if (s.notifyLost !== false) {
        showToast(eventData?.error || 'Связь с VPN прервана', 'error');
      }
      return;
    }

    const u = userRef.current;
    const isSubActive =
      subscriptionIsActive(u);

    if (!isSubActive) {
      console.log('[VihtVPN] AutoReconnect skipped: subscription is not active');
      return;
    }

    const targetServer = currentServerRef.current;
    if (!targetServer) {
      console.log('[VihtVPN] AutoReconnect skipped: no target server');
      return;
    }

    const attempt = reconnectAttemptsRef.current;
    if (attempt >= RECONNECT_DELAYS.length) {
      hadConnectedSessionRef.current = false;
      manualDisconnectRef.current = true;
      console.log('[VihtVPN] AutoReconnect max retries reached (5). Stopping retries.');
      showToast(
        s.language === 'en'
          ? 'Failed to reconnect to VPN automatically. Please check your internet connection.'
          : 'Не удалось восстановить связь с VPN автоматически. Проверьте интернет-соединение.',
        'error'
      );
      return;
    }

    const delay = RECONNECT_DELAYS[attempt];
    reconnectAttemptsRef.current += 1;
    const delaySec = Math.round(delay / 1000);

    const alertMsg =
      s.language === 'en'
        ? `VPN connection lost. Reconnecting in ${delaySec}s... (attempt ${attempt + 1}/${RECONNECT_DELAYS.length})`
        : `Связь с VPN потеряна. Повторное подключение через ${delaySec} сек... (попытка ${attempt + 1}/${RECONNECT_DELAYS.length})`;
    showToast(alertMsg, 'ping');

    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
    }

    reconnectTimerRef.current = setTimeout(async () => {
      reconnectTimerRef.current = null;
      if (manualDisconnectRef.current || !hadConnectedSessionRef.current) {
        console.log('[VihtVPN] AutoReconnect cancelled by manual user action.');
        return;
      }

      console.log(`[VihtVPN] Executing auto-reconnect attempt #${attempt + 1} to ${targetServer.name}...`);
      setConnectionState('connecting');

      try {
        if (window.electronAPI?.vpnConnect) {
          const res = await window.electronAPI.vpnConnect(targetServer, settingsRef.current);
          if (!res?.ok) {
            if (res?.cancelled) return;
            setConnectionState('error');
          }
        }
      } catch (err) {
        console.warn('[VihtVPN] Auto-reconnect exception:', err);
        setConnectionState('error');
      }
    }, delay);
  };

  // Helper to decode JWT token
  function decodeJwt(tok) {
    if (!tok || typeof tok !== 'string') return null;
    try {
      const parts = tok.split('.');
      if (parts.length < 2) return null;
      const base64Url = parts[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const json = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(json);
    } catch {
      return null;
    }
  }

  // 1. Initialize system HWID, synchronize login items, listen for OAuth & VPN events
  useEffect(() => {
    const preventDragDrop = (e) => {
      e.preventDefault();
      e.stopPropagation();
    };
    window.addEventListener('dragover', preventDragDrop, false);
    window.addEventListener('drop', preventDragDrop, false);

    if (window.electronAPI?.getAppVersion) {
      window.electronAPI.getAppVersion().then((ver) => {
        if (ver) {
          setAppVersion(ver);
          try { localStorage.setItem('viht_app_version', ver); } catch {}
        }
      }).catch(console.error);
    }

    if (window.electronAPI?.getSystemInfo) {
      window.electronAPI.getSystemInfo().then((info) => {
        setManualConnectOnly(Boolean(info?.manualConnectOnly));
        if (info?.hwid) {
          setHwid(info.hwid);
          try { localStorage.setItem('viht_client_hwid', info.hwid); } catch {}
        }
      }).catch(console.error);
    }

    // Synchronize autoLaunch / startMinimized with actual Windows login item settings
    if (window.electronAPI?.getLoginItemSettings) {
      window.electronAPI.getLoginItemSettings().then((loginSettings) => {
        if (loginSettings && typeof loginSettings === 'object') {
          setSettings((prev) => {
            const next = {
              ...prev,
              autoLaunch: Boolean(loginSettings.openAtLogin),
              startMinimized: Boolean(loginSettings.openAsHidden ?? loginSettings.startMinimized ?? prev.startMinimized),
            };
            try {
              localStorage.setItem('viht_settings', JSON.stringify(next));
            } catch {}
            return next;
          });
        }
      }).catch(console.error);
    }

    // Check if another VPN is already active on system launch (Requirements 10 & 11)
    if (window.electronAPI?.checkForeignVpn) {
      window.electronAPI.checkForeignVpn().then((res) => {
        if (res && res.hasForeignVpn && connectionStateRef.current === 'disconnected') {
          setForeignVpnWarning(res);
        }
      }).catch(console.error);
    }

    // Listen for duplicate instance attempt (Requirement 9: Single Instance)
    if (window.electronAPI?.onSingleInstanceAlert) {
      window.electronAPI.onSingleInstanceAlert((data) => {
        showToast(data?.message || 'Viht уже запущен на компьютере', 'ping');
      });
    }

    // Listen for route hijacking / conflicts during active connection (Requirement 16)
    if (window.electronAPI?.onVpnConflict) {
      window.electronAPI.onVpnConflict((conflict) => {
        showToast(`Внимание: сторонний VPN (${conflict?.summary || 'соединение'}) перехватил сетевые маршруты!`, 'error');
      });
    }

    const authUnsubscribe = window.electronAPI?.onAuthSuccess?.((data) => {
      const resolvedToken = data?.originalToken || data?.token;
      if (!resolvedToken) { showToast('Не удалось получить сессию. Повторите вход.', 'error'); return; }
      if (resolvedToken === tokenRef.current) { if (!data?.restore) setProfileRetry(value => value + 1); return; }
      if (decodeJwt(resolvedToken)?.sub && decodeJwt(resolvedToken)?.sub === decodeJwt(tokenRef.current)?.sub) { localStorage.setItem('viht_desktop_token',resolvedToken); setToken(resolvedToken); return; }
      clearAccountStorage();
      applyServerList([]);
      localStorage.setItem('viht_desktop_token', resolvedToken);
      localStorage.setItem('viht_chosen_provider', data.provider || 'yandex');
      setUser(null);
      setProfileError('');
      setToken(resolvedToken);
    });
    const unsubs = [authUnsubscribe];

    // Listen for VPN status changes from background TunnelManager
    if (window.electronAPI?.onVpnStateChanged) {
      unsubs.push(
        window.electronAPI.onVpnStateChanged((data) => {
          console.log('[VihtVPN] VPN state changed:', data);
          if (data?.state) {
            const previousState=connectionStateRef.current;
            setConnectionState(data.state);
            if(data.serverName) setNativeServerName(data.serverName);
            if(data.state==='disconnected') setNativeServerName('');
            try { localStorage.setItem('viht_vpn_state', data.state); } catch {}
            if (data.state === 'connected') {
              setConnectedAt(data?.connectedAt || Date.now());
              hadConnectedSessionRef.current = true;
              clearTimeout(stableConnectionTimerRef.current);
              stableConnectionTimerRef.current = setTimeout(() => {
                if (connectionStateRef.current === 'connected') reconnectAttemptsRef.current = 0;
              }, 15000);
              if (reconnectTimerRef.current) {
                clearTimeout(reconnectTimerRef.current);
                reconnectTimerRef.current = null;
              }
              if (!data.snapshot && previousState !== 'connected' && settingsRef.current.notifyConnected !== false) {
                const name=data.serverName||currentServerRef.current?.name;
                showToast(data.message || (name ? `VPN подключён · ${name}` : 'VPN подключён'), 'success');
              }
            } else if (data.state === 'disconnected') {
              clearTimeout(stableConnectionTimerRef.current);
              setConnectedAt(null);
              publishTelemetry(null);
              if (manualDisconnectRef.current) {
                if (settingsRef.current.notifyDisconnected !== false) {
                  showToast('VPN отключен', 'default');
                }
              } else {
                // Unexpected disconnection (sing-box crashed or tunnel dropped)
                triggerAutoReconnect('disconnected', data);
              }
            } else if (data.state === 'error') {
              clearTimeout(stableConnectionTimerRef.current);
              setConnectedAt(null);
              publishTelemetry(null);
              if (manualDisconnectRef.current || !hadConnectedSessionRef.current || data.retryable === false) {
                if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
                reconnectTimerRef.current = null;
                hadConnectedSessionRef.current = false;
                if (settingsRef.current.notifyLost !== false) {
                  showToast(data.error || 'Ошибка подключения VPN', 'error');
                }
              } else {
                // Unexpected error
                triggerAutoReconnect('error', data);
              }
            }
          }
        })
      );
    }

    // Listen for live traffic telemetry
    if (window.electronAPI?.onVpnTrafficStats) {
      unsubs.push(
        window.electronAPI.onVpnTrafficStats((stats) => {
          publishTelemetry(stats);
        })
      );
    }

    // Check initial VPN status
    if (window.electronAPI?.getVpnStatus) {
      window.electronAPI.getVpnStatus().then((status) => {
        if (status?.state) {
          setConnectionState(status.state);
          if(status.serverName) setNativeServerName(status.serverName);
          hadConnectedSessionRef.current = status.state === 'connected';
          if (status.state === 'error' && status.lastError) showToast(status.lastError, 'error');
        }
        if (status?.connectedAt) setConnectedAt(status.connectedAt);
        if (status?.stats) publishTelemetry(status.stats);
      }).catch(console.error);
    }

    return () => {
      unsubs.forEach((fn) => { if (typeof fn === 'function') fn(); });
    };
  }, []);

  // A profile error never creates a subscription or invents an expiry date.
  useEffect(() => {
    let mounted = true;
    async function loadProfile() {
      if (!token) { setLoading(false); return; }
      setLoading(!userRef.current);
      try {
        const result = await fetchCabinetProfile(token, hwid, {includeKeys:false});
        if (!mounted) return;
        if (!result.ok) {
          if (!result.unauthorized) { if (userRef.current) showToast('Не удалось обновить данные. Показана сохранённая информация.', 'info'); else setProfileError(result.error || 'Не удалось загрузить профиль. Повторите попытку.'); }
          return;
        }
        const profile = result.data.profile || result.data.user;
        const chosen = localStorage.getItem('viht_chosen_provider') || profile.current_provider || 'yandex';
        setUser({ ...normalizeProfile(profile), auth_provider: chosen, chosen_provider: chosen });
        if (profile.subscription_token) localStorage.setItem('viht_subscription_token', profile.subscription_token);
        else localStorage.removeItem('viht_subscription_token');
        if (profile.subscription_token && subscriptionIsActive(profile)) {
          void fetchSubscriptionClients(profile.subscription_token).then(keys=>{
            if(mounted && tokenRef.current===token && keys.ok) applyServerList(keys.clients);
          });
        } else applyServerList([]);
        setProfileError('');
      } finally { if (mounted) setLoading(false); }
    }
    void loadProfile();
    return () => { mounted = false; };
  }, [token, hwid, profileRetry, applyServerList]);

  // 3. Tray events & Tray status sync (Requirement 11)
  useEffect(() => {
    if (window.electronAPI?.updateTrayStatus) {
      window.electronAPI.updateTrayStatus({
        state: connectionState,
        serverName: currentServer?.name,
        servers: servers,
      });
    }
  }, [connectionState, currentServer, servers]);

  useEffect(() => {
    const unsubs = [];
    if (window.electronAPI?.onTraySelectServer) {
      unsubs.push(
        window.electronAPI.onTraySelectServer((srv) => {
          handleSelectServerRef.current?.(srv);
        })
      );
    }
    if (window.electronAPI?.onTrayToggleVpn) {
      unsubs.push(
        window.electronAPI.onTrayToggleVpn(() => {
          handleToggleConnectionRef.current?.();
        })
      );
    }
    if (window.electronAPI?.onNavigateTo) {
      unsubs.push(
        window.electronAPI.onNavigateTo((page) => {
          setActiveTab(page);
        })
      );
    }
    return () => {
      unsubs.forEach((fn) => { if (typeof fn === 'function') fn(); });
    };
  }, []);

  useEffect(() => {
    const first = window.electronAPI?.onQuickConnect?.(() => setQuickConnectRequested(true));
    const second = window.electronAPI?.onOpenUpdates?.(() => { setFocusUpdates(true); setActiveTab('settings'); });
    return () => { first?.(); second?.(); };
  }, []);
  useEffect(() => {
    if (!quickConnectRequested || loading || !user || !currentServer) return;
    setQuickConnectRequested(false);
    if (connectionStateRef.current !== 'connected' && connectionStateRef.current !== 'connecting') handleToggleConnectionRef.current?.();
  }, [quickConnectRequested, loading, user, currentServer]);

  // 4. Background subscription check every 15 minutes (Safe disconnect on expiry, zero disruption if active)
  useEffect(() => {
    if (!token) return;

    const checkExpiry = async () => {
      try {
        const res = await fetchCabinetProfile(token, hwid, { includeKeys: false });
        if (tokenRef.current !== token) return;
        if (res && res.ok && res.data) {
          const profile = res.data.profile || res.data.user || res.data;
          const isSubActive =
            subscriptionIsActive(profile);

          if (Array.isArray(res.data.clients)) applyServerList(res.data.clients);
          if (!isSubActive) {
            applyServerList([]);
            console.warn('[VihtVPN] Subscription expired in background!');
            if (connectionStateRef.current === 'connected' || connectionStateRef.current === 'connecting') {
              try {
                await window.electronAPI?.vpnDisconnect();
              } catch {}
              showToast('Срок действия подписки истёк. VPN был безопасно отключён.', 'error');
            }
            setUser((prev) => ({
              ...prev,
              subscription_active: false,
              subscription_days_left: 0,
            }));
          } else {
            // Subscription is active — update state smoothly without resetting tunnel or network
            setUser((prev) => ({
              ...prev,
              ...normalizeProfile(profile),
            }));

          }
        }
      } catch (err) {
        console.warn('[VihtVPN] Background expiry check network error:', err);
      }
    };

    const intervalId = setInterval(checkExpiry, 900000); // 15 mins (900 000 ms)
    return () => clearInterval(intervalId);
  }, [token, hwid]);

  // 5. Auto-Connect on application startup (Requirement 5)
  useEffect(() => {
    if (loading || manualConnectOnly) return;
    if (autoConnectAttemptedRef.current) return;
    autoConnectAttemptedRef.current = true;

    if (!settings.autoConnect) {
      console.log('[VihtVPN] AutoConnect disabled in settings.');
      return;
    }

    if (!token) {
      console.log('[VihtVPN] AutoConnect skipped: not authorized.');
      return;
    }

    const subActive =
      subscriptionIsActive(user);

    if (!subActive) {
      console.log('[VihtVPN] AutoConnect skipped: subscription inactive or expired.');
      return;
    }

    const targetServer = currentServer || servers[0];
    if (!targetServer) {
      console.log('[VihtVPN] AutoConnect skipped: no available servers.');
      return;
    }

    if (connectionStateRef.current === 'disconnected') {
      console.log(`[VihtVPN] AutoConnect executing on startup for: ${targetServer.name}`);
      manualDisconnectRef.current = false;
      reconnectAttemptsRef.current = 0;
      setConnectionState('connecting');

      if (window.electronAPI?.vpnConnect) {
        window.electronAPI.vpnConnect(targetServer, settings).then((res) => {
          if (!res?.ok) {
            setConnectionState('error');
            showToast(res?.error || 'Не удалось автоподключиться к VPN', 'error');
          }
        }).catch((err) => {
          setConnectionState('error');
          showToast(err.message || 'Ошибка автоподключения к VPN', 'error');
        });
      }
    }
  }, [loading, manualConnectOnly, user, token, settings.autoConnect, currentServer, servers]);

  // 6. Connect / Disconnect Action
  const handleToggleConnection = async () => {
    if (connectionState === 'disconnected' || connectionState === 'error') {
      hadConnectedSessionRef.current = false;
      clearTimeout(stableConnectionTimerRef.current);
      if (!currentServer?.vless_uri) {
        showToast('Нет актуального ключа. Обновите подписку на странице серверов.', 'error');
        return;
      }
      if (foreignVpnWarning) {
        setForeignVpnWarning(null);
        showToast('Проверка готовности сетевого подключения...', 'ping');
      }

      manualDisconnectRef.current = false;
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      reconnectAttemptsRef.current = 0;

      setConnectionState('connecting');
      try {
        if (window.electronAPI?.vpnConnect) {
          const res = await window.electronAPI.vpnConnect(currentServer, settings);
          if (!res?.ok) {
            setConnectionState('error');
            showToast(res?.error || 'Не удалось подключиться к VPN', 'error');
          }
        } else {
          throw new Error('Подключение к VPN доступно только в приложении Windows');
        }
      } catch (err) {
        console.error('Failed to connect VPN:', err);
        setConnectionState('error');
        showToast(err.message || 'Ошибка подключения к VPN', 'error');
      }
    } else if (connectionState === 'connected' || connectionState === 'connecting') {
      hadConnectedSessionRef.current = false;
      clearTimeout(stableConnectionTimerRef.current);
      manualDisconnectRef.current = true;
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      reconnectAttemptsRef.current = 0;

      setConnectionState('disconnecting');
      try {
        if (window.electronAPI?.vpnDisconnect) {
          await window.electronAPI.vpnDisconnect();
        } else {
          setConnectionState('disconnected');
        }
      } catch (err) {
        console.error('Failed to disconnect VPN:', err);
        setConnectionState('disconnected');
      }
    }
  };

  // 7. Safe Server Selection (Requirement 10)
  const handleSelectServer = useCallback(async (srv) => {
    if (!srv) return;
    if (srv.id === currentServerRef.current?.id) return;

    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    reconnectAttemptsRef.current = 0;

    if (connectionStateRef.current === 'connected' || connectionStateRef.current === 'connecting') {
      manualDisconnectRef.current = true; // Mark as intentional disconnect to avoid auto-reconnecting to previous server
      hadConnectedSessionRef.current = false;
      clearTimeout(stableConnectionTimerRef.current);
      showToast(`Переключение на ${srv.name}...`, 'ping');
      setConnectionState('disconnecting');
      try {
        await window.electronAPI?.vpnDisconnect();
        setCurrentServer(srv);
        currentServerRef.current = srv;
        manualDisconnectRef.current = false;
        setConnectionState('connecting');
        const res = await window.electronAPI?.vpnConnect(srv, settingsRef.current);
        if (!res?.ok) {
          setConnectionState('error');
          showToast(res?.error || 'Ошибка подключения к новому серверу', 'error');
        }
      } catch (e) {
        setConnectionState('error');
        showToast('Ошибка при переключении сервера', 'error');
      }
    } else {
      setCurrentServer(srv);
      currentServerRef.current = srv;
      showToast(`Выбран сервер: ${srv.name}`, 'success');
    }
  }, []);

  handleSelectServerRef.current = handleSelectServer;
  handleToggleConnectionRef.current = handleToggleConnection;

  // Every sync replaces the complete list, including an empty subscription.
  const synchronizeKeys = useCallback(async (silent = false) => {
    if (!token) {
      if (!silent) showToast('Войдите в аккаунт для синхронизации', 'error');
      return null;
    }
    let flight = keySyncInFlightRef.current;
    if (!flight || flight.token !== token) {
      const request = ++keySyncRequestRef.current;
      flight = { token, promise: null };
      flight.promise = (async () => {
        try {
          const subscriptionToken = userRef.current?.subscription_token;
          const res = subscriptionToken
            ? await fetchSubscriptionClients(subscriptionToken)
            : await fetchCabinetProfile(token, hwid);
          if (tokenRef.current !== token || request !== keySyncRequestRef.current) return { ok: false };
          const clients = res.clients || res.data?.clients;
          if (!res.ok || !Array.isArray(clients)) {
            if (res.unauthorized) applyServerList([]);
            return { ok: false, error: res.unauthorized ? 'Сессия истекла. Войдите снова.' : res.error || 'Не удалось синхронизировать подписку' };
          }
          const updated = applyServerList(clients);
          const profile = res.data?.profile || res.data?.user;
          if (profile) setUser(previous => ({ ...previous, ...normalizeProfile(profile) }));
          return { ok: true, servers: updated };
        } catch {
          return { ok: false, error: 'Ошибка при синхронизации подписки' };
        }
      })();
      keySyncInFlightRef.current = flight;
    }
    if (!silent) setRefreshingKeys(true);
    try {
      const result = await flight.promise;
      if (tokenRef.current !== token) return null;
      if (!silent) showToast(result.ok
        ? result.servers.length ? 'Ключи обновлены из подписки' : 'В подписке нет доступных ключей'
        : result.error || 'Синхронизация отменена', result.ok ? 'success' : 'error');
      return result.ok ? result.servers : null;
    } finally {
      if (keySyncInFlightRef.current === flight) keySyncInFlightRef.current = null;
      if (!silent && tokenRef.current === token) setRefreshingKeys(false);
    }
  }, [token, hwid, applyServerList]);

  const handleRefreshKeys = useCallback(() => synchronizeKeys(false), [synchronizeKeys]);

  useEffect(() => {
    if (activeTab !== 'servers' || !token || !hwid) return;
    const refresh = () => {
      if (Date.now() - lastKeySyncAtRef.current >= 60000) void synchronizeKeys(true);
    };
    refresh();
    const timer = setInterval(refresh, 60000);
    window.addEventListener('focus', refresh);
    window.addEventListener('online', refresh);
    return () => {
      clearInterval(timer);
      window.removeEventListener('focus', refresh);
      window.removeEventListener('online', refresh);
    };
  }, [activeTab, token, hwid, synchronizeKeys]);

  const handleRefreshPing = useCallback(async () => {
    setRefreshingPing(true);
    let pingTimer;
    try {
      const latest = serversRef.current;
      if (!latest.length) {
        showToast('В подписке нет доступных серверов', 'error');
        return;
      }
      if (!window.electronAPI?.pingServers) {
        showToast('Замер пинга доступен только в приложении Windows', 'error');
        return;
      }
      const results = await Promise.race([
        window.electronAPI.pingServers(latest.map(({ id, host, port, vless_uri }) => ({ id, host, port, vless_uri }))),
        new Promise((_, reject) => { pingTimer = setTimeout(() => reject(new Error('Ping timed out')), 6500); }),
      ]);
      // Ignore a result belonging to a superseded subscription snapshot.
      const current = serversRef.current;
      if (tokenRef.current !== token || current.length !== latest.length
        || current.some((server, index) => server.vless_uri !== latest[index].vless_uri)) return;
      const measured = latest.map(server => {
        const value = results?.[server.id];
        const reachable = Number.isFinite(value) && value >= 0;
        return { ...server, ping: reachable ? value : null, pingStatus: reachable ? 'measured' : 'unreachable', pingMeasuredAt: Date.now(), isRecommended: false };
      });
      const candidates = measured.filter(server => server.ping !== null && !server.isYouTubeNoAds);
      const best = candidates.reduce((previous, server) => !previous || server.ping < previous.ping ? server : previous, null);
      const updated = measured.map(server => ({ ...server, isRecommended: server.id === best?.id }));
      serversRef.current = updated;
      setServers(updated);
      setCurrentServer(previous => {
        const selected = updated.find(server => server.id === previous?.id) || updated[0] || null;
        currentServerRef.current = selected;
        return selected;
      });
      showToast(best ? 'Пинг измерен • Лучший: ' + best.ping + ' ms' : 'Замер завершён. Доступных серверов не найдено.', best ? 'ping' : 'error');
    } catch (error) {
      showToast(error.message?.includes('Ping timed out') ? 'Истекло время проверки ключей' : error.message || 'Ошибка при проверке ключей', 'error');
    } finally {
      clearTimeout(pingTimer);
      setRefreshingPing(false);
    }
  }, [token]);

  const handleUpdateSetting = useCallback(async (key, value) => {
    const networkChange = ['killSwitch', 'allowLan', 'bypassRussia', 'dnsAdblock', 'excludedApps', 'excludedDomains', 'bypassRules'].includes(key);
    if (networkChange && settingsChangeRef.current) {
      showToast('Дождитесь применения предыдущей сетевой настройки', 'info');
      return;
    }
    if (networkChange) settingsChangeRef.current = true;
    const previous = settingsRef.current;
    const next = { ...previous, [key]: value };
    settingsRef.current = next;
    setSettings(next);
    const wasManual = manualDisconnectRef.current;
    try {
      localStorage.setItem('viht_settings', JSON.stringify(next));
      let result = { ok: true };
      if (['autoLaunch', 'startMinimized'].includes(key)) {
        result = await window.electronAPI?.setLoginItemSettings?.({ openAtLogin: Boolean(next.autoLaunch), startMinimized: Boolean(next.startMinimized) });
      } else if (['minimizeToTray', 'notifyUpdate'].includes(key)) {
        result = await window.electronAPI?.setDesktopPreferences?.({ [key]: value });
      } else if (networkChange) {
        // Reconfiguring the current tunnel is deliberate, not an unexpected drop.
        manualDisconnectRef.current = true;
        result = await window.electronAPI?.vpnUpdateSettings?.(next);
      }
      if (!result?.ok) throw new Error(result?.error || 'Перезапустите приложение, чтобы применить настройку');
      return result;
    } catch (error) {
      if (settingsRef.current[key] === value) {
        const restored = { ...settingsRef.current, [key]: previous[key] };
        settingsRef.current = restored;
        setSettings(restored);
        try { localStorage.setItem('viht_settings', JSON.stringify(restored)); } catch {}
      }
      showToast(error.message || 'Не удалось применить настройку', 'error');
      return {ok:false,error:error.message};
    } finally {
      if (networkChange) settingsChangeRef.current = false;
      if (networkChange) manualDisconnectRef.current = tokenRef.current ? wasManual : true;
    }
  }, []);

  useEffect(() => {
    window.electronAPI?.setDesktopPreferences?.({ minimizeToTray: settingsRef.current.minimizeToTray !== false, notifyUpdate: settingsRef.current.notifyUpdate !== false }).catch(() => {});
    return window.electronAPI?.onUpdateAvailable?.(release => {
      setAvailableUpdate(release);
      if (settingsRef.current.notifyUpdate !== false) showToast('Доступна версия ' + release.version + '. Обновление — в настройках.', 'info');
    });
  }, []);

  const handleLogout = useCallback(async () => {
    hadConnectedSessionRef.current = false;
    clearTimeout(stableConnectionTimerRef.current);
    manualDisconnectRef.current = true;
    clearTimeout(reconnectTimerRef.current);
    reconnectTimerRef.current = null;
    reconnectAttemptsRef.current = 0;
    ++keySyncRequestRef.current;
    tokenRef.current = '';
    keySyncInFlightRef.current = null;
    clearAccountStorage();
    applyServerList([]);
    setToken(''); setUser(null); setProfileError(''); setLoading(false);
    setConnectionState('disconnected'); setActiveTab('home');
    await Promise.allSettled([
      window.electronAPI?.clearOwnerSession?.(),
      window.electronAPI?.vpnDisconnect?.(),
    ]);
  }, [applyServerList]);

  useEffect(() => {
    const expired = () => { void handleLogout(); };
    window.addEventListener('viht-auth-required', expired);
    return () => window.removeEventListener('viht-auth-required', expired);
  }, [handleLogout]);

  const handleSwitchProvider = (newProvider) => {
    localStorage.setItem('viht_chosen_provider', newProvider);
    setUser((prev) => (prev ? { ...prev, chosen_provider: newProvider, auth_provider: newProvider } : null));
    const label = newProvider === 'vk' ? 'VK ID' : newProvider === 'yandex' ? 'Яндекс ID' : 'Telegram';
    showToast(`Выбран аккаунт: ${label}`, 'success');
  };

  // Requirement 5: Open external Help guide
  const handleOpenHelp = () => {
    const helpUrl = 'https://help.anviht.ru/';
    if (window.electronAPI?.openExternal) {
      window.electronAPI.openExternal(helpUrl);
    } else {
      window.open(helpUrl, '_blank');
    }
  };

  // Determine current screen view
  const isSubActive =
    subscriptionIsActive(user);

  const currentLang = settings?.language || 'ru';

  return (
    <div ref={glassRef} className={`fluent-window liquid-window theme-${settings?.theme || 'emerald'} ${isWindowDragging ? 'is-window-dragging' : ''} ${isMobile ? 'is-mobile' : ''} ${isAndroid ? 'is-android' : ''}`}>
      {/* Living Ambient Aura (Dynamic Island / Fluent glow) */}
      <LiquidBackdrop />

      {/* Native Windows 11 TitleBar */}
      <TitleBar title="VihtVPN" isConnected={connectionState === 'connected'} user={user} onOpenHelp={handleOpenHelp} minimizeToTray={settings.minimizeToTray !== false} />
      <IosNotificationBanner toast={toast} onClose={() => setToast(null)} />
      {token && user && <SubscriptionBilling key={String(user.id || user.telegram_id || user.yandex_email || token)} token={token} accountId={String(user.id || user.telegram_id || user.yandex_email || 'current')} onPaid={() => setProfileRetry(n => n + 1)} />}
      {token && user && <SubscriptionBilling product="slots" key={'slots:' + String(user.id || user.telegram_id || user.yandex_email || token)} token={token} accountId={String(user.id || user.telegram_id || user.yandex_email || 'current')} onPaid={() => setProfileRetry(n => n + 1)} />}
      {availableUpdate && <div className="fluent-card" style={{position:'absolute',top:58,left:16,right:16,zIndex:1000,display:'flex',alignItems:'center',gap:10,padding:14}}>
        <span style={{flex:1}}>Доступно обновление {availableUpdate.version}</span>
        <button className="btn btn--primary btn--sm" onClick={() => {setFocusUpdates(true);setActiveTab('settings');setAvailableUpdate(null);}}>Обновить</button>
        <button className="btn btn--secondary btn--sm" aria-label="Закрыть" onClick={() => setAvailableUpdate(null)}>×</button>
      </div>}
      <ForeignVpnModal
        detection={foreignVpnWarning}
        onClose={() => setForeignVpnWarning(null)}
        lang={currentLang}
      />

      <div className="fluent-window-body">
        {loading ? (
          <div className="fluent-splash">
            <Loader2 size={38} className="spinning fluent-spinner-glow" />
            <span className="fluent-splash-text">
              {currentLang === 'en' ? 'Loading Viht ID profile...' : 'Загрузка профиля Viht ID...'}
            </span>
          </div>
        ) : !token ? (
          <LoginScreen />
        ) : profileError ? (
          <ProfileErrorScreen error={profileError} onRetry={() => setProfileRetry(value => value + 1)} onLogout={handleLogout} />
        ) : !isSubActive ? (
          <NoSubscriptionScreen
            user={user}
            onRefresh={() => setProfileRetry(value => value + 1)}
            onLogout={handleLogout}
          />
        ) : (
          <div className="fluent-app-layout">
            {/* Main Content Workspace with Silky Page Transitions */}
            <main className="fluent-main-viewport">
              <AnimatePresence mode="wait">
                {activeTab === 'home' && (
                  <motion.div
                    key="page-home"
                    className="fluent-page-motion-wrap"
                    ref={pageMotionRef}
                    onAnimationStart={preparePageMotion}
                    onAnimationComplete={releasePageMotion}
                    {...pageVariants}
                  >
                    <MainVpnScreen
                      user={user}
                      currentServer={connectionState === 'connected' && nativeServerName ? {...currentServer,name:nativeServerName} : currentServer}
                      connectionState={connectionState}
                      connectedAt={connectedAt}
                      onToggleConnection={handleToggleConnection}
                      onOpenServerSelector={() => setActiveTab('servers')}
                      lang={currentLang}
                    />
                  </motion.div>
                )}

                {activeTab === 'servers' && (
                  <motion.div
                    key="page-servers"
                    className="fluent-page-motion-wrap"
                    ref={pageMotionRef}
                    onAnimationStart={preparePageMotion}
                    onAnimationComplete={releasePageMotion}
                    {...pageVariants}
                  >
                    <ServersScreen
                      servers={servers}
                      currentServer={connectionState === 'connected' && nativeServerName ? {...currentServer,name:nativeServerName} : currentServer}
                      connectionState={connectionState}
                      onSelectServer={handleSelectServer}
                      onRefreshKeys={handleRefreshKeys}
                      refreshingKeys={refreshingKeys}
                      onRefreshPing={handleRefreshPing}
                      refreshingPing={refreshingPing}
                      lang={currentLang}
                    />
                  </motion.div>
                )}

                {activeTab === 'devices' && (
                  <motion.div
                    key="page-devices"
                    className="fluent-page-motion-wrap"
                    ref={pageMotionRef}
                    onAnimationStart={preparePageMotion}
                    onAnimationComplete={releasePageMotion}
                    {...pageVariants}
                  >
                    <DevicesScreen onBindingChanged={() => synchronizeKeys(true)}
                      token={token}
                      hwid={hwid}
                      limits={user?.limits}
                      lang={currentLang}
                    />
                  </motion.div>
                )}

                {activeTab === 'profile' && (
                  <motion.div
                    key="page-profile"
                    className="fluent-page-motion-wrap"
                    ref={pageMotionRef}
                    onAnimationStart={preparePageMotion}
                    onAnimationComplete={releasePageMotion}
                    {...pageVariants}
                  >
                    <ProfileScreen
                      user={user}
                      hwid={hwid}
                      token={token}
                      onLogout={handleLogout}
                      onSwitchProvider={handleSwitchProvider}
                      lang={currentLang}
                    />
                  </motion.div>
                )}

                {activeTab === 'settings' && (
                  <motion.div
                    key="page-settings"
                    className="fluent-page-motion-wrap"
                    ref={pageMotionRef}
                    onAnimationStart={preparePageMotion}
                    onAnimationComplete={releasePageMotion}
                    {...pageVariants}
                  >
                    <SettingsScreen initialCategory={focusUpdates ? 'advanced' : 'connection'}
                      settings={settings}
                      onUpdateSetting={handleUpdateSetting}
                      clientVersion={appVersion}
                      hwid={hwid}
                    />
                  </motion.div>
                )}
              </AnimatePresence>
            </main>

            {/* iOS Style Floating Island Bottom Navigation (Mobile only) */}
            <IosBottomNav
                activeTab={activeTab}
                onTabChange={setActiveTab}
                lang={currentLang}
            />
          </div>
        )}
      </div>
    </div>
  );
}
