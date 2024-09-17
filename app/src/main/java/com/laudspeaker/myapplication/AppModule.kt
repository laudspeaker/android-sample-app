package com.laudspeaker.myapplication
import com.laudspeaker.android.LaudspeakerAndroid
import com.laudspeaker.android.LaudspeakerAndroidConfig
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single {
        val config = LaudspeakerAndroidConfig(
            this.androidContext(),
            "FkyTRWAcnb6wENW5Qcc9bm7ro0KU57oCcTOt2oCq",
            "https://3d2bc2089b9a.ngrok.app",//"https://app.laudspeaker.com/api",
            MainActivity::class.java // Might need to adjust based on actual usage
        )
        LaudspeakerAndroid.with(get(), config)
    }
}