export async function restoreStoredSession(bridge) {
  const result = await bridge.getStoredOwnerSession();
  if (!result?.ok) throw new Error(result?.error || 'Не удалось восстановить сохранённый вход. Повторите попытку.');
  if (result.hasSession === false) return null;
  if (typeof result.token !== 'string' || (!/^vg_[a-f0-9]{64}$/.test(result.token) && result.token.split('.').length !== 3)) {
    throw new Error('Не удалось прочитать сохранённый вход. Данные сохранены; повторите попытку.');
  }
  return {token:result.token,provider:result.provider || (result.token.startsWith('vg_')?'guest':'yandex')};
}
