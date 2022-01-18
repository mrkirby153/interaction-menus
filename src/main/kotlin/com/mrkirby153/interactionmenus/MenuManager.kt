package com.mrkirby153.interactionmenus

import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.requests.restaction.MessageAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyAction
import org.apache.logging.log4j.LogManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class MenuManager(
    threadFactory: ThreadFactory? = null,
    gcPeriod: Long = 1,
    gcUnits: TimeUnit = TimeUnit.SECONDS
) : ListenerAdapter() {

    private val log = LogManager.getLogger()

    private val cleanupThreadPool =
        if (threadFactory != null)
            ScheduledThreadPoolExecutor(1, threadFactory)
        else
            ScheduledThreadPoolExecutor(1, ThreadFactory {
                Thread(it).apply {
                    name = "MenuCleanupThread"
                    isDaemon = true
                }
            })

    init {
        cleanupThreadPool.scheduleAtFixedRate(
            { garbageCollect() },
            0,
            gcPeriod,
            gcUnits
        )
    }

    private val registeredMenus = CopyOnWriteArrayList<RegisteredMenu>()

    @JvmOverloads
    fun register(menu: Menu<*>, timeout: Long = 5, timeUnit: TimeUnit = TimeUnit.MINUTES) {
        val timeoutMs =
            if (timeout == -1L) timeout else TimeUnit.MILLISECONDS.convert(timeout, timeUnit)
        log.debug("Registered menu $menu with a timeout of $timeoutMs ms")
        if (registeredMenus.firstOrNull { it.menu.id == menu.id } != null)
            throw IllegalArgumentException("Cannot register a menu twice")
        registeredMenus.add(
            RegisteredMenu(menu, System.currentTimeMillis(), timeoutMs)
        )
    }

    @JvmOverloads
    fun send(
        menu: Menu<*>,
        channel: MessageChannel,
        timeout: Long = 5,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): MessageAction {
        register(menu, timeout, timeUnit)
        return channel.sendMessage(menu.render())
    }

    @JvmOverloads
    fun reply(
        menu: Menu<*>,
        interaction: Interaction,
        ephemeral: Boolean = false,
        timeout: Long = 5,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): ReplyAction {
        register(menu, timeout, timeUnit)
        return interaction.reply(menu.render()).setEphemeral(ephemeral)
    }

    private fun garbageCollect() {
        var removed = 0
        registeredMenus.removeIf {
            if (it.timeout == -1L) {
                // -1 is persistent
                return@removeIf false
            }
            val expired = it.lastActivity + it.timeout < System.currentTimeMillis()
            if (expired)
                removed += 1
            expired
        }
        if (removed > 0) {
            log.debug("Garbage collected $removed menus")
        }
    }

    override fun onButtonClick(event: ButtonClickEvent) {
        registeredMenus.forEach {
            try {
                if (it.menu.triggerButtonCallback(event.componentId, event.hook)) {
                    log.debug("Executed ${event.componentId}")
                    if (it.menu.needsRender) {
                        log.debug("Re-rendering ${it.menu.id}")
                        event.editMessage(it.menu.render()).queue()
                        it.lastActivity = System.currentTimeMillis()
                    }
                    return
                }
            } catch (e: Exception) {
                log.error("${it.menu.id} encountered an exception", e)
                event.reply(":warning: Error processing: ${e.message}").setEphemeral(true).queue()
            }
        }
    }

    override fun onSelectionMenu(event: SelectionMenuEvent) {
        registeredMenus.forEach {
            try {
                if (it.menu.triggerSelectCallback(
                        event.componentId,
                        event.selectedOptions ?: mutableListOf(),
                        event.hook
                    )
                ) {
                    if (it.menu.needsRender) {
                        log.debug("Re-rendering ${it.menu.id}")
                        event.editMessage(it.menu.render()).queue()
                        it.lastActivity = System.currentTimeMillis()
                    }
                    return
                }
            } catch (e: Exception) {
                log.error("${it.menu.id} encountered an exception", e)
                event.reply(":warning: Error processing: ${e.message}").setEphemeral(true).queue()
            }
        }

    }

    private data class RegisteredMenu(
        val menu: Menu<*>,
        var lastActivity: Long,
        var timeout: Long
    )
}