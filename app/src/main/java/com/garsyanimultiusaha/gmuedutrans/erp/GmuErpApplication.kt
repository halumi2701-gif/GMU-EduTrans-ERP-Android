package com.garsyanimultiusaha.gmuedutrans.erp

import android.app.Application

class GmuErpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PushNotifications.initialize(this)
    }
}
