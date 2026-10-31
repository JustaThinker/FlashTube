package com.thinker.flashtube.potoken

import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import com.thinker.flashtube.FlashTubeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class PoTokenGenerator {
  private val TAG = "PoTokenGenerator"

  private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
  private var webViewBadImpl = false // whether the system has a bad WebView implementation

  private val webPoTokenGenLock = Mutex()
  private var webPoTokenSessionId: String? = null
  private var webPoTokenStreamingPot: String? = null
  private var webPoTokenGenerator: PoTokenWebView? = null

  fun initialize() {
    if (!webViewSupported || webViewBadImpl) return
    GlobalScope.launch(Dispatchers.Main) {
      try {
        webPoTokenGenLock.withLock {
          if (webPoTokenGenerator == null) {
            Log.d(TAG, "Pre-initializing PoTokenWebView in background...")
            webPoTokenSessionId = "init-" + System.currentTimeMillis()
            webPoTokenGenerator =
              PoTokenWebView.getNewPoTokenGenerator(FlashTubeApplication.appContext)
            webPoTokenStreamingPot = webPoTokenGenerator!!.generatePoToken(webPoTokenSessionId!!)
          }
        }
      } catch (e: Throwable) {
        Log.e(TAG, "Failed to pre-initialize PoTokenWebView", e)
      }
    }
  }

  fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
    Log.d(TAG, "getWebClientPoToken called: videoId=$videoId, sessionId=$sessionId")
    if (!webViewSupported || webViewBadImpl) {
      return null
    }

    if (Looper.myLooper() == Looper.getMainLooper()) {
      Log.w(TAG, "getWebClientPoToken called on Main thread; skipping to avoid deadlock")
      return null
    }

    return try {
      Log.d(TAG, "Calling runBlocking to generate poToken (timeout=${POTOKEN_TIMEOUT_MS}ms)...")
      runBlocking {
        withTimeout(POTOKEN_TIMEOUT_MS) {
          getWebClientPoToken(videoId, sessionId, forceRecreate = false)
        }
      }
    } catch (e: Throwable) {
      Log.w(TAG, "poToken generation failed: ${e.javaClass.simpleName}: ${e.message}")
      if (e is BadWebViewException) {
        Log.e(TAG, "Could not obtain poToken because WebView is broken", e)
        webViewBadImpl = true
      }
      cleanupWebViewAsync()
      null
    }
  }

  private fun cleanupWebViewAsync() {
    GlobalScope.launch(Dispatchers.Main) {
      try {
        webPoTokenGenLock.withLock {
          try {
            webPoTokenGenerator?.close()
          } catch (closeEx: Exception) {
            Log.e(TAG, "Exception closing PoTokenWebView during cleanup", closeEx)
          }
          webPoTokenGenerator = null
          webPoTokenStreamingPot = null
          webPoTokenSessionId = null
        }
      } catch (e: Throwable) {
        Log.e(TAG, "Error in cleanupWebViewAsync", e)
      }
    }
  }

  private companion object {
    const val POTOKEN_TIMEOUT_MS = 5_000L
  }

  private suspend fun getWebClientPoToken(
    videoId: String,
    sessionId: String,
    forceRecreate: Boolean
  ): PoTokenResult {
    Log.d(TAG, "Web poToken requested: videoId=$videoId, sessionId=$sessionId")

    val (poTokenGenerator, streamingPot, hasBeenRecreated) =
      webPoTokenGenLock.withLock {
        val shouldRecreate =
          forceRecreate ||
            webPoTokenGenerator == null ||
            webPoTokenGenerator!!.isExpired ||
            webPoTokenSessionId != sessionId

        if (shouldRecreate) {
          Log.d(TAG, "Creating new PoTokenWebView (forceRecreate=$forceRecreate)")
          webPoTokenSessionId = sessionId

          withContext(Dispatchers.Main) { webPoTokenGenerator?.close() }

          // create a new webPoTokenGenerator
          webPoTokenGenerator = PoTokenWebView.getNewPoTokenGenerator(FlashTubeApplication.appContext)

          // The streaming poToken needs to be generated exactly once before generating
          // any other (player) tokens.
          webPoTokenStreamingPot = webPoTokenGenerator!!.generatePoToken(webPoTokenSessionId!!)
          Log.d(TAG, "Streaming poToken generated for sessionId=${webPoTokenSessionId?.take(20)}...")
        }

        Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
      }

    val playerPot =
      try {
        poTokenGenerator.generatePoToken(videoId)
      } catch (throwable: Throwable) {
        if (hasBeenRecreated) {
          throw throwable
        } else {
          Log.e(TAG, "Failed to obtain poToken, retrying", throwable)
          return getWebClientPoToken(videoId = videoId, sessionId = sessionId, forceRecreate = true)
        }
      }

    Log.d(TAG, "poToken generated successfully: session=${streamingPot.take(20)}..., video=${playerPot.take(20)}...")

    return PoTokenResult(
      playerRequestPoToken = playerPot,
      streamingDataPoToken = streamingPot,
    )
  }
}
