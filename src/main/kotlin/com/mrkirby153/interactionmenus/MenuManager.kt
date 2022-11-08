package com.mrkirby153.interactionmenus

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.components.ComponentInteraction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.apache.logging.log4j.LogManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * Management class for [Menu]s
 *
 * @param threadFactory The factory to use when spawning the garbage collector
 * @param gcPeriod How often the garbage collector should run
 * @param gcUnits The time units on the gcPeriod
 * @param gcPriority The priority that the garbage collector should have. Defaults to min priority
 */
class MenuManager(
    threadFactory: ThreadFactory? = null,
    gcPeriod: Long = 1,
    gcUnits: TimeUnit = TimeUnit.SECONDS,
    gcPriority: Int = Thread.MIN_PRIORITY
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
                    priority = gcPriority
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

    /**
     * Registers the given [menu] with the manager. The menu will time out and be garbage collected
     * after [timeout]. Specify [timeUnit] to change the units of [timeout]
     *
     * Defaults to a timeout of 5 minutes
     */
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

    /**
     * Sends the provided [menu] in the given [channel] as a **non-ephemeral** message.
     *
     * The menu will time out and be garbage collected after [timeout]. Specify [timeUnit] to change
     * the time units.
     *
     * Defaults to a timeout of 5 minutes
     */
    @JvmOverloads
    fun send(
        menu: Menu<*>,
        channel: MessageChannel,
        timeout: Long = 5,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): MessageCreateAction {
        register(menu, timeout, timeUnit)
        return channel.sendMessage(menu.renderCreate())
    }

    /**
     * Sends the provided [menu] as an [ephemeral] reply to the given [interaction].
     *
     * The menu will time out and be garbage collected after [timeout]. Specify [timeUnit] to change
     * the time units.
     *
     * Defaults to a timeout of 5 minutes
     */
    @JvmOverloads
    fun reply(
        menu: Menu<*>,
        interaction: IReplyCallback,
        ephemeral: Boolean = false,
        timeout: Long = 5,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): ReplyCallbackAction {
        register(menu, timeout, timeUnit)
        return interaction.reply(menu.renderCreate()).setEphemeral(ephemeral)
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


    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        registeredMenus.forEach {
            try {
                if (it.menu.triggerButtonCallback(event.componentId, event.hook)) {
                    log.debug("Executed ${event.componentId}")
                    if (it.menu.needsRender) {
                        log.debug("Re-rendering ${it.menu.id}")
                        event.editMessage(it.menu.renderEdit()).queue()
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

    override fun onSelectMenuInteraction(event: SelectMenuInteractionEvent) {
        registeredMenus.forEach {
            try {
                if (it.menu.triggerSelectCallback(
                        event.componentId,
                        event.selectedOptions,
                        event.hook
                    )
                ) {
                    if (it.menu.needsRender) {
                        log.debug("Re-rendering ${it.menu.id}")
                        event.editMessage(it.menu.renderEdit()).queue()
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