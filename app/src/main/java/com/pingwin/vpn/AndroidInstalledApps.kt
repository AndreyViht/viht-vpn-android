package com.pingwin.vpn
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
object AndroidInstalledApps {
 fun list(context:Context):JSONArray {
  val pm=context.packageManager
  val entries=pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS).filter { it.packageName!=context.packageName }.sortedWith(compareBy({it.loadLabel(pm).toString().lowercase()},{it.packageName}))
  return JSONArray().apply { entries.forEach { info -> put(JSONObject().put("packageName",info.packageName).put("label",runCatching{info.loadLabel(pm).toString()}.getOrDefault(info.packageName)).put("system",info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM!=0).put("enabled",info.enabled)) } }
 }
 fun icon(context:Context,pkg:String):String {
  require(pkg.matches(Regex("[A-Za-z0-9_.]+")))
  val drawable=context.packageManager.getApplicationIcon(pkg);val bitmap=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888)
  drawable.setBounds(0,0,48,48);drawable.draw(Canvas(bitmap));val output=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
  return "data:image/png;base64,"+Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP)
 }
}
