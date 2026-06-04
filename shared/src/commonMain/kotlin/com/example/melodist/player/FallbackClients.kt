package com.example.melodist.player

import com.metrolist.innertube.models.YouTubeClient

object FallbackClients {
    val mainClient: YouTubeClient = YouTubeClient.WEB_REMIX

    val streamFallbackClients: Array<YouTubeClient> = arrayOf(
        YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        YouTubeClient.TVHTML5,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_CREATOR,
        YouTubeClient.IPADOS,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.MOBILE,
        YouTubeClient.WEB,
        YouTubeClient.IOS,
        YouTubeClient.WEB_CREATOR,
    )
}
