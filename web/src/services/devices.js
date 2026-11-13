export function slotUsage(devices, limits = {}) {
  const reported = Number(limits.active_count);
  const used = limits.active_count != null && Number.isFinite(reported) && reported >= 0
    ? reported : devices.filter(d => !d.deleted && !d.archived && !d.observation_only && !d.slot_exempt).length;
  const maximum = Number(limits.total_slots);
  const total = limits.total_slots != null && Number.isFinite(maximum) && maximum >= 0 ? maximum : null;
  return { used, total, percent: total > 0 ? Math.min(100, Math.round(used / total * 100)) : 0 };
}
export function deviceState(device) {
  if (device.disabled || device.deleted || device.archived) return 'offline';
  if (device.online === true) return 'online';
  if (device.online_known === false) return 'unknown';
  return 'offline';
}
export function deviceAppLabel(device) {
  const name = String(device.app_name || '');
  const platform = String(device.platform || device.device_os || '').toLowerCase();
  const version = device.app_version ? ` v${device.app_version}` : '';
  if (/viht/i.test(name)) {
    const system = /android/i.test(platform + ' ' + name) ? 'Android' : /windows|desktop|win32/i.test(platform + ' ' + name) ? 'Windows' : 'VPN';
    return `💠 Viht ${system}${version}`;
  }
  if (/happ/i.test(name)) return platform.includes('ios') ? `🍏 Viht iOS / Happ${version}` : `🟣 Happ${version}`;
  if (/incy/i.test(name)) return `🍏 Incy${version}`;
  if (/v2rayng/i.test(name)) return `🚀 v2rayNG${version}`;
  if (/v2raytun/i.test(name)) return `⚡ V2RayTun${version}`;
  return name ? name + version : 'VPN Клиент';
}

export function devicePresenceText(device, lang = 'ru', now = Date.now()) {
  const english = lang === 'en';
  if (device.revocation_pending) return english ? 'Disabling access…' : 'Отключаем доступ…';
  if (device.disabled) return english ? 'Access disabled' : 'Доступ отключён';
  if (deviceState(device) === 'online') return english ? 'VPN connected' : 'VPN подключён';
  const vpnTime = Date.parse(device.last_vpn_activity_at || '');
  const appTime = Date.parse(device.last_seen || '');
  const time = Number.isFinite(vpnTime) ? vpnTime : appTime;
  if (Number.isFinite(time) && time <= now + 10000) {
    const age = Math.max(0, now - time);
    const minutes = Math.floor(age / 60000);
    let when;
    if (minutes < 1) when = english ? 'just now' : 'только что';
    else if (minutes < 60) when = minutes + (english ? ' min ago' : ' мин. назад');
    else if (minutes < 1440) when = Math.floor(minutes / 60) + (english ? ' h ago' : ' ч. назад');
    else when = new Date(time).toLocaleDateString(english ? 'en-GB' : 'ru-RU', {day:'numeric',month:'short'});
    return (Number.isFinite(vpnTime) ? (english ? 'VPN active ' : 'VPN работал ') : (english ? 'App active ' : 'Приложение открывали ')) + when;
  }
  return deviceState(device) === 'offline'
    ? (english ? 'VPN disconnected' : 'VPN выключен')
    : (english ? 'Waiting for app to report' : 'Ожидаем связи с приложением');
}
