import {useCallback,useSyncExternalStore} from 'react';

let latest=null;
const listeners=new Set();
export function publishTelemetry(value) {
  latest=value;
  for(const listener of [...listeners]) listener();
}
const subscribe=listener=>{listeners.add(listener);return()=>listeners.delete(listener)};
export function useTelemetryValue(field) {
  const snapshot=useCallback(()=>field==='total'?(latest?.bytesSent||0)+(latest?.bytesReceived||0):Number(latest?.[field])||0,[field]);
  return useSyncExternalStore(subscribe,snapshot,snapshot);
}
