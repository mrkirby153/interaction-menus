package com.mrkirby153.interactionsmenu.testbot

import com.mrkirby153.botcore.coroutine.enableCoroutines
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder

private val log = KotlinLogging.logger { }

fun main() {
    val token = System.getenv("BOT_TOKEN")?.trim()
    requireNotNull(token) { "Token must be provided" }

    log.info { "Starting up..." }
    val shardManager = DefaultShardManagerBuilder.createDefault(token).enableCoroutines().build()
    shardManager.shards.forEach { it.awaitReady() }
    log.info { "Ready!" }
}