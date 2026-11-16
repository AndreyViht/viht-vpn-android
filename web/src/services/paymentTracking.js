export const PAYMENT_POLL_LIMIT = 30 * 60 * 1000;
export function normalizePayment(value, now = Date.now()) {
  if (!value || typeof value.label !== 'string' || typeof value.paymentUrl !== 'string') return null;
  const createdAt = Number(value.createdAt);
  return { ...value, createdAt: Number.isFinite(createdAt) && createdAt > 0 && createdAt <= now ? createdAt : now };
}
export function paymentPollDelay(order, now = Date.now()) {
  if (!order) return null;
  if (now - order.createdAt >= PAYMENT_POLL_LIMIT) return 60000;
  return now - order.createdAt < 120000 ? 10000 : 30000;
}
export function archivePayment(history, order) {
  return [order, ...history.filter(item => item.label !== order.label)].slice(0,20);
}

export const paymentRecordsEvent = 'viht-payment-records';

export function paymentStorageKey(accountId, product) {
  return 'viht_payment_v1:' + accountId + (product === 'slots' ? ':slots' : '');
}

export function readLocalPayments(accountId) {
  return ['subscription', 'slots'].flatMap(product => {
    const key = paymentStorageKey(accountId, product);
    try {
      const current = JSON.parse(localStorage.getItem(key));
      const history = JSON.parse(localStorage.getItem(key + ':history'));
      const records = [current, ...(Array.isArray(history) ? history : [])].map(value => normalizePayment(value)).filter(Boolean);
      return Array.from(new Map(records.reverse().map(value => [value.label, {...value, product}])).values());
    } catch {
      return [];
    }
  }).sort((a,b)=>b.createdAt-a.createdAt);
}
