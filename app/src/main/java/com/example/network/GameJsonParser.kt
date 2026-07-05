package com.example.network

import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object GameJsonParser {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val sessionAdapter = moshi.adapter(GameSessionState::class.java)
    private val eventAdapter = moshi.adapter(NetworkEvent::class.java)
    private val commandAdapter = moshi.adapter(ClientCommand::class.java)
    private val presetAdapter = moshi.adapter(PresetState::class.java)

    fun toJson(state: GameSessionState): String = sessionAdapter.toJson(state)
    fun fromJson(json: String): GameSessionState? = sessionAdapter.fromJson(json)

    fun eventToJson(event: NetworkEvent): String = eventAdapter.toJson(event)
    fun eventFromJson(json: String): NetworkEvent? = eventAdapter.fromJson(json)

    fun commandToJson(cmd: ClientCommand): String = commandAdapter.toJson(cmd)
    fun commandFromJson(json: String): ClientCommand? = commandAdapter.fromJson(json)

    fun presetToJson(preset: PresetState): String = presetAdapter.toJson(preset)
    fun presetFromJson(json: String): PresetState? = presetAdapter.fromJson(json)
}
