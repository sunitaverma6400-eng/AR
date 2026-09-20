package com.ar.messenger

import android.app.Application
import com.google.firebase.FirebaseApp

class ARApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
