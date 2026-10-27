package com.badger454.onethreefive

import android.app.Application
import android.content.Context
import com.badger454.onethreefive.notify.Reminders

class App : Application() {
    val model: AppModel by lazy { AppModel(this) }

    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannel(this)
    }

    companion object {
        fun model(context: Context): AppModel = (context.applicationContext as App).model
    }
}
