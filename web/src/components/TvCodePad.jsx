import React from 'react';

const digits = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

export default function TvCodePad({ code, onChange }) {
  return (
    <div>
      <output className="tv-code-display" aria-live="polite" aria-label="Код подписки">
        {code.padEnd(6, '•')}
      </output>
      <div className="tv-code-pad" role="group" aria-label="Цифровая клавиатура">
        {digits.map(digit => (
          <button type="button" key={digit} data-tv-digit={digit} onClick={() => onChange(value => (value + digit).slice(0, 6))}>
            {digit}
          </button>
        ))}
        <button type="button" className="tv-code-delete" onClick={() => onChange(value => value.slice(0, -1))}>Стереть</button>
      </div>
    </div>
  );
}
