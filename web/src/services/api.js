import { parseSubscriptionBody } from './servers';
export { parseApiClients } from './servers';
import { subscriptionIsActive } from './account';

const API_BASE = 'https://anviht.ru/api';

export async function fetchSubscriptionClients(subscriptionToken) {
  if (!window.electronAPI?.fetchSubscription) {
    return { ok: false, error: 'Перезапустите приложение для обновления подписки' };
  }
  let timer;
  try {
    const subscription = await Promise.race([
      window.electronAPI.fetchSubscription(subscriptionToken),
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('Время синхронизации истекло. Повторите попытку.')), 12000); }),
    ]);
    if (!subscription?.ok) return { ok: false, error: subscription?.error || 'Ошибка загрузки подписки' };
    return { ok: true, clients: parseSubscriptionBody(subscription.body) };
  } catch (error) {
    return { ok: false, error: error.message || 'Ошибка загрузки подписки' };
  } finally {
    clearTimeout(timer);
  }
}

async function ownerToken(fallbackToken, rejectedToken = '') {
  if (window.electronAPI?.getOwnerSession) {
    const result = await window.electronAPI.getOwnerSession({ fallbackToken, rejectedToken });
    if (!result?.ok) {
      const error = new Error(result?.error || 'Не удалось получить сессию');
      error.unauthorized = Boolean(result?.unauthorized);
      throw error;
    }
    localStorage.setItem('viht_desktop_token', result.token);
    return result.token;
  }
  if (typeof fallbackToken === 'string' && fallbackToken.split('.').length === 3) return fallbackToken;
  const error = new Error('Войдите в аккаунт'); error.unauthorized = true; throw error;
}

async function cabinetRequest(token, action, { hwid, method = 'GET', body } = {}) {
  try {
    const url = new URL(API_BASE + '/cabinet-api');
    url.searchParams.set('action', action);
    if (method === 'GET') url.searchParams.set('ts', Date.now());
    const deviceHwid = hwid || localStorage.getItem('viht_client_hwid');
    if (deviceHwid) url.searchParams.set('hwid', deviceHwid);
    const version = localStorage.getItem('viht_app_version');
    if (version) url.searchParams.set('app_version', version);
    let accessToken = await ownerToken(token);
    const send = () => fetch(url, {
      method, cache: 'no-store', signal: AbortSignal.timeout(12000),
      headers: { Authorization: 'Bearer ' + accessToken, ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    });
    let response = await send();
    if (response.status === 401) {
      accessToken = await ownerToken(token, accessToken);
      response = await send();
    }
    if (response.status === 401) {
      const error = new Error('Сессия истекла. Войдите заново.'); error.unauthorized = true; throw error;
    }
    const data = await response.json().catch(() => null);
    if (!response.ok || !data) return { ok: false, error: data?.error || 'Ошибка сервиса: HTTP ' + response.status };
    return { ok: data.ok !== false, data, error: data.error };
  } catch (error) {
    if (error.unauthorized) window.dispatchEvent(new CustomEvent('viht-auth-required'));
    return { ok: false, unauthorized: Boolean(error.unauthorized), error: error.name === 'TimeoutError' ? 'Сервис не ответил вовремя. Повторите попытку.' : error.message || 'Ошибка соединения' };
  }
}

export async function fetchCabinetProfile(token, hwid = null, { includeKeys = true } = {}) {
  const result = await cabinetRequest(token, 'profile', { hwid });
  if (!result.ok) return result;
  const profile = result.data.profile || result.data.user;
  if (!profile) return { ok: false, error: 'Профиль не найден. Завершите регистрацию в личном кабинете на сайте.' };
  if (!includeKeys) {
    delete result.data.clients; // Never replace live subscription keys with the cabinet's legacy list.
    return result;
  }
  if (profile.subscription_token && subscriptionIsActive(profile)) {
    const subscription = await fetchSubscriptionClients(profile.subscription_token);
    if (!subscription.ok) return { ...result, data: { ...result.data, clients: null }, keysError: subscription.error };
    result.data.clients = subscription.clients;
  } else {
    result.data.clients = [];
  }
  return result;
}

export async function fetchActiveDevices(token, hwid = null) {
  const result = await cabinetRequest(token, 'active-devices', { hwid });
  return result.ok ? result.data : result;
}
export async function deviceAction(token, params = {}) {
  const result = await cabinetRequest(token, 'device-action', { method: 'POST', body: params });
  return result.ok ? result.data : result;
}
export async function fetchPayments(token) {
  const result = await cabinetRequest(token, 'payments');
  return result.ok ? result.data : result;
}
export async function resetSubscriptionKeys(token) {
  const result = await cabinetRequest(token, 'reset-keys', { method: 'POST', body: {} });
  return result.ok ? result.data : result;
}
