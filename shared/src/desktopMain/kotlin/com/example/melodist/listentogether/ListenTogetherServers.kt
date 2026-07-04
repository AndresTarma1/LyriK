package com.example.melodist.listentogether

/**
 * Known Listen Together relay servers. We reuse Metrolist's community server so Melodist rooms
 * are interoperable with Metrolist clients (same protobuf protocol).
 */
data class ListenTogetherServer(
    val name: String,
    val url: String,
    val location: String,
    val operator: String,
)

object ListenTogetherServers {
    val servers: List<ListenTogetherServer> = listOf(
        ListenTogetherServer(
            name = "The Meowery",
            url = "wss://metroserverx.meowery.eu/ws",
            location = "Poland",
            operator = "Nyx",
        ),
    )

    val defaultServerUrl: String get() = servers.first().url

    fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }
}
