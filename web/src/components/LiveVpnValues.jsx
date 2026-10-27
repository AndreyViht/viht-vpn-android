import React,{useEffect,useState} from 'react';
import {useTelemetryValue} from '../services/telemetry';

function formatSpeed(num) {
  if(num<=0)return '0.0 MB/s';
  if(num<.05)return `${Math.round(num*1024)} KB/s`;
  return `${num.toFixed(1)} MB/s`;
}
function formatBytes(bytes) {
  if(bytes<=0)return '0 MB';
  if(bytes<1048576)return `${(bytes/1024).toFixed(1)} KB`;
  if(bytes<1073741824)return `${(bytes/1048576).toFixed(1)} MB`;
  return `${(bytes/1073741824).toFixed(2)} GB`;
}
export const TransferValue=React.memo(function TransferValue({field}) {
  const value=useTelemetryValue(field);
  return field==='total'?formatBytes(value):formatSpeed(value);
});
export const SessionTimer=React.memo(function SessionTimer({connectedAt}) {
  const duration=useTelemetryValue('duration');
  const [now,setNow]=useState(Date.now);
  useEffect(()=>{
    let timer;
    const update=()=>setNow(Date.now());
    const visibility=()=>{clearInterval(timer);if(!document.hidden){update();timer=setInterval(update,1000)}};
    visibility();document.addEventListener('visibilitychange',visibility);
    return()=>{clearInterval(timer);document.removeEventListener('visibilitychange',visibility)};
  },[]);
  const seconds=duration>0?duration:connectedAt?Math.max(0,Math.floor((now-connectedAt)/1000)):0;
  const hours=Math.floor(seconds/3600),minutes=Math.floor(seconds%3600/60),remaining=Math.floor(seconds%60);
  const pair=value=>String(value).padStart(2,'0');
  return hours>0?`${pair(hours)}:${pair(minutes)}:${pair(remaining)}`:`${pair(minutes)}:${pair(remaining)}`;
});
