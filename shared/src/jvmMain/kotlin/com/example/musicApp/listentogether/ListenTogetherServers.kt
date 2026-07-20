package com.example.musicApp.listentogether

/**
 * Servidores de retransmisión conocidos de Listen Together. Reutilizamos el servidor comunitario
 * de Metrolist para que las salas de Melodist sean interoperables con los clientes de Metrolist
 * (mismo protocolo protobuf).
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
