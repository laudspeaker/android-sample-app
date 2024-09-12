package com.laudspeaker.myapplication
import com.laudspeaker.android.LaudspeakerAndroid
import com.laudspeaker.android.LaudspeakerAndroidConfig
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single {
        val config = LaudspeakerAndroidConfig(
            this.androidContext(),
            "WjzfCfRwSXDtG3H6u72b4PGate0B1a4MDhu51j0x",
            "https://b25df60f2464.ngrok.app", //"https://app.laudspeaker.com/api",
            MainActivity::class.java // Might need to adjust based on actual usage
        )
        LaudspeakerAndroid.with(get(), config)
    }
}