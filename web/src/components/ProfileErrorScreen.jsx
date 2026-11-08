import { AlertCircle, RefreshCw, LogOut } from 'lucide-react';

export default function ProfileErrorScreen({ error, onRetry, onLogout }) {
  return (
    <div className="no-sub-screen">
      <div className="no-sub-card">
        <AlertCircle size={36} />
        <h2 className="no-sub-title">Не удалось загрузить профиль</h2>
        <p className="no-sub-desc">{error}</p>
        <div className="no-sub-secondary-actions">
          <button className="btn btn--primary" onClick={onRetry}><RefreshCw size={14} />Повторить</button>
          <button className="btn btn--secondary" onClick={onLogout}><LogOut size={14} />Выйти</button>
        </div>
      </div>
    </div>
  );
}
