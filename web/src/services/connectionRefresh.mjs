export async function freshConnectionTarget(previous, refresh, isCurrent) {
  const updated = await refresh();
  if (!isCurrent()) return {cancelled:true};
  if (Array.isArray(updated)) {
    const server = updated.find(s=>s.vless_uri===previous?.vless_uri) ||
      updated.find(s=>s.name===previous?.name) || updated[0];
    return server?.vless_uri ? {server} : {error:'В подписке нет доступных серверов'};
  }
  // A temporary network failure must not discard an already downloaded key.
  return previous?.vless_uri ? {server:previous} : {error:'Не удалось получить подключение из подписки. Повторяем при следующем подключении.'};
}
