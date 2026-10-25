import React, { useRef } from 'react';
import { motion } from 'framer-motion';
import {
  Shield,
  Globe,
  Smartphone,
  User,
  Settings,
} from 'lucide-react';
import { t } from '../utils/i18n';
import { springs } from '../utils/motion';

export default function IosBottomNav({
  activeTab,
  onTabChange,
  lang = 'ru',
}) {
  const buttons = useRef([]);
  const tabs = [
    { id: 'home', label: t('nav_home', lang), icon: Shield },
    { id: 'servers', label: t('nav_servers', lang), icon: Globe },
    { id: 'devices', label: t('nav_devices', lang), icon: Smartphone },
    { id: 'profile', label: t('nav_profile', lang), icon: User },
    { id: 'settings', label: t('nav_settings', lang), icon: Settings },
  ];

  return (
    <nav className="ios-bottom-bar" aria-label={lang === 'en' ? 'Main navigation' : 'Основная навигация'}>
      <div className="ios-bottom-bar__island" onKeyDown={(event) => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        const index = tabs.findIndex(tab => tab.id === activeTab);
        const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1
          : (index + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
        onTabChange(tabs[next].id);
        buttons.current[next]?.focus();
      }}>
        {tabs.map((tab, index) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;

          return (
            <motion.button
              ref={element => { buttons.current[index] = element; }}
              key={tab.id}
              type="button"
              className={`ios-tab-item ${isActive ? 'ios-tab-item--active' : ''}`}
              onClick={() => onTabChange(tab.id)}
              aria-current={isActive ? 'page' : undefined}
              whileHover={{ y: -2 }}
              whileTap={{ scale: 0.88, y: 1 }}
              transition={springs.tactile}
            >
              {isActive && (
                <motion.span
                  layoutId="liquid-navigation-lens"
                  className="ios-tab-item__active-pill"
                  transition={springs.pill}
                />
              )}
              <motion.span className="ios-tab-item__icon-wrap"
                animate={{ y: isActive ? -1 : 0, scale: isActive ? 1.08 : 1 }}
                transition={springs.tactile}>
                <Icon size={21} strokeWidth={isActive ? 2.1 : 1.65} className="ios-tab-item__icon" />
              </motion.span>
              <span className="ios-tab-item__label">{tab.label}</span>
            </motion.button>
          );
        })}
      </div>
    </nav>
  );
}
