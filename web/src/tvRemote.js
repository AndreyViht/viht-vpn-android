const controlSelector = 'button, a[href], input:not([type=hidden]), select, textarea, [tabindex="0"]';
const dialogSelector = 'dialog[open], [aria-modal="true"], .fluent-modal-overlay, .invite-overlay, .billing-shade';
const directions = {
  ArrowUp: [0, -1],
  ArrowDown: [0, 1],
  ArrowLeft: [-1, 0],
  ArrowRight: [1, 0],
};

function visible(element) {
  return element.getClientRects().length > 0 && getComputedStyle(element).visibility === 'visible';
}

function controls() {
  const dialogs = [...document.querySelectorAll(dialogSelector)].filter(visible);
  const scope = dialogs.at(-1) || document;
  return [...scope.querySelectorAll(controlSelector)].filter(element =>
    !element.matches(':disabled, [aria-disabled="true"]') &&
    !element.closest('[inert], [aria-hidden="true"]') &&
    visible(element)
  );
}

function focus(element) {
  element?.focus({ preventScroll: true });
  element?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
}

function center(element) {
  const bounds = element.getBoundingClientRect();
  return [bounds.left + bounds.width / 2, bounds.top + bounds.height / 2];
}

function initialFocus(items) {
  return items.find(element => element.matches('[data-tv-digit], .tv-home-power, .login-actions button'))
    || items[0];
}

function focusMenu() {
  const items = controls();
  focus(items.find(element => element.matches('.ios-tab-item[aria-current="page"]')) || initialFocus(items));
}

window.vihtTvRemoteKey = (key, repeat) => {
  if (key === 'Menu') {
    if (!repeat) focusMenu();
    return;
  }
  if (key === 'Enter') {
    if (repeat) return;
    const items = controls();
    const active = document.activeElement;
    if (!items.includes(active)) focus(initialFocus(items));
    else active.click();
    return;
  }
  document.activeElement.dispatchEvent(new KeyboardEvent('keydown', { key, repeat, bubbles: true, cancelable: true }));
};

document.addEventListener('keydown', event => {
  const direction = directions[event.key];
  const items = controls();
  const active = document.activeElement;
  if (/^\d$/.test(event.key)) {
    const digit = items.find(item => item.dataset.tvDigit === event.key);
    if (digit) {
      event.preventDefault();
      if (!event.repeat) digit.click();
      return;
    }
  }
  if (event.key === 'Enter' && active.matches('.invite-pin-input')) {
    const submit = active.closest('.invite-card')?.querySelector('.invite-primary:not(:disabled)');
    if (submit && !event.repeat) {
      event.preventDefault();
      submit.click();
    }
    return;
  }
  if (event.key === 'Enter' && !items.includes(active)) {
    event.preventDefault();
    focus(initialFocus(items));
    return;
  }
  if (!direction || event.isComposing) return;
  if (event.isTrusted && active.matches('input, textarea, select') && direction[0]) return;
  event.preventDefault();
  event.stopPropagation();
  if (!items.includes(active)) {
    focus(initialFocus(items));
    return;
  }

  const [x, y] = center(active);
  const [dx, dy] = direction;
  let best;
  let bestScore = Infinity;
  for (const item of items) {
    if (item === active) continue;
    const [itemX, itemY] = center(item);
    const forward = (itemX - x) * dx + (itemY - y) * dy;
    if (forward <= 3) continue;
    const sideways = Math.abs((itemX - x) * dy + (itemY - y) * dx);
    const score = forward + sideways * 3;
    if (score < bestScore) {
      best = item;
      bestScore = score;
    }
  }
  focus(best);
}, true);

let focusFrame;
let loginScreen;
const observer = new MutationObserver(() => {
  if (focusFrame) return;
  focusFrame = requestAnimationFrame(() => {
    focusFrame = null;
    const items = controls();
    const nextLoginScreen = document.querySelector('.login-actions');
    if ((nextLoginScreen && nextLoginScreen !== loginScreen) || !items.includes(document.activeElement)) {
      focus(initialFocus(items));
    }
    loginScreen = nextLoginScreen;
  });
});
observer.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['disabled', 'open', 'aria-hidden'] });
