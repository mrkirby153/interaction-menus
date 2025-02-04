package com.mrkirby153.interactionmenus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.utils.messages.MessageEditData
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Management class for [Menu]s
 *
 * @param threadFactory The factory to use when spawning the garbage collector
 * @param gcPeriod How often the garbage collector should run
 * @param gcUnits The time units on the gcPeriod
 * @param gcPriority The priority that the garbage collector should have. Defaults to min priority
 * @param automaticDeferralThreshold The threshold at which the menu will automatically defer. Defaults to 1000ms. Set to -1 to disable
 * @param notifyOnDeferral Whether to notify the user that the menu is taking a long time to update. Defaults to true
 */
class MenuManager(
    threadFactory: ThreadFactory? = null,
    gcPeriod: Long = 1,
    gcUnits: TimeUnit = TimeUnit.SECONDS,
    gcPriority: Int = Thread.MIN_PRIORITY,
    private val automaticDeferralThreshold: Int = 1000,
    private val notifyOnDeferral: Boolean = true
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

    private val coroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob() + EmptyCoroutineContext)

    init {
        cleanupThreadPool.scheduleAtFixedRate({ garbageCollect() }, 0, gcPeriod, gcUnits)
    }

    /**
     * Registers the provided [menu] to be managed. The menu will time out after the provided [timeout]
     */
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

    /**
     * Sends the provided [menu] as a reply to the given [hook].
     *
     * The menu will time out and be garbage collected after [timeout]. Specify [timeUnit] to change
     * the time units.
     *
     * Defaults to a timeout of 5 minutes.
     */
    suspend fun reply(
        menu: Menu<*>,
        hook: IReplyCallback,
        ephemeral: Boolean = true,
        timeout: Long = 5,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): ReplyCallbackAction {
        return HookRegisteringCallback(
            hook.reply(menu.renderCreate(coroutineScope)).setEphemeral(ephemeral),
            register(menu, timeout, timeUnit)
        )
    }

    /**
     * Shows a menu directly
     */
    suspend fun show(
        menu: Menu<*>,
        hook: InteractionHook,
        timeout: Long = 5,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): RestAction<*> {
        val original = hook.editOriginal(menu.renderEdit(coroutineScope))
        val registeredMenu = register(menu, timeout, timeUnit)
        registeredMenu.hook = hook
        return original
    }

    /**
     * Deletes the given [menu]
     */
    fun deleteMenu(menu: Menu<*>) {
        registeredMenus.firstOrNull { it.menu.id == menu.id }?.let { m ->
            log.debug { "Deleting menu ${m.menu.id}" }
            m.hook?.deleteOriginal()?.queue({
                log.trace { "Deleted menu ${m.menu.id}" }
                registeredMenus.removeIf { it.menu.id == menu.id }
            }, {
                log.trace { "Could not delete menu ${m.menu.id}: ${it.message}" }
            })
        }
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
        if (event !is GenericComponentInteractionCreateEvent) {
            return
        }

        val editChannel = Channel<MessageEditData?>()

        suspend fun maybeRerender(
            registeredMenu: RegisteredMenu,
            force: Boolean = false,
        ) {
            if (registeredMenu.menu.dirty || force) {
                log.trace { "Re-rendering menu ${registeredMenu.menu.id}" }
                val result = registeredMenu.menu.renderEdit(coroutineScope)
                registeredMenu.lastActivity = System.currentTimeMillis()
                log.trace { "Sending over channel" }
                editChannel.send(result)
            } else {
                editChannel.send(null)
            }
        }

        coroutineScope.launch {
            var delayJob: Job? = null
            var resultsJob: Job? = null
            var isDeferred = false
            var followMessage: Message? = null
            launch {
                var didProcess = false
                // Main coroutine to handle the event
                when (event) {
                    is ButtonInteractionEvent -> {
                        registeredMenus.forEach {
                            if (it.menu.triggerCallback(event.componentId, event.hook)) {
                                log.debug { "Triggered callback ${event.componentId}" }
                                maybeRerender(it)
                                log.debug { "Executed button callback ${event.componentId} for menu ${it.menu}" }
                                it.hook = event.hook
                                didProcess = true
                            }
                            return@forEach
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
                                didProcess = true
                                return@forEach
                            }
                        }
                    }

                    is EntitySelectInteractionEvent -> {
                        registeredMenus.forEach {
                            if (it.menu.triggerEntitySelectCallback(
                                    event.componentId,
                                    event.values,
                                    event.hook,
                                )
                            ) {
                                maybeRerender(it, true)
                                log.debug { "Executed entity select callback ${event.componentId} for menu ${it.menu}" }
                                it.hook = event.hook
                                didProcess = true
                                return@forEach
                            }
                        }
                    }
                }
                log.trace { "Cancelling delay job" }
                delayJob?.cancel() // Finished, cancel the delay job
                if (!didProcess) {
                    log.trace { "Cancelling results job. No processing happened" }
                    resultsJob?.cancel()
                }
            }
            if(automaticDeferralThreshold != -1) {
                delayJob = launch {
                    // Coroutine to defer for 1 second
                    delay(automaticDeferralThreshold.toLong())
                    log.trace { "Event is taking a long time to execute. Deferring" }
                    isDeferred = true
                    event.deferEdit().await()
                    if(notifyOnDeferral) {
                        followMessage =
                            event.hook.sendMessage("Updating the menu is taking longer than normal. Please wait")
                                .setEphemeral(true).await()
                    }
                }
            }
            resultsJob = launch {
                log.trace { "Waiting for results" }
                val result = editChannel.receive()
                log.trace { "Got response" }
                followMessage?.delete()?.await()
                if (isDeferred) {
                    if (result != null) {
                        log.trace { "Deferred. Editing original!" }
                        event.hook.editOriginal(result).await()
                    }
                } else {
                    if (result != null) {
                        log.trace { "Not Deferred. Editing!" }
                        event.editMessage(result).await()
                    } else {
                        event.deferEdit().await()
                    }
                }
            }
            resultsJob.join()
        }
    }

    /**
     * Data class storing a menu registered in a menu manager
     */
    data class RegisteredMenu(
        val menu: Menu<*>,
        var lastActivity: Long,
        var timeout: Long,
        var hook: InteractionHook? = null
    ) {
        private val log = KotlinLogging.logger { }

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