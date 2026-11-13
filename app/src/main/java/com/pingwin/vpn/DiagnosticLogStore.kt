package com.pingwin.vpn
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Bounded in-memory ring; batched disk writes never run on the UI thread. */
object DiagnosticLogStore {
 private const val MAX_ENTRIES=1200
 private val lock=Any();private val ring=java.util.ArrayDeque<String>();private var app:Context?=null;private var dirty=false;private var dropped=0;private var bucket=0L;private var count=0
 private val worker=Executors.newSingleThreadScheduledExecutor { task -> Thread(task,"viht-log-writer").apply{isDaemon=true} }
 init { worker.scheduleWithFixedDelay({runCatching{flush()}},2,2,TimeUnit.SECONDS) }
 private fun prefs(c:Context)=c.getSharedPreferences("pingwin_diagnostic_log",Context.MODE_PRIVATE)
 private fun initialize(c:Context){if(app==null){app=c.applicationContext;prefs(c).getString("entries","").orEmpty().lineSequence().filter{it.isNotBlank()}.take(MAX_ENTRIES).forEach{ring.addLast(it)}}}
 fun isDetailedEnabled(context:Context)=prefs(context).getBoolean("detailed_enabled",false)
 fun setDetailedEnabled(context:Context,enabled:Boolean){prefs(context).edit().putBoolean("detailed_enabled",enabled).apply();append(context,"Detailed diagnostics: $enabled")}
 fun append(context:Context,message:String){synchronized(lock){initialize(context);val second=System.currentTimeMillis()/1000;if(second!=bucket){bucket=second;count=0;if(dropped>0){ring.addLast("Diagnostic rate limit: $dropped entries omitted");dropped=0}};if(++count>80){dropped++;return};val clean=DiagnosticLogRedactor.redact(message.take(2500));if(clean.isNotBlank()){ring.addLast(SimpleDateFormat("dd.MM HH:mm:ss.SSS",Locale.US).format(Date())+"  "+clean);while(ring.size>MAX_ENTRIES)ring.removeFirst();dirty=true}}}
 fun appendDetailed(context:Context,message:String){if(isDetailedEnabled(context))append(context,message)}
 fun appendDetailedBatch(context:Context,messages:List<String>){if(isDetailedEnabled(context))messages.takeLast(80).forEach{append(context,it)}}
 fun entries(context:Context):List<String> = synchronized(lock){initialize(context);ring.toList()}
 fun clear(context:Context){synchronized(lock){initialize(context);ring.clear();dirty=true};worker.execute{flush()}}
 private fun flush(){val snapshot=synchronized(lock){val c=app;if(!dirty||c==null)return;dirty=false;Pair(c,ring.joinToString("\n").takeLast(400000))};prefs(snapshot.first).edit().putString("entries",snapshot.second).apply()}
}
