package com.mrkirby153.interactionmenus

import mu.KotlinLogging
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class HookRegisteringCallback(
    private val action: ReplyCallbackAction,
    private val registeredMenu: MenuManager.RegisteredMenu
) : ReplyCallbackAction by action {

    private val log = KotlinLogging.logger { }

    override fun queue(success: Consumer<in InteractionHook>?, failure: Consumer<in Throwable>?) {
        action.queue {
            log.trace { "Registering interaction hook with menu ${registeredMenu.menu.id}" }
            registeredMenu.hook = it
            success?.accept(it)
        }
    }

    override fun queueAfter(
        delay: Long,
        unit: TimeUnit,
        success: Consumer<in InteractionHook>?,
        failure: Consumer<in Throwable>?,
        executor: ScheduledExecutorService?
    ): ScheduledFuture<*> {
        return action.queueAfter(delay, unit, {
            log.trace { "Registering interaction hook with menu ${registeredMenu.menu.id}" }
            registeredMenu.hook = it
            success?.accept(it)
        }, failure, executor)
    }

    override fun submit(shouldQueue: Boolean): CompletableFuture<InteractionHook> {
        return action.submit().thenApply {
            log.trace { "Registering interaction hook with menu ${registeredMenu.menu.id}" }
            registeredMenu.hook = it
            it
        }
    }

    override fun complete(shouldQueue: Boolean): InteractionHook {
        val hook = action.complete(shouldQueue)
        log.trace { "Registering interaction hook with menu ${registeredMenu.menu.id}" }
        registeredMenu.hook = hook
        return hook
    }
}