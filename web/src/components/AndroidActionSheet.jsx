import React, {useCallback, useEffect, useRef, useState} from 'react';
import './AndroidActionSheet.css';

export function AndroidActionSheet({title, children, onClose}) {
  const dialog = useRef(null);
  useEffect(() => {
    const node = dialog.current;
    if (!node.open) node.showModal();
    return () => { if (node.open) node.close(); };
  }, []);
  return <dialog ref={dialog} aria-label={title} className="viht-action-sheet"
    onClose={onClose} onClick={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section>
      <h2>{title}</h2>
      {children}
    </section>
  </dialog>;
}

export function useAndroidConfirmation() {
  const [request, setRequest] = useState(null);
  const pending = useRef(null);
  const settle = useCallback(value => {
    pending.current?.(value);
    pending.current = null;
    setRequest(null);
  }, []);
  useEffect(() => () => { pending.current?.(false); pending.current = null; }, []);
  const confirm = useCallback((title, message, action = 'Подтвердить') => {
    if (pending.current) return Promise.resolve(false);
    return new Promise(resolve => {
      pending.current = resolve;
      setRequest({title, message, action});
    });
  }, []);
  const sheet = request && <AndroidActionSheet title={request.title} onClose={() => settle(false)}>
    <p>{request.message}</p>
    <div className="viht-sheet-actions">
      <button autoFocus type="button" onClick={() => settle(false)}>Отмена</button>
      <button type="button" className="viht-sheet-primary" onClick={() => settle(true)}>{request.action}</button>
    </div>
  </AndroidActionSheet>;
  return [confirm, sheet];
}
