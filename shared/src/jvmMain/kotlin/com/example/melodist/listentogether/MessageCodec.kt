package com.example.melodist.listentogether

import io.github.aakira.napier.Napier
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Codifica/decodifica mensajes de Escuchar Juntos como [Envelope] de protobuf,
 * replicando el `MessageCodec` de Metrolist. Usa kotlinx-serialization-protobuf
 * (sin clases generadas por protoc).
 */
@OptIn(ExperimentalSerializationApi::class)
class MessageCodec(
    var compressionEnabled: Boolean = false,
) {
    companion object {
        @PublishedApi
        internal const val COMPRESSION_THRESHOLD = 100 // solo comprimir payloads mayores a este valor

        @PublishedApi
        internal val protobuf = ProtoBuf { encodeDefaults = false }
    }

    /** Envuelve [payload] (o null) en un [Envelope] de [type] y retorna el frame codificado. */
    inline fun <reified T> encode(type: String, payload: T?): ByteArray {
        var payloadBytes = if (payload != null) protobuf.encodeToByteArray(payload) else ByteArray(0)
        var compressed = false

        if (compressionEnabled && payloadBytes.size > COMPRESSION_THRESHOLD) {
            val gz = gzip(payloadBytes)
            if (gz.size < payloadBytes.size) {
                payloadBytes = gz
                compressed = true
            }
        }

        return protobuf.encodeToByteArray(Envelope(type = type, payload = payloadBytes, compressed = compressed))
    }

    /** Decodifica el envelope externo, retornando (tipo, bytes-del-payload-descomprimido). */
    fun decode(data: ByteArray): Pair<String, ByteArray> {
        val envelope = protobuf.decodeFromByteArray<Envelope>(data)
        val payloadBytes = if (envelope.compressed) gunzip(envelope.payload) ?: envelope.payload else envelope.payload
        return envelope.type to payloadBytes
    }

    /** Decodifica el payload interno para un tipo de mensaje conocido [type]. */
    fun decodePayload(type: String, payloadBytes: ByteArray): Any? {
        if (payloadBytes.isEmpty()) return null
        return when (type) {
            MessageTypes.ROOM_CREATED -> protobuf.decodeFromByteArray<RoomCreatedPayload>(payloadBytes)
            MessageTypes.JOIN_REQUEST -> protobuf.decodeFromByteArray<JoinRequestPayload>(payloadBytes)
            MessageTypes.JOIN_APPROVED -> protobuf.decodeFromByteArray<JoinApprovedPayload>(payloadBytes)
            MessageTypes.JOIN_REJECTED -> protobuf.decodeFromByteArray<JoinRejectedPayload>(payloadBytes)
            MessageTypes.USER_JOINED -> protobuf.decodeFromByteArray<UserJoinedPayload>(payloadBytes)
            MessageTypes.USER_LEFT -> protobuf.decodeFromByteArray<UserLeftPayload>(payloadBytes)
            MessageTypes.SYNC_PLAYBACK -> protobuf.decodeFromByteArray<PlaybackActionPayload>(payloadBytes)
            MessageTypes.BUFFER_WAIT -> protobuf.decodeFromByteArray<BufferWaitPayload>(payloadBytes)
            MessageTypes.BUFFER_COMPLETE -> protobuf.decodeFromByteArray<BufferCompletePayload>(payloadBytes)
            MessageTypes.ERROR -> protobuf.decodeFromByteArray<ErrorPayload>(payloadBytes)
            MessageTypes.HOST_CHANGED -> protobuf.decodeFromByteArray<HostChangedPayload>(payloadBytes)
            MessageTypes.KICKED -> protobuf.decodeFromByteArray<KickedPayload>(payloadBytes)
            MessageTypes.SYNC_STATE -> protobuf.decodeFromByteArray<SyncStatePayload>(payloadBytes)
            MessageTypes.RECONNECTED -> protobuf.decodeFromByteArray<ReconnectedPayload>(payloadBytes)
            MessageTypes.USER_RECONNECTED -> protobuf.decodeFromByteArray<UserReconnectedPayload>(payloadBytes)
            MessageTypes.USER_DISCONNECTED -> protobuf.decodeFromByteArray<UserDisconnectedPayload>(payloadBytes)
            else -> null
        }
    }

    @PublishedApi
    internal fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray? =
        try {
            GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        } catch (e: Exception) {
            Napier.e("[LT] Error al descomprimir el payload", e)
            null
        }
}
