import React, { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  User,
  CreditCard,
  ExternalLink,
  Shield,
  RotateCcw,
  LogOut,
  Check,
  RefreshCw,
  Calendar,
  Clock,
  Send,
  Loader2,
  Copy,
  AlertTriangle,
} from 'lucide-react';
import SocialIcon from './SocialIcon';
import { fetchPayments, resetSubscriptionKeys } from '../services/api';
import { fixMojibake, t } from '../utils/i18n';
import { springs, buttonPress, cardPress } from '../utils/motion';

function ProfileScreen({
  user,
  hwid,
  token,
  onLogout,
  onSwitchProvider,
  lang = 'ru',
}) {
  const [activeTab, setActiveTab] = useState('accounts'); // 'accounts' | 'payments'

  // Payments data
  const [payments, setPayments] = useState([]);
  const [paymentsLoading, setPaymentsLoading] = useState(false);
  const [paymentsLoaded, setPaymentsLoaded] = useState(false);

  // Key reset state
  const [resettingKeys, setResettingKeys] = useState(false);
  const [toast, setToast] = useState(null);

  const showToast = (message, isError = false) => {
    setToast({ message, isError });
    setTimeout(() => setToast(null), 3500);
  };

  const loadPayments = useCallback(async () => {
    if (!token) return;
    setPaymentsLoading(true);
    try {
      const res = await fetchPayments(token);
      if (res && res.ok) {
        setPayments(res.payments || []);
        setPaymentsLoaded(true);
      } else {
        showToast(res?.error || 'Не удалось загрузить историю платежей', true);
      }
    } catch (e) {
      showToast('Ошибка сети при загрузке платежей', true);
    } finally {
      setPaymentsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    if (activeTab === 'payments' && !paymentsLoaded) {
      loadPayments();
    }
  }, [activeTab, paymentsLoaded, loadPayments]);

  const handleResetKeys = async () => {
    if (!window.confirm('Вы уверены, что хотите сбросить ключи подключения? Все текущие ссылки на устройствах перестанут работать и потребуется обновить подключение.')) {
      return;
    }
    setResettingKeys(true);
    try {
      const res = await resetSubscriptionKeys(token);
      if (res && res.ok) {
        showToast('Ключи подписки успешно обновлены!');
      } else {
        showToast(res?.error || 'Не удалось сбросить ключи', true);
      }
    } catch (e) {
      showToast('Ошибка сети при сбросе ключей', true);
    } finally {
      setResettingKeys(false);
    }
  };

  const openCabinet = () => {
    const url = 'https://anviht.ru/lk';
    if (window.electronAPI?.openExternal) window.electronAPI.openExternal(url);
    else window.open(url, '_blank');
  };

  const formatSource = (src) => {
    switch (src) {
      case 'site': return 'ЮMoney / Сайт';
      case 'bot_grant': return 'Telegram Stars';
      case 'admin': return 'Администратор';
      case 'referral': return 'Бонус за друга';
      default: return src || 'Оплата онлайн';
    }
  };

  const formatSubTime = (u) => {
    if (!u) return '—';
    if (u.subscription_expires_at) {
      const diff = new Date(u.subscription_expires_at).getTime() - Date.now();
      if (diff > 0) {
        const totalHours = Math.floor(diff / (1000 * 60 * 60));
        const d = Math.floor(totalHours / 24);
        const h = totalHours % 24;
        return `${d} дн. ${h} ч.`;
      }
    }
    const d = u.subscription_days_left ?? 0;
    const h = u.subscription_hours_left ?? 0;
    return `${d} дн. ${h} ч.`;
  };

  const hasTelegram = Boolean(user?.telegram_id || (user?.authorizations || []).some(a => a.provider === 'telegram'));
  const hasVk = Boolean(user?.vk_ids?.length || (user?.authorizations || []).some(a => a.provider === 'vk'));
  const hasYandex = Boolean(user?.yandex_email || (user?.authorizations || []).some(a => a.provider === 'yandex'));

  const currentProvider = user?.auth_provider || user?.chosen_provider || (hasVk ? 'vk' : hasYandex ? 'yandex' : 'tg');

  const displayName=fixMojibake(user?.name||user?.first_name||user?.username||user?.email||'Мой профиль');
  const accounts=user?.authorizations?.length?user.authorizations:[{provider:currentProvider,identifier:user?.yandex_email||user?.email||user?.telegram_username||user?.phone||'Аккаунт Viht'}];
  return <div className="fluent-page fluent-profile-page viht-profile-compact">
    {toast&&<div role="status" className="viht-profile-notice">{toast.message}</div>}
    <section className="fluent-card viht-profile-identity"><div className="viht-profile-avatar"><User size={25}/></div><div><small>ЛИЧНЫЙ КАБИНЕТ</small><h2>{displayName}</h2><span>{user?.email||user?.yandex_email||'Viht VPN'}</span></div></section>
    <section className="fluent-card viht-profile-sub"><div><small>ОСТАЛОСЬ ПОДПИСКИ</small><strong>{formatSubTime(user)}</strong></div><button className="btn btn--primary" onClick={()=>window.dispatchEvent(new Event('viht-open-tariffs'))}><CreditCard size={16}/>Продлить</button></section>
    <div className="viht-profile-segments"><button aria-pressed={activeTab==='accounts'} onClick={()=>setActiveTab('accounts')}>Аккаунты</button><button aria-pressed={activeTab==='payments'} onClick={()=>setActiveTab('payments')}>Платежи</button></div>
    {activeTab==='accounts'?<section className="fluent-card viht-profile-accounts">{accounts.map((account,index)=><div className="viht-profile-account" key={account.provider+':'+index}><SocialIcon type={account.provider==='telegram'?'tg':account.provider} size={26}/><div><strong>{account.title||(account.provider==='yandex'?'Яндекс ID':account.provider==='vk'?'VK ID':'Telegram')}</strong><span>{account.identifier||account.email||account.username||'Подключён'}</span></div><button type="button" className="btn btn--secondary btn--sm" onClick={()=>onSwitchProvider?.(account.provider)}>Войти</button></div>)}<button className="viht-profile-cabinet" onClick={openCabinet}>Управление аккаунтом на сайте <ExternalLink size={14}/></button></section>:<section className="fluent-card viht-profile-payments">{paymentsLoading?<p>Загружаем платежи…</p>:payments.length?payments.map((payment,index)=><div key={payment.id||index}><span>{formatSource(payment.source||payment.provider)}<small>{new Date(payment.created_at||payment.paid_at).toLocaleDateString('ru-RU')}</small></span><strong>{payment.amount||payment.price||0} ₽</strong></div>):<p>Платежей пока нет</p>}</section>}
    <section className="fluent-card viht-profile-action"><div><RotateCcw size={20}/><div><strong>Сбросить ключи</strong><p>Текущие ссылки перестанут работать. На устройствах потребуется обновить подключение.</p></div></div><button type="button" className="btn btn--secondary" disabled={resettingKeys} onClick={handleResetKeys}>{resettingKeys?'Сбрасываем…':'Сбросить ключи'}</button></section>
    <section className="fluent-card viht-profile-action viht-profile-logout"><div><LogOut size={20}/><div><strong>Выйти из аккаунта</strong><p>Завершить вход на этом устройстве. Подписка и остальные устройства сохранятся.</p></div></div><button type="button" className="btn btn--secondary" onClick={()=>{if(window.confirm('Выйти из аккаунта на этом устройстве?'))onLogout?.()}}>Выйти из аккаунта</button></section>
  </div>;
}
export default React.memo(ProfileScreen);
