function configToClient(config) {
  const outbound = config?.outbounds?.find(item => item.protocol === 'vless');
  const target = outbound?.settings?.vnext?.[0];
  const user = target?.users?.[0];
  if (!target?.address || !user?.id) return null;
  const stream = outbound.streamSettings || {};
  const security = stream.security || 'none';
  const tls = security === 'reality' ? stream.realitySettings || {} : stream.tlsSettings || {};
  const params = new URLSearchParams({ type: stream.network || 'tcp', security });
  const fields = {
    encryption: user.encryption || 'none', flow: user.flow,
    sni: tls.serverName, fp: tls.fingerprint, pbk: tls.publicKey, sid: tls.shortId,
    path: stream.wsSettings?.path || stream.xhttpSettings?.path || stream.httpSettings?.path,
    host: stream.wsSettings?.headers?.Host || stream.xhttpSettings?.host,
    serviceName: stream.grpcSettings?.serviceName, mode: stream.xhttpSettings?.mode,
    alpn: Array.isArray(tls.alpn) ? tls.alpn.join(',') : undefined,
  };
  for (const [key, value] of Object.entries(fields)) {
    if (value != null && value !== '') params.set(key, String(value));
  }
  const address = target.address.includes(':') && !target.address.startsWith('[')
    ? `[${target.address}]` : target.address;
  return {
    inbound_remarks: config.remarks || config.tag || '',
    vless_uri: `vless://${user.id}@${address}:${target.port}?${params}#${encodeURIComponent(config.remarks || config.tag || '')}`,
  };
}

// Read the same subscription delivered to VPN clients, including JSON and base64 feeds.
export function parseSubscriptionBody(body) {
  let text = String(body || '').trim();
  if (!text) throw new Error('Сервер вернул пустой ответ подписки');
  if (!text.startsWith('[') && !text.startsWith('{') && !text.includes('vless://')) {
    try {
      const bytes = Uint8Array.from(atob(text.replace(/\s/g, '')), char => char.charCodeAt(0));
      text = new TextDecoder().decode(bytes).trim();
    } catch {
      throw new Error('Не удалось прочитать подписку');
    }
  }
  if (text.startsWith('[') || text.startsWith('{')) {
    const parsed = JSON.parse(text);
    const configs = Array.isArray(parsed) ? parsed : parsed.outbounds ? [parsed] : null;
    if (!configs) throw new Error('Неизвестный формат подписки');
    return configs.flatMap(config => {
      if (typeof config === 'string') return [{ vless_uri: config }];
      if (config?.vless_uri) return [config];
      if (String(config?.uri||'').startsWith('vless://')) return [{...config,vless_uri:config.uri,inbound_remarks:config.name||config.location||''}];
      const client = configToClient(config);
      return client ? [client] : [];
    });
  }
  const links = text.split(/\r?\n/).map(line => line.trim()).filter(line => line.startsWith('vless://'));
  if (!links.length) throw new Error('В ответе нет данных подписки');
  return links.map(vless_uri => ({ vless_uri }));
}

export function parseApiClients(clients = []) {
  if (!Array.isArray(clients)) return [];
  const servers = [];
  const seen = new Set();
  for (const client of clients) {
    if (!client || client.enabled === false) continue;
    const uri = String(client.vless_uri || '').trim();
    if (!uri.startsWith('vless://') || seen.has(uri)) continue;
    let url;
    try { url = new URL(uri); } catch { continue; }
    const host = url.hostname.replace(/^\[|\]$/g, '');
    const port = Number(url.port || 443);
    // Subscription notices are dummy localhost entries, never connection targets.
    if (!host || ['localhost', '127.0.0.1', '::1', '0.0.0.0'].includes(host)
      || !url.username || !Number.isInteger(port) || port < 1 || port > 65535) continue;
    let name = String(client.inbound_remarks || '').trim();
    if (!name) {
      try { name = decodeURIComponent(url.hash.slice(1)); } catch { name = url.hash.slice(1); }
    }
    name ||= host;
    if (name.includes('временно выключен') || name.includes('LTE тестовые протоколы')) continue;
    let flag = '🌐';
    if (name.includes('🇷🇺') || name.includes('Ютуб')) flag = '🇷🇺';
    else if (name.includes('🇩🇪') || name.includes('Германия')) flag = '🇩🇪';
    else if (name.includes('🇳🇱') || name.includes('Нидерланды')) flag = '🇳🇱';
    else if (name.includes('🇸🇪') || name.includes('Стокгольм')) flag = '🇸🇪';
    seen.add(uri);
    servers.push({
      id: `subscription_${encodeURIComponent(uri)}`, name, flag, host, port,
      ping: null, pingStatus: 'unmeasured', isRecommended: false,
      vless_uri: uri, vlessUri: uri,
      isYouTubeNoAds: /Ютуб|YouTube/i.test(name), isLte: /LTE/i.test(name),
    });
  }
  return servers;
}

export function formatServerPing(server, lang = 'ru') {
  if (Number.isFinite(server?.ping) && server.ping >= 0) return `${server.ping} ms`;
  return server?.pingStatus === 'unreachable' ? (lang === 'en' ? 'No response' : 'Нет ответа') : '—';
}

export function getServerCountry(server) {
  const value = `${server?.flag || ''} ${server?.name || ''}`;
  if (value.includes('🇷🇺') || /Ютуб|YouTube|Россия/i.test(value)) return 'RU';
  if (value.includes('🇩🇪') || /Германия|Germany/i.test(value)) return 'DE';
  if (value.includes('🇳🇱') || /Нидерланды|Netherlands/i.test(value)) return 'NL';
  if (value.includes('🇸🇪') || /Стокгольм|Stockholm|Sweden/i.test(value)) return 'SE';
  return 'GLOBAL';
}
