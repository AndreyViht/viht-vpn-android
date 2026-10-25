const ACCOUNT_KEYS = ['viht_desktop_token', 'viht_subscription_token', 'viht_devices_cache', 'viht_servers_cache', 'viht_chosen_provider', 'viht_vpn_state'];

export function clearAccountStorage(storage = localStorage) {
  ACCOUNT_KEYS.forEach(key => storage.removeItem(key));
}

export function subscriptionIsActive(user, now = Date.now()) {
  if (!user || user.status === 'disabled' || user.display_status === 'disabled') return false;
  const expiry = Date.parse(user.expires_at || user.subscription_expires_at || user.trial_ends_at || '');
  if (Number.isFinite(expiry)) return expiry > now;
  return user.subscription_active === true && Number(user.subscription_days_left) > 0;
}

export function normalizeProfile(profile) {
  const expiry = profile.expires_at || profile.subscription_expires_at || null;
  const active = subscriptionIsActive(profile);
  return {
    ...profile, subscription_expires_at: expiry,
    subscription_active: active,
    subscription_days_left: Number.isFinite(Number(profile.subscription_days_left)) ? Math.max(0, Number(profile.subscription_days_left)) : 0,
  };
}
