import React from 'react';
import {ArrowDown, ArrowUp} from 'lucide-react';
import {useTelemetryValue} from '../services/telemetry';
import {deviceState} from '../services/devices';

function formatRate(bytes) {
  if (bytes < 1024) return Math.round(bytes) + ' Б/с';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' КБ/с';
  return (bytes / 1048576).toFixed(1) + ' МБ/с';
}

function Rates({down, up, label}) {
  return <div className="device-live-rate" title={label}>
    <span><ArrowDown size={11}/>{formatRate(down)}</span>
    <span><ArrowUp size={11}/>{formatRate(up)}</span>
  </div>;
}

function CurrentRate() {
  const down = useTelemetryValue('speedDown');
  const up = useTelemetryValue('speedUp');
  return <Rates down={Math.max(0,down)*1048576} up={Math.max(0,up)*1048576} label="Скорость этого VPN-подключения"/>;
}

export const DeviceRate = React.memo(function DeviceRate({device,current,observedAt}) {
  if (deviceState(device) !== 'online') return <small className="device-live-unavailable">Скорость · —</small>;
  if (current) return <CurrentRate/>;
  const at = Date.parse(device.traffic_sampled_at || '');
  const down = device.download_bps;
  const up = device.upload_bps;
  const fresh = Number.isFinite(at) && observedAt >= at - 5000 && observedAt - at <= 45000;
  if (!fresh || !Number.isFinite(down) || !Number.isFinite(up)) return <small className="device-live-unavailable">Ожидаем замер скорости</small>;
  return <Rates down={Math.max(0,down)} up={Math.max(0,up)} label={'Замер в ' + new Date(at).toLocaleTimeString('ru-RU')}/>;
});

