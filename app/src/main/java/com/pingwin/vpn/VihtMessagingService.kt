package com.pingwin.vpn

import androidx.work.*
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class VihtMessagingService:FirebaseMessagingService(){
    override fun onNewToken(token:String){FirebasePush.sync(applicationContext)}
    override fun onMessageReceived(message:RemoteMessage){
        val id=message.data["announcement_id"]?:return
        if(!Regex("[a-fA-F0-9-]{36}").matches(id))return
        // Resolve the current audience through our API; never trust a notification body from the transport.
        val received=runCatching{runBlocking{withTimeout(8000){AppAnnouncements.refresh(applicationContext,true).optBoolean("ok")}}}.getOrDefault(false)
        if(!received){val work=OneTimeWorkRequestBuilder<AnnouncementWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();WorkManager.getInstance(applicationContext).enqueueUniqueWork("viht-announcements-push",ExistingWorkPolicy.KEEP,work)}
    }
}
