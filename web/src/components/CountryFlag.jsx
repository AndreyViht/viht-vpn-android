import React from 'react';

export default function CountryFlag({ country = 'DE', className = '' }) {
  const code = (country || '').toUpperCase();

  // 🇩🇪 Germany
  if (code === 'DE' || code.includes('🇩🇪') || code.includes('GERMANY')) {
    return (
      <svg
        className={`country-flag-svg ${className}`}
        viewBox="0 0 24 16"
        width="24"
        height="16"
        style={{ borderRadius: '3.5px', overflow: 'hidden', display: 'block' }}
        xmlns="http://www.w3.org/2000/svg"
      >
        <rect width="24" height="5.33" y="0" fill="#141414" />
        <rect width="24" height="5.34" y="5.33" fill="#E61C24" />
        <rect width="24" height="5.33" y="10.67" fill="#FFCF00" />
        <rect width="24" height="16" fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth="1" rx="3.5" />
      </svg>
    );
  }

  // 🇳🇱 Netherlands
  if (code === 'NL' || code.includes('🇳🇱') || code.includes('NETHERLANDS')) {
    return (
      <svg
        className={`country-flag-svg ${className}`}
        viewBox="0 0 24 16"
        width="24"
        height="16"
        style={{ borderRadius: '3.5px', overflow: 'hidden', display: 'block' }}
        xmlns="http://www.w3.org/2000/svg"
      >
        <rect width="24" height="5.33" y="0" fill="#AD1D25" />
        <rect width="24" height="5.34" y="5.33" fill="#FFFFFF" />
        <rect width="24" height="5.33" y="10.67" fill="#1E4785" />
        <rect width="24" height="16" fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth="1" rx="3.5" />
      </svg>
    );
  }

  // 🇸🇪 Sweden
  if (code === 'SE' || code.includes('🇸🇪') || code.includes('SWEDEN')) {
    return (
      <svg
        className={`country-flag-svg ${className}`}
        viewBox="0 0 24 16"
        width="24"
        height="16"
        style={{ borderRadius: '3.5px', overflow: 'hidden', display: 'block' }}
        xmlns="http://www.w3.org/2000/svg"
      >
        <rect width="24" height="16" fill="#005B99" />
        <rect width="3.2" height="16" x="7.2" fill="#FECC00" />
        <rect width="24" height="3.2" y="6.4" fill="#FECC00" />
        <rect width="24" height="16" fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth="1" rx="3.5" />
      </svg>
    );
  }

  // 🇷🇺 Russia
  if (code === 'RU' || code.includes('🇷🇺') || code.includes('RUSSIA')) {
    return (
      <svg
        className={`country-flag-svg ${className}`}
        viewBox="0 0 24 16"
        width="24"
        height="16"
        style={{ borderRadius: '3.5px', overflow: 'hidden', display: 'block' }}
        xmlns="http://www.w3.org/2000/svg"
      >
        <rect width="24" height="5.33" y="0" fill="#FFFFFF" />
        <rect width="24" height="5.34" y="5.33" fill="#0039A6" />
        <rect width="24" height="5.33" y="10.67" fill="#D52B1E" />
        <rect width="24" height="16" fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth="1" rx="3.5" />
      </svg>
    );
  }

  // 🌐 Default / LTE / Global
  return (
    <svg
      className={`country-flag-svg ${className}`}
      viewBox="0 0 24 16"
      width="24"
      height="16"
      style={{ borderRadius: '3.5px', overflow: 'hidden', display: 'block' }}
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <linearGradient id="globe-grad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#0d2b45" />
          <stop offset="100%" stopColor="#203c56" />
        </linearGradient>
      </defs>
      <rect width="24" height="16" fill="url(#globe-grad)" />
      <circle cx="12" cy="8" r="5.2" fill="none" stroke="#00ff88" strokeWidth="1.2" opacity="0.9" />
      <ellipse cx="12" cy="8" rx="2.4" ry="5.2" fill="none" stroke="#00ff88" strokeWidth="1" opacity="0.8" />
      <line x1="6.8" y1="8" x2="17.2" y2="8" stroke="#00ff88" strokeWidth="1" opacity="0.8" />
      <rect width="24" height="16" fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth="1" rx="3.5" />
    </svg>
  );
}
