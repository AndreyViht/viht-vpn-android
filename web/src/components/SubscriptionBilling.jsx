import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { X, CheckCircle2, Loader2, CreditCard } from 'lucide-react';
import { fetchAppTariffs, createAppCheckout, fetchAppPaymentStatus } from '../services/api';
import './SubscriptionBilling.css';

export default function SubscriptionBilling({ token, accountId, onPaid, product = 'subscription' }) {
  const isSlots = product === 'slots';
  const storageKey = 'viht_payment_v1:' + accountId + (isSlots ? ':slots' : '');
  const [slotCount, setSlotCount] = useState(1);
  const [slotPrice, setSlotPrice] = useState(null);
  const [slotMax, setSlotMax] = useState(20);
  const [open, setOpen] = useState(false);
  const [tariffs, setTariffs] = useState([]);
  const [selected, setSelected] = useState(30);
  const [order, setOrder] = useState(() => {
    try { const data = JSON.parse(localStorage.getItem(storageKey)); return data?.label && data?.paymentUrl ? data : null; }
    catch { return null; }
  });
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [paid, setPaid] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const inFlight = useRef(false);
  const payLock = useRef(false);
  const onPaidRef = useRef(onPaid);
  onPaidRef.current = onPaid;
  const dialog = useRef(null);
  const trigger = useRef(null);

  useEffect(() => {
    const show = event => { if ((event.detail?.product || 'subscription') !== product) return; trigger.current = document.activeElement; setPaid(false); setOpen(true); };
    window.addEventListener('viht-open-tariffs', show);
    return () => window.removeEventListener('viht-open-tariffs', show);
  }, [product]);
  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true); setTariffs([]); setSlotPrice(null); setError('');
    fetchAppTariffs(token).then(result => {
      if (!active) return;
      if (!result.ok || !Array.isArray(result.data?.tariffs) || !result.data.tariffs.length) throw Error(result.error || 'Не удалось загрузить тарифы');
      setTariffs(result.data.tariffs);
      if (isSlots && (!Number.isFinite(result.data.slotPrice) || result.data.slotPrice <= 0)) throw Error('Не удалось получить стоимость слота');
      setSlotPrice(result.data.slotPrice); setSlotMax(result.data.maxSlotPurchase || 20);
    }).catch(e => { if (active) setError(e.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open, token, loadAttempt, isSlots]);
  useEffect(() => {
    if (!open) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialog.current?.focus();
    const keyboard = event => {
      if (event.key === 'Escape') { event.preventDefault(); setOpen(false); }
      if (event.key === 'Tab') {
        const items = [...dialog.current.querySelectorAll('button:not(:disabled), a[href]')];
        if (!items.length) return;
        if (event.shiftKey && (document.activeElement === items[0] || document.activeElement === dialog.current)) { event.preventDefault(); items.at(-1).focus(); }
        else if (!event.shiftKey && (document.activeElement === items.at(-1) || document.activeElement === dialog.current)) { event.preventDefault(); items[0].focus(); }
      }
    };
    document.addEventListener('keydown', keyboard);
    return () => { document.body.style.overflow = previous; document.removeEventListener('keydown', keyboard); trigger.current?.focus?.(); };
  }, [open]);
  useEffect(() => {
    if (!order?.label) return;
    let active = true;
    const check = async () => {
      if (inFlight.current || document.hidden) return;
      inFlight.current = true;
      try {
        const result = await fetchAppPaymentStatus(token, order.label);
        if (!active) return;
        if (!result.ok) { setError(result.error || 'Не удалось проверить оплату. Повторим автоматически.'); return; }
        setError('');
        if (result.data?.status === 'paid') {
          localStorage.removeItem(storageKey); localStorage.removeItem(storageKey + ':draft'); setOrder(null); setPaid(true); setOpen(true);
          onPaidRef.current?.();
        }
      } catch (e) { if (active) setError(e.message || 'Проверьте подключение к интернету'); }
      finally { inFlight.current = false; }
    };
    void check();
    const timer = setInterval(check, 10000);
    window.addEventListener('focus', check);
    document.addEventListener('visibilitychange', check);
    const off = window.electronAPI?.onAppResumed?.(check);
    return () => { active = false; clearInterval(timer); window.removeEventListener('focus', check); document.removeEventListener('visibilitychange', check); off?.(); };
  }, [order?.label, token, storageKey]);
  const pay = async () => {
    if (payLock.current) return;
    payLock.current = true; setBusy(true); setError('');
    try {
      if (!window.electronAPI?.openPayment) throw Error('Для встроенной оплаты обновите приложение Viht.');
      let checkout = order;
      if (!checkout) {
        // Retain this id before requesting checkout: a retry must open the same order.
        const draftKey = storageKey + ':draft';
        let draft;
        try { draft = JSON.parse(localStorage.getItem(draftKey)); } catch {}
        if (!draft?.requestId || (draft.product || 'subscription') !== product || (isSlots ? draft.slots !== slotCount : draft.days !== selected)) draft = { days: selected, product, slots: slotCount, requestId: crypto.randomUUID() };
        localStorage.setItem(draftKey, JSON.stringify(draft));
        const result = await createAppCheckout(token, draft.days, draft.requestId, { product, slots: draft.slots });
        if (!mounted.current) return;
        if (!result.ok || !result.data?.checkout?.label) throw Error(result.error || 'Не удалось создать оплату');
        checkout = result.data.checkout;
        localStorage.setItem(storageKey, JSON.stringify(checkout));
        setOrder(checkout); setPaid(false);
        if (checkout.price !== (isSlots ? slotCount * slotPrice : tariffs.find(t => t.days === selected)?.price)) { setError('Цена обновилась. Проверьте новую сумму и нажмите «Продолжить оплату».'); return; }
      }
      if (!mounted.current) return;
      const result = await window.electronAPI.openPayment(checkout.paymentUrl);
      if (!result?.ok) throw Error(result?.error || 'Не удалось открыть оплату');
    } catch (e) { setError(e.message || 'Не удалось открыть оплату'); }
    finally { payLock.current = false; setBusy(false); }
  };
  if (!open) return null;
  return createPortal(<div className="billing-shade" onClick={e => { if (e.target === e.currentTarget) setOpen(false); }}>
    <section className="billing-sheet" ref={dialog} role="dialog" aria-modal="true" aria-labelledby="billing-title" tabIndex={-1}>
      <header><div><span>VIHT VPN</span><h2 id="billing-title">{isSlots ? 'Дополнительные слоты' : 'Тарифы'}</h2></div><button className="billing-close" aria-label="Закрыть тарифы" onClick={() => setOpen(false)}><X size={20} /></button></header>
      {paid ? <div className="billing-success"><CheckCircle2 size={44} /><h3>{isSlots ? 'Слоты добавлены' : 'Подписка продлена'}</h3><p>{isSlots ? 'Оплата подтверждена. Обновляем лимит устройств.' : 'Оплата подтверждена. Обновляем срок подписки.'}</p><button onClick={() => setOpen(false)}>Готово</button></div> : <>
        {order ? <div className="billing-pending"><h3>{isSlots ? `Дополнительные слоты · ${order.slots}` : `Оплата · ${order.days} дней`}</h3><strong>{order.price} ₽</strong><p>Подтверждение от сервера появится автоматически. Если вы ещё не оплатили, продолжите оплату ниже.</p></div> : <>
          <p className="billing-caption">{isSlots ? 'Слоты добавляются навсегда. Срок подписки не меняется.' : 'Оплаченные дни добавятся к оставшемуся сроку.'}</p>
          {loading ? <p className="billing-loading"><Loader2 className="spinning" size={20} /> Загружаем тарифы…</p> : isSlots ? <div className="billing-slot-picker"><p>Один слот · {slotPrice} ₽</p><div><button aria-label="Уменьшить количество слотов" disabled={slotCount <= 1} onClick={() => setSlotCount(n => n - 1)}>−</button><strong aria-live="polite">{slotCount}</strong><button aria-label="Увеличить количество слотов" disabled={slotCount >= slotMax} onClick={() => setSlotCount(n => n + 1)}>+</button></div><span>Итого: {slotCount * slotPrice} ₽</span></div> : <div className="billing-tariffs" role="group" aria-label="Выбор тарифа">{tariffs.map(t => <button key={t.days} aria-pressed={selected === t.days} onClick={() => setSelected(t.days)}><span>{t.title}</span><strong>{t.price} ₽</strong>{selected === t.days && <CheckCircle2 size={18} />}</button>)}</div>}
        </>}
        {error && <p className="billing-error" role="alert">{error}</p>}
        {!order && !loading && !tariffs.length && <button className="billing-retry" onClick={() => setLoadAttempt(n => n + 1)}>Повторить загрузку</button>}
        <button className="billing-pay" disabled={busy || (!order && (loading || (isSlots ? !slotPrice : !tariffs.some(t => t.days === selected))))} onClick={pay}>{busy ? <Loader2 className="spinning" size={18} /> : <CreditCard size={18} />}{order ? 'Продолжить оплату' : `Оплатить ${isSlots ? slotCount * slotPrice : (tariffs.find(t => t.days === selected)?.price ?? '')} ₽`}</button>
        <p className="billing-footnote">Карта и другие доступные способы — в защищённой форме ЮMoney. Для подтверждения банк может открыть своё приложение.</p>
        {order && <><button className="billing-retry" onClick={() => setOpen(false)}>Проверим оплату в фоне · закрыть</button><button className="billing-retry" onClick={() => { localStorage.removeItem(storageKey); localStorage.removeItem(storageKey + ':draft'); setOrder(null); setPaid(false); }}>{isSlots ? 'Изменить количество слотов' : 'Выбрать другой тариф'}</button><p className="billing-footnote">Если уже оплатили, дождитесь подтверждения. Изменение заказа не отменяет платёж в банке.</p></>}
      </>}
    </section>
  </div>, document.body);
}
