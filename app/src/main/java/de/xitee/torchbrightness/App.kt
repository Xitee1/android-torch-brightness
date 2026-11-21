package de.xitee.torchbrightness

import android.app.Application
import de.xitee.torchbrightness.curve.CurveReader
import de.xitee.torchbrightness.data.Prefs

class App : Application() {
    val prefs by lazy { Prefs(this) }
    val curveReader by lazy { CurveReader(this) }

    override fun onCreate() {
        super.onCreate()
        curveReader.start()
    }
}
