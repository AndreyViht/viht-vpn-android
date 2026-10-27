export const PAYMENT_POLL_LIMIT = 30 * 60 * 1000;
export function normalizePayment(value, now = Date.now()) {
  if (!value || typeof value.label !== 'string' || typeof value.paymentUrl !== 'string') return null;
  const createdAt = Number(value.createdAt);
  return { ...value, createdAt: Number.isFinite(createdAt) && createdAt > 0 && createdAt <= now ? createdAt : now };
}
export function paymentPollDelay(order, now = Date.now()) {
  if (!order || now - order.createdAt >= PAYMENT_POLL_LIMIT) return null;
  return now - order.createdAt < 120000 ? 10000 : 30000;
}
export function archivePayment(history, order) {
  return [order, ...history.filter(item => item.label !== order.label)].slice(0,20);
}
