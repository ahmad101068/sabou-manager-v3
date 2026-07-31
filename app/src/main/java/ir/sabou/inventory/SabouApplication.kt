package ir.sabou.inventory

import android.app.Application
import ir.sabou.inventory.data.AppContainer

class SabouApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(applicationContext)
    }
}

