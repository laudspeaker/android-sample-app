package com.laudspeaker.myapplication
import com.laudspeaker.android.LaudspeakerAndroid
import com.laudspeaker.android.LaudspeakerAndroidConfig
import org.koin.dsl.module

val appModule = module {
    single {
        val config = LaudspeakerAndroidConfig(
            "jRLW4EHcKWo5mHQfRBA3VQ34pcOenhZsBbUtBHhL",
            "https://8dd0db94cec8.ngrok.app",
            MainActivity::class.java // Might need to adjust based on actual usage
        )
        LaudspeakerAndroid.with(get(), config)
    }
}