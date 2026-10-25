import React from 'react';
import { motion } from 'framer-motion';
import {
  Shield,
  Globe,
  Smartphone,
  User,
  Settings,
  HelpCircle,
  ExternalLink,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-react';

import vihtLogo from '../assets/viht-logo.png';
import { t } from '../utils/i18n';
import { springs } from '../utils/motion';

export default function NavigationSidebar({
  activeTab,
  onTabChange,
  connectionState,
  currentServer,
  user,
  onOpenHelp,
  isCollapsed = true,
  onToggleCollapse,
  lang = 'ru',
}) {
  const isConnected = connectionState === 'connected';
  const isConnecting = connectionState === 'connecting';

  const navItems = [
    { id: 'home', label: t('nav_home', lang), icon: Shield },
    { id: 'servers', label: t('nav_servers', lang), icon: Globe },
    { id: 'devices', label: t('nav_devices', lang), icon: Smartphone },
    { id: 'profile', label: t('nav_profile', lang), icon: User },
    { id: 'settings', label: t('nav_settings', lang), icon: Settings },
  ];

  const serverDisplayName = currentServer?.name?.replace(/[\uD83C][\uDDE6-\uDDFF][\uD83C][\uDDE6-\uDDFF]/g, '').trim() || 'Локация';

  return (
    <aside className={`fluent-sidebar ${isCollapsed ? 'fluent-sidebar--collapsed' : ''}`}>
      {/* Brand Header with Toggle Button */}
      <div className="fluent-sidebar__brand">
        <div className="fluent-brand-logo-wrap" title={isCollapsed ? 'Развернуть меню' : 'Viht VPN'}>
          <motion.div
            className="fluent-brand-logo"
            onClick={onToggleCollapse}
            style={{ cursor: 'pointer' }}
            title={isCollapsed ? 'Развернуть меню' : 'Свернуть меню'}
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            transition={springs.tactile}
          >
            <img src={vihtLogo} alt="Viht VPN" className="fluent-brand-logo-img" />
          </motion.div>
          {!isCollapsed && (
            <motion.div
              className="fluent-brand-info"
              initial={{ opacity: 0, x: -6 }}
              animate={{ opacity: 1, x: 0 }}
              transition={springs.natural}
            >
              <span className="fluent-brand-title">Viht VPN</span>
              <span className="fluent-brand-sub">Windows Desktop</span>
            </motion.div>
          )}
        </div>

        <motion.button
          type="button"
          className="fluent-sidebar-toggle-btn"
          onClick={onToggleCollapse}
          title={isCollapsed ? 'Развернуть меню' : 'Свернуть меню'}
          aria-label={isCollapsed ? 'Развернуть меню' : 'Свернуть меню'}
          whileHover={{ scale: 1.08 }}
          whileTap={{ scale: 0.92 }}
          transition={springs.tactile}
        >
          {isCollapsed ? <PanelLeftOpen size={16} /> : <PanelLeftClose size={16} />}
        </motion.button>
      </div>

      {/* Main Navigation List with Shared-Layout Spring Indicator */}
      <nav className="fluent-sidebar__nav">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <motion.button
              key={item.id}
              type="button"
              className={`fluent-nav-item ${isActive ? 'fluent-nav-item--active' : ''} ${isCollapsed ? 'fluent-nav-item--collapsed' : ''}`}
              onClick={() => onTabChange(item.id)}
              title={item.label}
              whileHover={{ x: isCollapsed ? 0 : 3 }}
              whileTap={{ scale: 0.96 }}
              transition={springs.tactile}
            >
              {/* Physically morphing sliding pill indicator */}
              {isActive && (
                <motion.div
                  layoutId="sidebar-active-indicator"
                  className="fluent-nav-item__active-bg"
                  transition={springs.pill}
                />
              )}

              <div className="fluent-nav-item__pill" />
              <Icon size={18} className="fluent-nav-item__icon" />
              {!isCollapsed && <span className="fluent-nav-item__label">{item.label}</span>}
            </motion.button>
          );
        })}
      </nav>

      {/* Sidebar Footer */}
      <div className="fluent-sidebar__footer">
        {/* Help button */}
        <motion.button
          type="button"
          className={`fluent-nav-item fluent-nav-item--help ${isCollapsed ? 'fluent-nav-item--collapsed' : ''}`}
          onClick={onOpenHelp}
          title="Открыть помощник по настройке help.anviht.ru"
          whileHover={{ x: isCollapsed ? 0 : 3 }}
          whileTap={{ scale: 0.96 }}
          transition={springs.tactile}
        >
          <HelpCircle size={18} className="fluent-nav-item__icon" />
          {!isCollapsed && (
            <>
              <span className="fluent-nav-item__label">{t('nav_help', lang)}</span>
              <ExternalLink size={12} className="fluent-nav-item__ext" />
            </>
          )}
        </motion.button>

        {/* Live Tunnel Mini-Status Card */}
        <motion.div
          className={`fluent-mini-status ${connectionState} ${isCollapsed ? 'fluent-mini-status--collapsed' : ''}`}
          title={isConnected ? `${t('status_protected', lang)}: ${serverDisplayName}` : isConnecting ? t('status_connecting', lang) : t('status_unprotected', lang)}
          layout
          transition={springs.natural}
        >
          <div className="fluent-mini-status__dot" />
          {!isCollapsed && (
            <div className="fluent-mini-status__text">
              <span className="fluent-mini-status__title">
                {isConnected ? (lang === 'en' ? 'Protected' : 'Защищено') : isConnecting ? (lang === 'en' ? 'Connecting' : 'Подключение') : (lang === 'en' ? 'Unprotected' : 'Не защищено')}
              </span>
              <span className="fluent-mini-status__server">
                {serverDisplayName}
              </span>
            </div>
          )}
        </motion.div>
      </div>
    </aside>
  );
}
