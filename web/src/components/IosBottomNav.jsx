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

function IosBottomNav({
  activeTab,
  onTabChange,
  lang = 'ru',
}) {
  const buttons = useRef([]);
  const gesture = useRef(null);
  const island = useRef(null);
  const tabs = [
    { id: 'home', label: t('nav_home', lang), icon: Shield },
    { id: 'servers', label: t('nav_servers', lang), icon: Globe },
    { id: 'devices', label: t('nav_devices', lang), icon: Smartphone },
    { id: 'profile', label: t('nav_profile', lang), icon: User },
    { id: 'settings', label: t('nav_settings', lang), icon: Settings },
  ];

  return (
    <nav className="ios-bottom-bar" aria-label={lang === 'en' ? 'Main navigation' : 'Основная навигация'}>
      <div ref={island} className="ios-bottom-bar__island" style={{touchAction:'none'}}
        onPointerDown={event=>{if(event.pointerType==='mouse'&&event.button!==0)return;const index=tabs.findIndex(tab=>tab.id===activeTab);gesture.current={id:event.pointerId,index};event.currentTarget.setPointerCapture(event.pointerId);}}
        onPointerMove={event=>{if(gesture.current?.id!==event.pointerId)return;const rect=island.current.getBoundingClientRect();const index=Math.max(0,Math.min(tabs.length-1,Math.floor((event.clientX-rect.left)/rect.width*tabs.length)));if(index!==gesture.current.index){gesture.current.index=index;onTabChange(tabs[index].id);}}}
        onPointerUp={event=>{if(gesture.current?.id!==event.pointerId)return;const rect=island.current.getBoundingClientRect();const index=Math.max(0,Math.min(tabs.length-1,Math.floor((event.clientX-rect.left)/rect.width*tabs.length)));onTabChange(tabs[index].id);gesture.current=null;}}
        onPointerCancel={()=>{gesture.current=null;}}
        onKeyDown={(event) => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        const index = tabs.findIndex(tab => tab.id === activeTab);
        const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1
          : (index + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
        onTabChange(tabs[next].id);
        buttons.current[next]?.focus();
      }}>
        <span className="viht-nav-lens" aria-hidden="true" style={{transform:`translateX(${Math.max(0,tabs.findIndex(tab=>tab.id===activeTab))*100}%)`}}/>
        {tabs.map((tab, index) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;

          return (
            <motion.button data-motion-press="true"
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

export default React.memo(IosBottomNav);
