package com.laudspeaker.myapplication
import com.laudspeaker.android.LaudspeakerAndroid
import com.laudspeaker.android.LaudspeakerAndroidConfig
import org.koin.dsl.module

val appModule = module {
    single {
        val config = LaudspeakerAndroidConfig(
            "WI1ltLvwUY5LiRd39UV7uRylDdk9FlBA73vYfDHJ",
            "https://b4766abc1095.ngrok.app",
            null//MainActivity::class.java // Might need to adjust based on actual usage
        )
        LaudspeakerAndroid.with(get(), config)
    }
}