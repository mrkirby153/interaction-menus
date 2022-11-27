package com.mrkirby153.interactionmenus

import mu.KotlinLogging
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class MenuManager(
    threadFactory: ThreadFactory? = null,
    gcPeriod: Long = 1,
    gcUnits: TimeUnit = TimeUnit.SECONDS,
    gcPriority: Int = Thread.MIN_PRIORITY
) : EventListener {

    private val log = KotlinLogging.logger { }

    private val cleanupThreadPool = threadFactory?.run { ScheduledThreadPoolExecutor(1, this) }
        ?: ScheduledThreadPoolExecutor(1, ThreadFactory {
            Thread(it).apply {
                name = "MenuCleanupThread"
                isDaemon = true
                priority = gcPriority
            }
        })

    private val menuLock = Any()
    private val registeredMenus = mutableListOf<RegisteredMenu>()

    init {
        cleanupThreadPool.scheduleAtFixedRate({ garbageCollect() }, 0, gcPeriod, gcUnits)
    }

    fun register(
        menu: Menu<*>,
        timeout: Long = 5,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): RegisteredMenu {
        check(timeout > 0) { "Timeout must be greater than 0" }
        var timeoutMs = TimeUnit.MILLISECONDS.convert(timeout, timeUnit)
        if (timeoutMs > TimeUnit.MILLISECONDS.convert(15, TimeUnit.MINUTES)) {
            log.warn { "Timeout for menu $menu is > 15 minutes, setting to 15 minutes" }
            timeoutMs = TimeUnit.MILLISECONDS.convert(15, TimeUnit.MINUTES)
        }
        log.debug { "Registering menu $menu with a timeout of ${timeoutMs}ms" }
        check(registeredMenus.none { it.menu.id == menu.id }) { "Attempting to register a menu twice $menu" }
        val registeredMenu = RegisteredMenu(menu, System.currentTimeMillis(), timeoutMs)
        synchronized(menuLock) {
            registeredMenus.add(registeredMenu)
        }
        return registeredMenu
    }

    fun send(
        menu: Menu<*>,
        channel: MessageChannel,
        timeout: Long = 5,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): MessageCreateAction {
        register(menu, timeout, timeUnit)
        return channel.sendMessage(menu.renderCreate())
    }

    fun reply(
        menu: Menu<*>,
        hook: IReplyCallback,
        ephemeral: Boolean = true,
        timeout: Long = 5,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): ReplyCallbackAction {
        return HookRegisteringCallback(
            hook.reply(menu.renderCreate()).setEphemeral(ephemeral),
            register(menu, timeout, timeUnit)
        )
    }

    private fun garbageCollect() {
        var removed = 0
        synchronized(registeredMenus) {
            registeredMenus.removeIf {
                if (it.timedOut) {
                    log.trace { "Removing menu $it, as it has timed out" }
                    removed += 1
                    it.onTimeout()
                }
                it.timedOut
            }
        }
        if (removed > 0)
            log.debug { "Garbage collected $removed menus" }
    }

    override fun onEvent(event: GenericEvent) {

        fun maybeRerender(registeredMenu: RegisteredMenu, force: Boolean = false) {
            check(event is GenericComponentInteractionCreateEvent) { "Event was not a component interaction" }
            if (registeredMenu.menu.dirty || force) {
                log.trace { "Re-rendering menu ${registeredMenu.menu.id}" }
                event.editMessage(registeredMenu.menu.renderEdit())
                    .queue()
                registeredMenu.lastActivity = System.currentTimeMillis()
            }
        }

        when (event) {
            is ButtonInteractionEvent -> {
                registeredMenus.forEach {
                    if (it.menu.triggerCallback(event.componentId, event.hook)) {
                        maybeRerender(it)
                        log.debug { "Executed button callback ${event.componentId} for menu ${it.menu}" }
                        it.hook = event.hook
                        return
                    }
                }
            }

            is StringSelectInteractionEvent -> {
                registeredMenus.forEach {
                    if (it.menu.triggerStringSelectCallback(
                            event.componentId,
                            event.selectedOptions,
                            event.hook
                        )
                    ) {
                        maybeRerender(it, true)
                        log.debug { "Executed string select callback ${event.componentId} for menu ${it.menu}" }
                        it.hook = event.hook
                        return
                    }
                }
            }

            is EntitySelectInteractionEvent -> {
                registeredMenus.forEach {
                    if (it.menu.triggerEntitySelectCallback(
                            event.componentId,
                            event.values,
                            event.hook
                        )
                    ) {
                        maybeRerender(it, true)
                        log.debug { "Executed entity select callback ${event.componentId} for menu ${it.menu}" }
                        it.hook = event.hook
                        return
                    }
                }
            }
        }
    }

    data class RegisteredMenu(
        val menu: Menu<*>,
        var lastActivity: Long,
        var timeout: Long,
        var hook: InteractionHook? = null
    ) {
        private val log = KotlinLogging.logger {  }

        val timedOut: Boolean
            get() = lastActivity + timeout < System.currentTimeMillis()


        fun onTimeout() {
            val hook = this.hook ?: return
            log.debug { "Disabling all components in $menu as it has timed out" }
            hook.retrieveOriginal().queue({ msg ->
                hook.editOriginalComponents(msg.components.map { it.asDisabled() }).queue()
                hook.deleteOriginal().queueAfter(2, TimeUnit.MINUTES, {
                    log.trace { "Deleted message for menu ${menu.id}" }
                }, {
                    log.trace { "Could not delete message for menu ${menu.id}: ${it.message}" }
                })
            }, {
                log.trace { "Could not disable components for ${menu.id}: ${it.message}" }
            })
        }
    }
}