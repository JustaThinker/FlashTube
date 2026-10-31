package com.thinker.flashtube

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.thinker.flashtube.data.ContentRegion
import com.thinker.flashtube.potoken.PoTokenGenerator
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

class FlashTubeApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        NewPipe.init(NewPipeDownloader.instance)

        // Initialize BotGuard PO token generator and hook it into NewPipeExtractor
        val poTokenGenerator = PoTokenGenerator()
        poTokenGenerator.initialize()
        YoutubeStreamExtractor.setPoTokenProvider(FlashtubePoTokenProvider(poTokenGenerator))

        // The iOS client only adds HLS livestream formats; Flashtube plays VOD, so skip that request.
        YoutubeStreamExtractor.setFetchIosClient(false)
        ThemeSettings.init(this)
        ContentRegion.init(this)
        QualityPreference.init(this)
        PlaybackSpeed.init(this)
        AmbientMode.init(this)
        SeekConfig.init(this)
        BackgroundPlayback.init(this)
        ResumeStore.init(this)
        Account.init(this)
        SponsorBlock.init(this)
        SearchHistory.init(this)
        Downloads.init(this)
        // Stream URLs are bound to the network they were fetched on: forget them on a switch.
        getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : android.net.ConnectivityManager.NetworkCallback() {
                private var last: android.net.Network? = null
                override fun onAvailable(network: android.net.Network) {
                    if (last != null && last != network) {
                        StreamCache.invalidateAll()
                        Net.onNetworkChanged()
                    }
                    last = network
                }
            },
        )
    }

    // Instant canvas drawing during fast list scrolling prevents painter animation tick overhead while flinging.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .build()
}
