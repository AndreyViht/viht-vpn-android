import {Search as ClientSearchIcon,RefreshCw as ClientRefreshIcon,ChevronRight as ClientChevron,CalendarDays as ClientCalendar,Smartphone as ClientPhone} from 'lucide-react';
import {useAdminConfirmation,AdminResultDialog} from './AdminClientFeedback.jsx';
import {useAdminSlidingPill} from './useAdminMotion.js';
import useAdminPoll from './useAdminPoll.js';
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useAdminFetch } from "./AdminFetchContext.jsx";
import "./AdminClients.css";
import "./AdminClientsGlass.css";

const USERS_PER_PAGE = 20;

function useDebounce(value, delay) {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]);

  return debouncedValue;
}

function formatTelegramHandle(username) {
  if (username == null || username === "") return null;
  const s = String(username).replace(/^@+/, "").trim();
  return s ? `@${s}` : null;
}

function fmtDate(iso) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString("ru-RU", { dateStyle: "short", timeStyle: "short" });
  } catch (_e) {
    return "—";
  }
}

const PRESET_DAYS = [7, 30, 90, 180, 365];
const PRESET_SLOTS = [1, 2, 4, 10];

function parseUserSessions(lastUa) {
  if (!lastUa) return [];
  try {
    const raw = String(lastUa).trim();
    if (raw.startsWith("[")) {
      const arr = JSON.parse(raw);
      if (Array.isArray(arr)) return arr;
    } else if (raw) {
      return [{ ua: raw, ip: "unknown", last_seen: null }];
    }
  } catch {
    return [];
  }
  return [];
}

// Clean inline SVGs for crisp minimalist rendering
function IconClock({ size = 15, className = "" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <circle cx="12" cy="12" r="10" />
      <polyline points="12 6 12 12 16 14" />
    </svg>
  );
}

function IconDevices({ size = 15, className = "" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <rect x="5" y="2" width="14" height="20" rx="3" />
      <circle cx="12" cy="18" r="1" />
    </svg>
  );
}

function IconUser({ size = 15, className = "" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  );
}

function IconCopy({ size = 13, className = "" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
    </svg>
  );
}

function IconCheck({ size = 13, className = "" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <polyline points="20 6 9 17 4 12" />
    </svg>
  );
}

function IconExternal({ size = 15, className = "" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
      <polyline points="15 3 21 3 21 9" />
      <line x1="10" y1="14" x2="21" y2="3" />
    </svg>
  );
}

function IconClose({ size = 16, className = "" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  );
}

function IconPlus({ size = 13, className = "" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </svg>
  );
}

function IconMinus({ size = 13, className = "" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <line x1="5" y1="12" x2="19" y2="12" />
    </svg>
  );
}

function IconChevronDown({ size = 14, className = "" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <polyline points="6 9 12 15 18 9" />
    </svg>
  );
}

function IconTrash({ size = 13, className = "" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
      <line x1="10" y1="11" x2="10" y2="17" />
      <line x1="14" y1="11" x2="14" y2="17" />
    </svg>
  );
}

function getPlatformIcon(os) {
  const o = String(os || "").toLowerCase();
  if (o.includes("ios") || o.includes("iphone") || o.includes("ipad")) return "🍏";
  if (o.includes("mac")) return "🍎";
  if (o.includes("android")) return "🤖";
  if (o.includes("win")) return "🪟";
  if (o.includes("linux")) return "🐧";
  return "📱";
}

