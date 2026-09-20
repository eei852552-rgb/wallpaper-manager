package com.wallpapermanager.app

import android.content.Context
import com.google.firebase.FirebaseApp

object FirebaseSetup {
    fun initialize(context: Context): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        return FirebaseApp.initializeApp(context) != null
    }
}
