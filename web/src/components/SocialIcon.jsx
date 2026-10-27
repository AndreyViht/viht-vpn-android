import React from 'react';

/**
 * Аутентичные, ультра-четкие векторные иконки / эмодзи соцсетей (Yandex ID, VK ID, Telegram)
 * Поддерживают динамический размер (size), форму (shape: 'circle' | 'squircle')
 */
export default function SocialIcon({ type = 'yandex', size = 24, className = '', shape = 'squircle' }) {
  const normalized = (type || '').toLowerCase();
  const radius = shape === 'circle' ? 12 : 6;
  const isCircle = shape === 'circle';

  // 1. YANDEX ID (Официальный алый круг / сквиркл с фирменной литерой «Я»)
  if (normalized === 'yandex' || normalized === 'ya' || normalized === 'custom:yandex') {
    return (
      <svg
        viewBox="0 0 24 24"
        width={size}
        height={size}
        className={`social-icon social-icon--yandex ${className}`}
        style={{
          borderRadius: isCircle ? '50%' : '26%',
          display: 'inline-block',
          verticalAlign: 'middle',
          flexShrink: 0,
        }}
      >
        <defs>
          <linearGradient id="viht-ya-grad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#FF3333" />
            <stop offset="100%" stopColor="#E50000" />
          </linearGradient>
        </defs>
        <rect width="24" height="24" rx={radius} fill="url(#viht-ya-grad)" />
        {/* Фирменная литера «Я» Яндекса */}
        <path
          fillRule="evenodd"
          clipRule="evenodd"
          fill="#FFFFFF"
          d="M14.33 5.33h-2.87c-2.8 0-4.47 1.6-4.47 3.87 0 1.93 1.2 3.2 3.13 3.73l-3.47 6.4h2.87l3.2-6.07h.27v6.07h2.6V5.33h-1.26zm-1.07 5.67h-1.27c-1.4 0-2.13-.67-2.13-1.8 0-1.13.73-1.8 2.13-1.8h1.27v3.6z"
        />
      </svg>
    );
  }

  // 2. VK ID (Официальный синий сквиркл с фирменной лигатурой VK)
  if (normalized === 'vk' || normalized === 'custom:vk') {
    return (
      <svg
        viewBox="0 0 24 24"
        width={size}
        height={size}
        className={`social-icon social-icon--vk ${className}`}
        style={{
          borderRadius: isCircle ? '50%' : '26%',
          display: 'inline-block',
          verticalAlign: 'middle',
          flexShrink: 0,
        }}
      >
        <defs>
          <linearGradient id="viht-vk-grad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#0077FF" />
            <stop offset="100%" stopColor="#0066EE" />
          </linearGradient>
        </defs>
        <rect width="24" height="24" rx={radius} fill="url(#viht-vk-grad)" />
        {/* Фирменный знак ВКонтакте */}
        <path
          fill="#FFFFFF"
          d="M12.68 16.53c-4.73 0-7.47-3.26-7.6-8.67h2.4c.09 4 1.8 5.67 3.2 6.07V7.86h2.27v3.47c1.4-.13 2.8-1.73 3.27-3.47h2.27c-.4 2.13-2 3.73-3.13 4.4 1.13.53 2.93 1.93 3.6 4.33h-2.47c-.53-1.87-2.07-3.27-4-3.47v3.47h-.81z"
        />
      </svg>
    );
  }

  // 3. TELEGRAM (Официальный лазурный круг с фирменным белым бумажным самолётиком)
  if (normalized === 'telegram' || normalized === 'tg') {
    return (
      <svg
        viewBox="0 0 24 24"
        width={size}
        height={size}
        className={`social-icon social-icon--tg ${className}`}
        style={{
          borderRadius: isCircle ? '50%' : '26%',
          display: 'inline-block',
          verticalAlign: 'middle',
          flexShrink: 0,
        }}
      >
        <defs>
          <linearGradient id="viht-tg-grad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#2AABEE" />
            <stop offset="100%" stopColor="#229ED9" />
          </linearGradient>
        </defs>
        <rect width="24" height="24" rx={radius} fill="url(#viht-tg-grad)" />
        {/* Фирменный самолётик Telegram */}
        <path
          fill="#FFFFFF"
          d="M16.64 8.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.75-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .38z"
        />
      </svg>
    );
  }

  // Дефолтный значок кабинета Viht
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      className={`social-icon social-icon--default ${className}`}
      style={{
        borderRadius: isCircle ? '50%' : '26%',
        display: 'inline-block',
        verticalAlign: 'middle',
        flexShrink: 0,
      }}
    >
      <rect width="24" height="24" rx={radius} fill="#00ff88" />
      <path
        d="M12 6.5a5.5 5.5 0 100 11 5.5 5.5 0 000-11zm-.7 3h1.4v3.5h-1.4zm0 4.9h1.4v1.4h-1.4z"
        fill="#07090e"
      />
    </svg>
  );
}