function ClientEditModal({ user, onClose, onSave }) {
  useEffect(()=>{const previous=document.body.style.overflow;document.body.style.overflow='hidden';document.body.classList.add('admin-client-detail-open');return()=>{document.body.style.overflow=previous;document.body.classList.remove('admin-client-detail-open')}},[]);
  const adminFetch = useAdminFetch();
  const confirmation=useAdminConfirmation(),operationLock=useRef(false);
  const [days, setDays] = useState("30");
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");
  const [loginLoading, setLoginLoading] = useState(false);
  const [toastMsg, setToastMsg] = useState(null);
  const [successModalData, setSuccessModalData] = useState(null);

  // Notification toggles
  const [notifyDaysTg, setNotifyDaysTg] = useState(true);
  const [notifyUserTg, setNotifyUserTg] = useState(true);

  // Device slots & sessions state
  const [extraSlots, setExtraSlots] = useState(Number(user.extra_device_slots) || 0);
  const [baseSlots, setBaseSlots] = useState(Number(user.base_device_slots || user.max_devices) || 4);
  const [slotsInput, setSlotsInput] = useState("1");
  const [migrationSlots,setMigrationSlots]=useState(Number(user.migration_device_slots)||0);
  const [savingSlots, setSavingSlots] = useState(false);
  const [slotsError, setSlotsError] = useState("");

  const initialSessions = useMemo(() => parseUserSessions(user.last_ua), [user.last_ua]);
  const [deviceSessions, setDeviceSessions] = useState(initialSessions);
  const [deletingDeviceIdx, setDeletingDeviceIdx] = useState(null);

  useEffect(() => {
    setDeviceSessions(parseUserSessions(user.last_ua));
  }, [user.last_ua]);

  const activeCount = new Set(deviceSessions.filter(d=>!d.observation_only&&!d.slot_exempt).map(d=>d.physical_device_id||d.device_id||d.id)).size;
  const onlineCount = new Set(deviceSessions.filter(d=>d.online).map(d=>d.physical_device_id||d.device_id||d.id)).size;
  const totalSlots = baseSlots + extraSlots + migrationSlots;
  const isExceeded = activeCount > totalSlots;
  const daysLeft = Math.max(0, Number(user.subscription_days_left) || 0);
  const isOk = daysLeft > 0;

  const [activeTab, setActiveTab] = useState("days");
  const {containerRef:tabContainerRef,indicatorRef:tabIndicatorRef}=useAdminSlidingPill(activeTab,'.admin-cm-tab--active'); // 'days' | 'slots' | 'info'
  const [copiedKey, setCopiedKey] = useState(null);

  // Close on Escape key
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === "Escape") {
        if(operationLock.current||document.querySelector("dialog.admin-feedback-dialog[open]"))return;
        if (successModalData) {
          setSuccessModalData(null);
        } else {
          onClose();
        }
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose, successModalData]);

  const showToast = (title, sub) => {
    setToastMsg({ title, sub,kind:/ошибк|не удалось/i.test(title)?"error":"success" });
    setTimeout(() => {
      setToastMsg(null);
    }, 2800);
  };

  const copyText = async (text, key) => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(String(text));
      setCopiedKey(key);
      showToast("Скопировано", String(text));
      setTimeout(() => setCopiedKey(null), 1800);
    } catch { /* No action required. */ }
  };

  const performDeleteDevice = async (idx, s) => {
    const devName = s.device_model || s.app_name || "Устройство";
    setDeletingDeviceIdx(idx);
    try {
      const res = await adminFetch("delete_user_device", {
        target_telegram_id: Number(user.telegram_id),
        device_idx: idx,
        hwid: s.hwid,
        device_id: s.device_id,
        ua: s.ua,
      });
      if (res?.ok) {
        setDeviceSessions(res.sessions ?? deviceSessions.filter((_, i) => i !== idx));
        showToast(res.revocation_pending ? "Отзыв ключей выполняется" : "Устройство удалено", devName);
        onSave();
      } else {
        showToast("Ошибка", res?.error || "Не удалось удалить устройство");
      }
    } catch (e) {
      showToast("Ошибка сети", e.message);
    }
    setDeletingDeviceIdx(null);
  };

  const performClearAllDevices = async () => {
    setDeletingDeviceIdx(-1);
    try {
      const res = await adminFetch("delete_user_device", {
        target_telegram_id: Number(user.telegram_id),
        clear_all: true,
      });
      if (res?.ok) {
        setDeviceSessions([]);
        showToast(res.revocation_pending ? "Отзыв ключей выполняется" : "Все устройства удалены", "Нужные устройства можно восстановить в кабинете и обновить подписку.");
        onSave();
      } else {
        showToast("Ошибка", res?.error || "Не удалось очистить устройства");
      }
    } catch (e) {
      showToast("Ошибка сети", e.message);
    }
    setDeletingDeviceIdx(null);
  };

  const performSlotAction = async (mode, value) => {
    let num = Math.floor(Number(value !== undefined ? value : slotsInput));
    if (mode === "reset") {
      num = 0;
    } else if (Number.isNaN(num) || num <= 0) {
      setSlotsError("Укажите число слотов больше 0");
      return;
    }

    setSavingSlots(true);
    setSlotsError("");

    try {
      const data = await adminFetch("manage_device_slots", {
        target_telegram_id: Number(user.telegram_id),
        mode,
        slots: num,
        notify_user: notifyUserTg,
      });

      setSavingSlots(false);

      if (data?.ok) {
        setExtraSlots(data.extra_device_slots ?? 0);
        setBaseSlots(data.max_devices ?? 4);
        setMigrationSlots(data.migration_device_slots ?? 0);
        setSuccessModalData({type:"slots",totalSlots:data.total_slots,clientName:user.yandex_name||formatTelegramHandle(user.telegram_username)||`TG: ${user.telegram_id}`,notified:data.notified===true,notificationQueued:data.notification_queued===true,notificationRequested:notifyUserTg});
        showToast(
          mode === "reset"
            ? "Слоты сброшены к стандарту (4)"
            : mode === "set"
            ? `Лимит установлен: ${data.total_slots} сл.`
            : mode === "add"
            ? `Добавлено +${num} сл.`
            : `Списано -${num} сл.`,
          `Всего доступно: ${data.total_slots} устройств`
        );
        onSave();
      } else {
        setSlotsError(data?.error || "Не удалось изменить слоты");
      }
    } catch (e) {
      setSavingSlots(false);
      setSlotsError(e.message || "Ошибка сети");
    }
  };

  const performAction = async (actionType) => {
    let val = Math.floor(Number(days));
    let action = "";
    let daysArg = val;

    if (actionType === "disable") {
      action = "unpay";
      daysArg = Math.max(0, Number(user.subscription_days_left) || 0);
    } else if (actionType === "add") {
      if (!val || val <= 0) {
        setErrorMsg("Введите число дней больше 0");
        return;
      }
      action = "grant";
    } else if (actionType === "remove") {
      if (!val || val <= 0) {
        setErrorMsg("Введите число дней больше 0");
        return;
      }
      action = "unpay";
    }

    setSaving(true);
    setErrorMsg("");

    try {
      const data = await adminFetch(action, {
        target_telegram_id: Number(user.telegram_id),
        days: daysArg,
        notify_user: notifyDaysTg,
      });

      setSaving(false);

      if (data?.ok) {
        onSave();
        // Show the beautiful mobile success alert dialog
        setSuccessModalData({
          type: actionType,
          days: daysArg,
          expiresAt: data.expires_at,
          notified: data.notified===true,
          notificationRequested:notifyDaysTg,
          notificationQueued:data.notification_queued===true,
          clientName: user.yandex_name || username || `TG: ${user.telegram_id}`,
        });
      } else {
        setErrorMsg(data?.error || "Не удалось сохранить изменения");
        window.dispatchEvent(new CustomEvent('viht-admin-notice',{detail:{text:data?.error||'Не удалось сохранить изменения',type:'error'}}));
      }
    } catch (e) {
      setSaving(false);
      setErrorMsg(e.name==='TimeoutError'?'Ответ не получен. Проверьте срок клиента перед повтором: дни могли уже начислиться.':e.message || "Ошибка сети");
      window.dispatchEvent(new CustomEvent('viht-admin-notice',{detail:{text:e.name==='TimeoutError'?'Ответ задержался. Проверьте срок клиента; не повторяйте начисление сразу.':e.message||'Ошибка сети',type:'error'}}));
    }
  };

  const handleGenerateLogin = async () => {
    setLoginLoading(true);
    try {
      const data = await adminFetch("generate_launch_token", {
        target_telegram_id: Number(user.telegram_id)
      });
      if (data?.ok && data.cabinet_url) {
        await navigator.clipboard.writeText(data.cabinet_url).catch(() => {});
        showToast("Ссылка скопирована", "Открываем кабинет клиента...");
        window.open(data.cabinet_url, "_blank");
      } else {
        showToast("Ошибка",data?.error || "Не удалось создать ссылку для входа");
      }
    } catch (_e) {
      showToast("Ошибка сети","Не удалось открыть кабинет клиента");
    }
    setLoginLoading(false);
  };

  const clientLabel=user.yandex_name||formatTelegramHandle(user.telegram_username)||`TG: ${user.telegram_id}`;
  async function runConfirmed(options,operation){if(operationLock.current)return;operationLock.current=true;try{if(await confirmation.ask({...options,client:clientLabel}))await operation()}finally{operationLock.current=false}}
  const handleAction=type=>{const value=Math.floor(Number(days));if(type!=='disable'&&(!value||value<=0)){setErrorMsg('Введите число дней больше 0');return;}return runConfirmed({title:type==='add'?'Выдать дни подписки?':type==='disable'?'Обнулить подписку?':'Списать дни подписки?',value:type==='disable'?'Остаток станет равен нулю':`${type==='add'?'+':'−'}${value} дней`,description:type==='disable'?'Клиент потеряет доступ по этой подписке.':'Проверьте клиента и количество дней перед сохранением.',danger:type!=='add',confirmLabel:type==='add'?'Выдать дни':type==='disable'?'Обнулить подписку':'Списать дни',note:notifyDaysTg?'Запросить уведомление клиенту в Telegram.':'Без уведомления клиента.'},()=>performAction(type));};
  const handleSlotAction=(mode,value)=>{const number=Math.floor(Number(value!==undefined?value:slotsInput));if(mode!=='reset'&&(!number||number<=0)){setSlotsError('Укажите число слотов больше 0');return;}return runConfirmed({title:mode==='set'?'Установить точный лимит?':mode==='reset'?'Сбросить лимит устройств?':mode==='add'?'Добавить слоты?':'Списать слоты?',value:mode==='reset'?'Стандартный лимит':mode==='set'?`${number} сл.`:`${mode==='add'?'+':'−'}${number} сл.`,description:`Сейчас доступно ${totalSlots} слотов. Изменение затронет лимит этого клиента.`,danger:mode!=='add',confirmLabel:'Изменить лимит',note:notifyUserTg?'Запросить уведомление клиенту в Telegram.':'Без уведомления клиента.'},()=>performSlotAction(mode,value));};
  const handleDeleteDevice=(idx,device)=>runConfirmed({title:'Удалить устройство?',value:device.device_model||device.app_name||'Устройство',description:'Доступ устройства будет отозван. Остальные устройства останутся в подписке.',danger:true,confirmLabel:'Отключить и удалить'},()=>performDeleteDevice(idx,device));
  const handleClearAllDevices=()=>runConfirmed({title:'Удалить все устройства?',description:'Будут отключены все активные устройства этого клиента. Действие требует повторного подключения устройств.',danger:true,confirmLabel:'Отключить все'},performClearAllDevices);

  const username = formatTelegramHandle(user.telegram_username);
  const phones = user.yandex_phones && user.yandex_phones.length > 0
    ? user.yandex_phones
    : (user.yandex_phone ? [user.yandex_phone] : []);

  const emails = user.emails && user.emails.length > 0
    ? user.emails
    : (user.yandex_emails && user.yandex_emails.length > 0
        ? user.yandex_emails
        : (user.yandex_email ? [user.yandex_email] : []));

  return (
    <>
      {createPortal(
        <div className="admin-cm-overlay clients-glass-overlay" onClick={()=>{if(!operationLock.current)onClose()}}>
          <div className="admin-cm-card clients-glass-sheet" data-mode={activeTab} role="dialog" aria-modal="true" aria-label="Карточка клиента" onClick={(e) => e.stopPropagation()}>
            {/* iOS Pull indicator for mobile bottom sheet */}
            <div className="admin-cm-pull-bar" />

            {/* Header: User avatar + info + Quick action buttons */}
            <div className="admin-cm-head">
              <div className="admin-cm-user-preview">
                <div className="admin-client-avatar admin-cm-avatar">
                  {user.yandex_avatar_url ? (
                    <><span aria-hidden="true">{(user.yandex_name||user.telegram_username||"К").replace(/^@/,"").charAt(0).toUpperCase()}</span><img src={user.yandex_avatar_url} alt="" className="admin-client-avatar-img" loading="lazy" decoding="async" onError={event=>{event.currentTarget.style.display="none"}}/></>
                  ) : (
                    <span>{user.yandex_name ? user.yandex_name.charAt(0).toUpperCase() : (username ? username.charAt(1).toUpperCase() : "👤")}</span>
                  )}
                  <span className={`admin-cm-status-dot ${isOk ? "admin-cm-status-dot--active" : "admin-cm-status-dot--expired"}`} />
                </div>
                <div className="admin-cm-user-meta">
                  <div className="admin-cm-user-title">
                    {user.yandex_name || username || `TG: ${user.telegram_id}`}
                  </div>
                  <div
                    className="admin-cm-user-sub"
                    onClick={() => copyText(user.yandex_name&&username?username:user.telegram_id, "user_sub")}
                    title="Нажмите, чтобы скопировать"
                  >
                    <span>{user.yandex_name&&username ? `@${username.replace(/^@+/, "")}` : `ID: ${user.telegram_id}`}</span>
                    {copiedKey === "user_sub" ? <IconCheck size={11} /> : <IconCopy size={11} />}
                  </div>
                  {user.linked_telegrams && user.linked_telegrams.length > 0 && (
                    <div className="admin-cm-linked-tgs-row">
                      {user.linked_telegrams.map((lt) => (
                        <span
                          key={lt.telegram_id}
                          className="admin-cm-linked-tg-chip"
                          onClick={() => copyText(lt.telegram_username || lt.telegram_id, `lt_${lt.telegram_id}`)}
                          title={`Доп. Telegram аккаунт ID: ${lt.telegram_id}`}
                        >
                          +TG: {lt.telegram_username ? `@${lt.telegram_username.replace(/^@+/, "")}` : lt.telegram_id}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div className="admin-cm-head-actions">
                <button
                  type="button"
                  className="admin-cm-icon-btn"
                  onClick={handleGenerateLogin}
                  disabled={loginLoading}
                  title="Войти в кабинет клиента"
                >
                  {loginLoading ? <span className="admin-cm-spinner" /> : <IconExternal size={15} />}
                </button>
                <button data-viht-close="true" aria-label="Закрыть"
                  type="button"
                  className="admin-cm-close-btn"
                  onClick={()=>{if(!operationLock.current)onClose()}}
                  title="Закрыть"
                >
                  <IconClose size={15} />
                </button>
              </div>
            </div>

            {/* Single iOS-style Segmented Tab Bar with Live Badges */}
            <div className="admin-client-summary">
              <div><span><ClientCalendar size={14}/> Подписка</span><strong>{daysLeft}<small> дн.</small></strong><em>{user.subscription_expires_at?'До '+fmtDate(user.subscription_expires_at):isOk?'Активна':'Неактивна'}</em></div>
              <div><span><ClientPhone size={14}/> Устройства</span><strong>{activeCount}<small> / {totalSlots}</small></strong><em className={isExceeded?"is-warning":""}>{isExceeded?"Лимит превышен":`${Math.max(0,totalSlots-activeCount)} свободных слотов`}</em></div>
            </div>
            <div ref={tabContainerRef} className="admin-cm-tabs admin-motion-track">
              <span ref={tabIndicatorRef} className="admin-motion-indicator" aria-hidden="true"/>
              <button
                type="button"
                className={`admin-cm-tab${activeTab === "days" ? " admin-cm-tab--active" : ""}`}
                onClick={() => setActiveTab("days")}
              >
                <IconClock size={13} />
                <span>Подписка</span>
              </button>
              <button
                type="button"
                className={`admin-cm-tab${activeTab === "slots" ? " admin-cm-tab--active" : ""}`}
                onClick={() => setActiveTab("slots")}
              >
                <IconDevices size={13} />
                <span>Устройства</span>
              </button>
              <button
                type="button"
                className={`admin-cm-tab${activeTab === "info" ? " admin-cm-tab--active" : ""}`}
                onClick={() => setActiveTab("info")}
              >
                <IconUser size={13} />
                <span>Контакты</span>
              </button>
            </div>

            {/* TAB 1: Subscription Days */}
            {activeTab === "days" && (
              <div className="admin-cm-tab-pane">
                {/* Modern Status Plate */}
                <div className="admin-cm-status-card">
                  <div className="admin-cm-status-main">
                    <span className="admin-cm-status-label">Текущий остаток</span>
                    <div className="admin-cm-status-val-wrap">
                      <span className={`admin-cm-status-val ${isOk ? "text-emerald" : "text-rose"}`}>
                        {daysLeft}
                      </span>
                      <span className="admin-cm-status-unit">дн.</span>
                    </div>
                  </div>
                  <div className="admin-cm-status-side">
                    <span className={`admin-cm-pill-status ${isOk ? "admin-cm-pill-status--ok" : "admin-cm-pill-status--off"}`}>
                      {isOk ? "Активна" : "Истекла"}
                    </span>
                    <span className="admin-cm-status-sub">
                      {user.subscription_expires_at ? `до ${fmtDate(user.subscription_expires_at)}` : "Не оплачен"}
                    </span>
                  </div>
                </div>

                {/* Preset Chips */}
                <div className="admin-cm-chips-wrap">
                  <div className="admin-cm-chips-row">
                    {PRESET_DAYS.map((d) => (
                      <button
                        key={d}
                        type="button"
                        className={`admin-cm-chip${String(days) === String(d) ? " admin-cm-chip--active" : ""}`}
                        onClick={() => setDays(String(d))}
                        disabled={saving}
                      >
                        +{d}д
                      </button>
                    ))}
                  </div>
                </div>

                {/* Stepper Row with Tactile Buttons */}
                <div className="admin-cm-stepper-row">
                  <button
                    type="button"
                    className="admin-cm-stepper-btn"
                    onClick={() => setDays(String(Math.max(1, (Number(days) || 0) - 5)))}
                    disabled={saving}
                    title="Уменьшить на 5"
                  >
                    −5
                  </button>
                  <button
                    type="button"
                    className="admin-cm-stepper-btn admin-cm-stepper-btn--micro"
                    onClick={() => setDays(String(Math.max(1, (Number(days) || 0) - 1)))}
                    disabled={saving}
                    title="Уменьшить на 1"
                  >
                    <IconMinus size={13} />
                  </button>
                  <div className="admin-cm-input-wrap">
                    <input
                      type="number"
                      inputMode="numeric"
                      pattern="[0-9]*"
                      min="1"
                      className="admin-cm-input-compact"
                      placeholder="0"
                      value={days}
                      onChange={(e) => setDays(e.target.value)}
                      disabled={saving}
                    />
                    <span className="admin-cm-input-suffix">дн.</span>
                  </div>
                  <button
                    type="button"
                    className="admin-cm-stepper-btn admin-cm-stepper-btn--micro"
                    onClick={() => setDays(String((Number(days) || 0) + 1))}
                    disabled={saving}
                    title="Прибавить 1"
                  >
                    <IconPlus size={13} />
                  </button>
                  <button
                    type="button"
                    className="admin-cm-stepper-btn"
                    onClick={() => setDays(String((Number(days) || 0) + 5))}
                    disabled={saving}
                    title="Прибавить 5"
                  >
                    +5
                  </button>
                </div>

                {/* Telegram Notification Switch for Days Top-Up */}
                <label className="admin-cm-switch-row">
                  <span className="admin-cm-switch-label">Уведомить клиента в Telegram</span>
                  <div className="admin-cm-switch">
                    <input
                      type="checkbox"
                      checked={notifyDaysTg}
                      onChange={(e) => setNotifyDaysTg(e.target.checked)}
                      disabled={saving}
                    />
                    <span className="admin-cm-switch-slider" />
                  </div>
                </label>

                {/* Action Buttons */}
                <div className="admin-cm-actions-grid">
                  <button
                    type="button"
                    className="admin-cm-btn admin-cm-btn--grant"
                    onClick={() => handleAction("add")}
                    disabled={saving || !days || Number(days) <= 0}
                  >
                    <IconPlus size={14} />
                    <span>{saving ? "Сохранение..." : `Выдать (+${days || 0}д)`}</span>
                  </button>

                  <button
                    type="button"
                    className="admin-cm-btn admin-cm-btn--unpay"
                    onClick={() => handleAction("remove")}
                    disabled={saving || !days || Number(days) <= 0}
                  >
                    <IconMinus size={14} />
                    <span>{saving ? "Сохранение..." : `Списать (-${days || 0}д)`}</span>
                  </button>
                </div>

                {errorMsg && (
                  <div className="admin-cm-error" role="alert">
                    ⚠️ {errorMsg}
                  </div>
                )}

                {/* Subtle Reset Link */}
                <button
                  type="button"
                  className="admin-cm-link-btn"
                  onClick={() => handleAction("disable")}
                  disabled={saving}
                >
                  Обнулить подписку (сбросить в 0 дней)
                </button>
              </div>
            )}

            {/* TAB 2: Device Slots & Active Sessions */}
            {activeTab === "slots" && (
              <div className="admin-cm-tab-pane">
                {/* Visual Capacity Meter Card */}
                <div className="admin-cm-meter-card">
                  <div className="admin-cm-meter-head">
                    <span className="admin-cm-meter-title">
                      В сети: <strong>{onlineCount}</strong> из <strong>{totalSlots}</strong> устройств
                    </span>
                    {isExceeded && <span className="admin-cm-pill-warn">Превышен лимит!</span>}
                  </div>

                  {/* Progress bar */}
                  <div className="admin-cm-meter-bar-track">
                    <div
                      className={`admin-cm-meter-bar-fill ${isExceeded ? "admin-cm-meter-bar-fill--exceeded" : extraSlots > 0 ? "admin-cm-meter-bar-fill--extra" : ""}`}
                      style={{ width: `${Math.min(100, Math.max(10, (activeCount / (totalSlots || 1)) * 100))}%` }}
                    />
                  </div>

                  <div className="admin-cm-meter-meta">
                    <span>Базовых: {baseSlots}</span>
                    <span className="admin-cm-meter-extra">Дополнительно: +{extraSlots}</span>
                  </div>
                </div>

                {/* Preset Chips */}
                <div className="admin-cm-chips-wrap">
                  <div className="admin-cm-chips-row">
                    {PRESET_SLOTS.map((s) => (
                      <button
                        key={s}
                        type="button"
                        className="admin-cm-chip"
                        onClick={() => {
                          setSlotsInput(String(s));
                          handleSlotAction("add", s);
                        }}
                        disabled={savingSlots}
                      >
                        +{s} {s === 1 ? "слот" : s < 5 ? "слота" : "слотов"}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Stepper Row */}
                <div className="admin-cm-stepper-row">
                  <button
                    type="button"
                    className="admin-cm-stepper-btn admin-cm-stepper-btn--micro"
                    onClick={() => setSlotsInput(String(Math.max(1, (Number(slotsInput) || 0) - 1)))}
                    disabled={savingSlots}
                  >
                    <IconMinus size={13} />
                  </button>
                  <div className="admin-cm-input-wrap">
                    <input
                      type="number"
                      inputMode="numeric"
                      pattern="[0-9]*"
                      min="1"
                      className="admin-cm-input-compact"
                      placeholder="Кол-во слотов"
                      value={slotsInput}
                      onChange={(e) => setSlotsInput(e.target.value)}
                      disabled={savingSlots}
                    />
                    <span className="admin-cm-input-suffix">сл.</span>
                  </div>
                  <button
                    type="button"
                    className="admin-cm-stepper-btn admin-cm-stepper-btn--micro"
                    onClick={() => setSlotsInput(String((Number(slotsInput) || 0) + 1))}
                    disabled={savingSlots}
                  >
                    <IconPlus size={13} />
                  </button>
                </div>

                {/* Action Buttons */}
                <div className="admin-cm-actions-grid">
                  <button
                    type="button"
                    className="admin-cm-btn admin-cm-btn--slot-add"
                    onClick={() => handleSlotAction("add")}
                    disabled={savingSlots || !slotsInput || Number(slotsInput) <= 0}
                  >
                    <IconPlus size={13} />
                    <span>{savingSlots ? "..." : `Добавить (+${slotsInput || 0})`}</span>
                  </button>

                  <button
                    type="button"
                    className="admin-cm-btn admin-cm-btn--slot-remove"
                    onClick={() => handleSlotAction("remove")}
                    disabled={savingSlots || !slotsInput || Number(slotsInput) <= 0 || extraSlots <= 0}
                  >
                    <IconMinus size={13} />
                    <span>{savingSlots ? "..." : `Списать (-${slotsInput || 0})`}</span>
                  </button>
                </div>

                {/* Direct exact limit setter */}
                <button
                  type="button"
                  className="admin-cm-btn admin-cm-btn--slot-set"
                  onClick={() => handleSlotAction("set")}
                  disabled={savingSlots || !slotsInput || Number(slotsInput) <= 0}
                >
                  <span>Задать точный лимит = {slotsInput || 0} слотов</span>
                </button>

                {extraSlots > 0 && (
                  <button
                    type="button"
                    className="admin-cm-link-btn"
                    onClick={() => handleSlotAction("reset")}
                    disabled={savingSlots}
                  >
                    Сбросить доп. слоты к стандарту (4)
                  </button>
                )}

                {/* Modern iOS Switch Row */}
                <label className="admin-cm-switch-row">
                  <span className="admin-cm-switch-label">Уведомить клиента в Telegram</span>
                  <div className="admin-cm-switch">
                    <input
                      type="checkbox"
                      checked={notifyUserTg}
                      onChange={(e) => setNotifyUserTg(e.target.checked)}
                    />
                    <span className="admin-cm-switch-slider" />
                  </div>
                </label>

                {slotsError && (
                  <div className="admin-cm-error" role="alert">
                    ⚠️ {slotsError}
                  </div>
                )}

                {/* Active Connected Devices List with Delete/Revoke Option */}
                <div className="admin-cm-devices-wrap">
                  <div className="admin-cm-devices-header">
                    <span className="admin-cm-devices-title">
                      📱 Устройства клиента ({deviceSessions.length})
                    </span>
                    {deviceSessions.length > 1 && (
                      <button
                        type="button"
                        className="admin-cm-devices-clear-all"
                        onClick={handleClearAllDevices}
                        disabled={deletingDeviceIdx !== null}
                      >
                        Очистить все
                      </button>
                    )}
                  </div>

                  {deviceSessions.length > 0 ? (
                    <div className="admin-cm-devices-list">
                      {deviceSessions.map((s, idx) => (
                        <div key={idx} className="admin-cm-device-item">
                          <div className="admin-cm-device-left">
                            <span className="admin-cm-device-os-icon">
                              {getPlatformIcon(s.device_os || s.platform)}
                            </span>
                            <div className="admin-cm-device-info">
                              <div className="admin-cm-device-name">
                                <span>{s.device_model || s.platform || "Устройство"}</span>
                                {s.app_name && (
                                  <span className="admin-cm-app-tag">
                                    {s.app_name}
                                  </span>
                                )}
                              </div>
                              <div className="admin-cm-device-meta">
                                {s.ip && <span>IP: {s.ip}</span>}
                                {s.last_seen && <span>• {fmtDate(s.last_seen)}</span>}
                              </div>
                            </div>
                          </div>

                          <button
                            type="button"
                            className="admin-cm-device-del-btn"
                            onClick={() => handleDeleteDevice(idx, s)}
                            disabled={deletingDeviceIdx === idx}
                            title="Отключить и удалить устройство"
                          >
                            {deletingDeviceIdx === idx ? (
                              <span className="admin-cm-spinner-micro" />
                            ) : (
                              <IconTrash size={13} />
                            )}
                          </button>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="admin-cm-devices-empty">
                      Нет подключенных устройств
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* TAB 3: Info & Contacts */}
            {activeTab === "info" && (
              <div className="admin-cm-tab-pane">
                <div className="admin-cm-info-list">
                  <div className="admin-cm-info-row" onClick={() => copyText(user.telegram_id, "tg")}>
                    <span className="admin-cm-info-label">Telegram ID</span>
                    <div className="admin-cm-info-val-wrap">
                      <code>{user.telegram_id}</code>
                      <button type="button" className="admin-cm-copy-btn" title="Скопировать">
                        {copiedKey === "tg" ? <IconCheck size={12} /> : <IconCopy size={12} />}
                      </button>
                    </div>
                  </div>

                  {username && (
                    <div className="admin-cm-info-row" onClick={() => copyText(username, "user_meta")}>
                      <span className="admin-cm-info-label">Основной TG</span>
                      <div className="admin-cm-info-val-wrap">
                        <span className="text-cyan">@{username.replace(/^@+/, "")}</span>
                        <button type="button" className="admin-cm-copy-btn" title="Скопировать">
                          {copiedKey === "user_meta" ? <IconCheck size={12} /> : <IconCopy size={12} />}
                        </button>
                      </div>
                    </div>
                  )}

                  {user.linked_telegrams && user.linked_telegrams.map((lt) => (
                    <div
                      key={lt.telegram_id}
                      className="admin-cm-info-row"
                      onClick={() => copyText(lt.telegram_username || lt.telegram_id, `lt_info_${lt.telegram_id}`)}
                    >
                      <span className="admin-cm-info-label">Доп. Telegram</span>
                      <div className="admin-cm-info-val-wrap">
                        <span className="text-cyan">{lt.telegram_username ? `@${lt.telegram_username.replace(/^@+/, "")}` : lt.telegram_id}</span>
                        <button type="button" className="admin-cm-copy-btn" title="Скопировать">
                          {copiedKey === `lt_info_${lt.telegram_id}` ? <IconCheck size={12} /> : <IconCopy size={12} />}
                        </button>
                      </div>
                    </div>
                  ))}

                  {phones.length > 0 && (
                    <div className="admin-cm-info-row" onClick={() => copyText(phones[0], "phone")}>
                      <span className="admin-cm-info-label">Телефон</span>
                      <div className="admin-cm-info-val-wrap">
                        <span>{phones.join(", ")}</span>
                        <button type="button" className="admin-cm-copy-btn" title="Скопировать">
                          {copiedKey === "phone" ? <IconCheck size={12} /> : <IconCopy size={12} />}
                        </button>
                      </div>
                    </div>
                  )}

                  {emails.length > 0 && (
                    <div className="admin-cm-info-row" onClick={() => copyText(emails[0], "email")}>
                      <span className="admin-cm-info-label">Email</span>
                      <div className="admin-cm-info-val-wrap">
                        <span title={emails.join(", ")}>{emails[0]}</span>
                        <button type="button" className="admin-cm-copy-btn" title="Скопировать">
                          {copiedKey === "email" ? <IconCheck size={12} /> : <IconCopy size={12} />}
                        </button>
                      </div>
                    </div>
                  )}

                  <div className="admin-cm-info-row">
                    <span className="admin-cm-info-label">Окончание</span>
                    <span className="admin-cm-info-val">
                      {fmtDate(user.subscription_expires_at)}
                    </span>
                  </div>

                  <div className="admin-cm-info-row">
                    <span className="admin-cm-info-label">Статус</span>
                    <span className="admin-cm-info-val" style={{ textTransform: "uppercase", fontSize: 11, fontWeight: 700, color: isOk ? "#34d399" : "#f87171" }}>
                      {user.status || "активен"}
                    </span>
                  </div>
                </div>

                <button
                  type="button"
                  className="admin-cm-btn admin-cm-btn--cabinet-full"
                  onClick={handleGenerateLogin}
                  disabled={loginLoading}
                >
                  {loginLoading ? <span className="admin-cm-spinner" /> : <IconExternal size={15} />}
                  <span>{loginLoading ? "Генерация ссылки..." : "Войти в кабинет клиента"}</span>
                </button>
              </div>
            )}
          </div>
        </div>,
        document.body
      )}

      {/* Floating Dynamic Island Style Toast */}
      {toastMsg && createPortal(
        <div className="admin-cm-toast" role={toastMsg.kind === "error" ? "alert" : "status"}>
          <div className={"admin-cm-toast-pill "+(toastMsg.kind==='error'?'is-error':'')}>
            <span className="admin-cm-toast-icon">{toastMsg.kind==='error'?'!':'✓'}</span>
            <div className="admin-cm-toast-content">
              <span className="admin-cm-toast-title">{toastMsg.title}</span>
              {toastMsg.sub && <span className="admin-cm-toast-sub">{toastMsg.sub}</span>}
            </div>
          </div>
        </div>,
        document.body
      )}

      {confirmation.dialog}
      <AdminResultDialog data={successModalData} onClose={()=>setSuccessModalData(null)}/>
    </>
  );
}

export default function AdminClients() {
  const adminFetch = useAdminFetch();
  const [users, setUsers] = useState([]);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const [q, setQ] = useState("");
  const [statusFilter, setStatusFilter] = useState("all"); // 'all' | 'paid' | 'trial' | 'zero' | 'remnawave'
  const limit = USERS_PER_PAGE;
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedUser, setSelectedUser] = useState(null);
  const closingClient=useRef(false),closeGeneration=useRef(0);

  const debouncedQ = useDebounce(q, 400);
  const requestVersion=useRef(0);

  const fetchUsers = useCallback(async (searchQuery, newOffset) => {
    const version=++requestVersion.current;
    setLoading(true);
    setError(null);
    try {
      const response = await adminFetch("users", { q: searchQuery, limit, offset: newOffset, status_filter:statusFilter });
      if(version!==requestVersion.current)return;
      if (response?.ok) {
        setUsers(response.users || []);
        setTotal(response.total || 0);
        setOffset(newOffset);
      } else {
        setError(response?.error || "Не удалось загрузить список клиентов");
      }
    } catch (e) {
      if(version===requestVersion.current)setError(e.message || "Ошибка сети");
    }
    if(version===requestVersion.current)setLoading(false);
  }, [adminFetch, limit, statusFilter]);

  useEffect(() => {
    fetchUsers(debouncedQ, 0);
  }, [debouncedQ, fetchUsers]);
  useAdminPoll(()=>fetchUsers(debouncedQ,offset));
  useEffect(()=>{setSelectedUser(previous=>previous ? users.find(u=>u.id===previous.id)||previous : previous)},[users]);

  const handleNextPage = () => {
    if (offset + limit < total) {
      fetchUsers(q, offset + limit);
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  };

  const handlePrevPage = () => {
    if (offset - limit >= 0) {
      fetchUsers(q, offset - limit);
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  };

  const handleUserClick = (user) => {
    closeGeneration.current++;closingClient.current=false;setSelectedUser(user);
  };

  const handleCloseModal = () => {
    if(closingClient.current)return;
    const card=document.querySelector('.admin-cm-card'),overlay=document.querySelector('.admin-cm-overlay');
    if(!card||!card.animate||window.matchMedia('(prefers-reduced-motion: reduce)').matches){setSelectedUser(null);return;}
    closingClient.current=true;const generation=++closeGeneration.current;card.inert=true;
    const animations=[card.animate([{opacity:1,transform:getComputedStyle(card).transform},{opacity:0,transform:'translate3d(0,12px,0) scale(.985)'}],{duration:180,easing:'ease-in',fill:'forwards'})];
    if(overlay)animations.push(overlay.animate([{opacity:1},{opacity:0}],{duration:180,fill:'forwards'}));
    Promise.allSettled(animations.map(animation=>animation.finished)).then(()=>{if(generation===closeGeneration.current){setSelectedUser(null);closingClient.current=false}});
  };

  const handleSaveDays = () => {
    fetchUsers(q, offset);
  };

  // Filter users by subscription status
  const displayedUsers = users;

  return (
    <div className="admin-clients-page clients-glass">
      {/* 1. Header Bar */}
      <div className="admin-clients-head">
        <div className="admin-clients-head__left">
          <span className="clients-glass-kicker">КАТАЛОГ VIHT ID</span><h1 className="admin-clients-head__title">
            Клиентская база</h1>
        </div>

        <button
          type="button"
          className="admin-clients-btn-refresh"
          onClick={() => fetchUsers(q, offset)}
          disabled={loading}
          title="Обновить список" aria-label={loading ? "Список обновляется" : "Обновить список"}
        >
          <ClientRefreshIcon size={17} className={loading ? "admin-clients-spin" : ""}/>
          <span>{loading ? "Загрузка..." : "Обновить"}</span>
        </button>
      </div>

      {/* 2. Controls: Search + Status Filter Chips */}
      <div className="admin-clients-controls">
        <div className="admin-clients-search-row">
          <span className="admin-clients-search-icon"><ClientSearchIcon size={18}/></span>
          <input
            type="search"
            placeholder="Поиск по ID, username, телефону, почте..."
            className="admin-clients-search-input"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          {q && (
            <button
              type="button"
              className="admin-clients-search-clear"
              onClick={() => setQ("")}
              title="Очистить"
            >
              ✕
            </button>
          )}
        </div>

        <div className="admin-clients-filter-capsule">
          <button
            type="button"
            className={`admin-clients-filter-btn${statusFilter === "all" ? " admin-clients-filter-btn--active" : ""}`}
            onClick={() => setStatusFilter("all")}
          >
            Все
          </button>
          <button type="button" className={`admin-clients-filter-btn${statusFilter === "remnawave" ? " admin-clients-filter-btn--active" : ""}`} onClick={() => setStatusFilter("remnawave")}>Remnawave</button>
          <button
            type="button"
            className={`admin-clients-filter-btn${statusFilter === "paid" ? " admin-clients-filter-btn--active" : ""}`}
            onClick={() => setStatusFilter("paid")}
          >
            Платные
          </button>
          <button
            type="button"
            className={`admin-clients-filter-btn${statusFilter === "trial" ? " admin-clients-filter-btn--active" : ""}`}
            onClick={() => setStatusFilter("trial")}
          >
            Триал
          </button>
          <button
            type="button"
            className={`admin-clients-filter-btn${statusFilter === "zero" ? " admin-clients-filter-btn--active" : ""}`}
            onClick={() => setStatusFilter("zero")}
          >
            Истекли
          </button>
          <button
            type="button"
            className={`admin-clients-filter-btn${statusFilter === "exceeded" ? " admin-clients-filter-btn--active" : ""}`}
            onClick={() => setStatusFilter("exceeded")}
          >
            Лимит превышен
          </button>
        </div>
      </div>

      {error && (
        <div className="clients-glass-error" role="alert">{error}
        </div>
      )}

      <section className="client-directory" aria-label="Каталог клиентов" aria-busy={loading}>
        <header className="client-directory-heading">
          <h2>Каталог <span className="client-directory-total">{total} пользователей</span></h2>
          <span>{displayedUsers.length} записей · стр. {Math.floor(offset / limit) + 1}</span>
        </header>
        <div className="client-directory-grid">
          {displayedUsers.map(user => {
            const days = Math.max(0, Number(user.subscription_days_left) || 0);
            const handle = formatTelegramHandle(user.telegram_username);
            const name = user.yandex_name || handle || user.yandex_email || 'TG ' + user.telegram_id;
            const contact = user.yandex_email || user.yandex_phones?.[0] || user.yandex_phone || handle || 'TG ' + user.telegram_id;
            const devices = user.active_devices_count ?? parseUserSessions(user.last_ua).length;
            const slots = (Number(user.base_device_slots || user.max_devices) || 4) + (Number(user.extra_device_slots) || 0);
            return (
              <button type="button" key={user.telegram_id} className="client-directory-entry" onClick={() => handleUserClick(user)}>
                <span className="client-directory-person">
                  <span className="client-directory-avatar" aria-hidden="true">
                    {name.replace(/^@/, '').slice(0, 1).toUpperCase()}
                    {user.yandex_avatar_url && <img src={user.yandex_avatar_url} alt="" loading="lazy" onError={event => {event.currentTarget.hidden = true;}} />}
                  </span>
                  <span className="client-directory-identity"><strong>{name}</strong><small>{contact}</small></span>
                  <ClientChevron size={18} aria-hidden="true" />
                </span>
                <span className="client-directory-stats">
                  <span><small>Остаток</small><strong data-tone={days === 0 ? 'expired' : days <= 5 ? 'warning' : undefined}>{days ? days + ' дн.' : 'Истекла'}</strong></span>
                  <span><small>Устройства</small><strong data-tone={devices > slots ? 'warning' : undefined}>{devices} / {slots}</strong></span>
                  <span><small>Remnawave</small><strong>{user.remnawave ? user.remnawave_username || '#' + user.subscription_id : '—'}</strong></span>
                </span>
              </button>
            );
          })}
        </div>
        {!displayedUsers.length && <p className="client-directory-empty" role="status">
          {loading ? 'Загружаем клиентов…' : error ? 'Не удалось загрузить каталог.' : q ? 'По этому запросу ничего не найдено.' : 'В этой категории пока нет клиентов.'}
        </p>}
      </section>

      {/* 4. Pagination */}
      <div className="admin-clients-pagination">
        <button
          type="button"
          className="admin-clients-page-btn"
          onClick={handlePrevPage}
          disabled={offset === 0 || loading}
        >
          ← Назад
        </button>
        <span className="admin-clients-page-info">
          Стр. {Math.floor(offset / limit) + 1} из {Math.ceil(total / limit) || 1}
        </span>
        <button
          type="button"
          className="admin-clients-page-btn"
          onClick={handleNextPage}
          disabled={offset + limit >= total || loading}
        >
          Вперед →
        </button>
      </div>

      {/* 5. Client Detail / Edit Modal */}
      {selectedUser && (
        <ClientEditModal
          user={selectedUser}
          onClose={handleCloseModal}
          onSave={handleSaveDays}
        />
      )}
    </div>
  );
}
