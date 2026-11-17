package com.blackpool2026.muse

import android.app.Application
import com.blackpool2026.muse.data.Covers
import com.blackpool2026.muse.data.Store

class MuseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Covers.init(this)
    }
}
