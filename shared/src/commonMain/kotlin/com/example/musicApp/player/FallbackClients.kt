package com.example.musicApp.player

import com.metrolist.innertube.models.YouTubeClient

object FallbackClients {
    // WEB_REMIX is used ONLY for metadata (audioConfig/videoDetails); its stream is skipped
    // because, with poTokens disabled, web streams 403 and would needlessly load player.js.
    val mainClient: YouTubeClient = YouTubeClient.WEB_REMIX

    // Only non-web clients: they return direct/NewPipe URLs (no poToken, no player.js/cipher).
    // VISIONOS first (no `spc` throttle gate). Anything none of these can play falls back to yt-dlp.
    val streamFallbackClients: Array<YouTubeClient> = arrayOf(
        YouTubeClient.VISIONOS,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.IOS,
        YouTubeClient.IPADOS,
        YouTubeClient.ANDROID_CREATOR,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.MOBILE,
    )
}
