package com.mrkirby153.interactionmenus

import com.mrkirby153.interactionmenus.builder.PageBuilder
import mu.KotlinLogging
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.utils.messages.AbstractMessageBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.api.utils.messages.MessageEditData
import java.util.UUID
import kotlin.system.measureTimeMillis


internal typealias InteractionCallback = (InteractionHook) -> Unit
private typealias PageCallback<Pages> = PageBuilder.(Menu<Pages>) -> Unit

/**
 * A menu
 */
class Menu<Pages : Enum<*>>(
    initialPage: Pages
) {
    private val log = KotlinLogging.logger { }

    /**
     * The id of this menu
     */
    internal var id: String = UUID.randomUUID().toString()

    /**
     * The current page this menu is on
     */
    var currentPage: Pages = initialPage
        set(value) {
            field = value
            markDirty()
        }

    /**
     * Callbacks registered for interactions
     */
    private val callbacks = mutableMapOf<String, InteractionCallback>()

    /**
     * A list of pages registered in this menu
     */
    private val pageBuilders = mutableMapOf<Pages, PageCallback<Pages>>()

    /**
     * If the contents of this menu changed, and it needs to be re-rendered
     */
    private var dirty = false

    /**
     * Registers a page with this menu
     */
    fun page(page: Pages, builder: PageCallback<Pages>) {
        log.trace { logMessage("Registering page $page") }
        check(!pageBuilders.containsKey(page)) { logMessage("Attempting to register a duplicate page $page") }
        pageBuilders[page] = builder
    }

    fun markDirty() {
        log.trace {
            logMessage(
                if (dirty) {
                    "Already marked dirty previously"
                } else {
                    "Marking dirty"
                }
            )
        }
        dirty = true
    }

    fun triggerCallback(id: String, hook: InteractionHook): Boolean {
        val callback = callbacks[id] ?: return false
        log.trace { logMessage("Triggering button callback $id") }
        return try {
            callback(hook)
            true
        } catch (e: Exception) {
            log.error(e) { logMessage("Caught exception invoking button callback $id") }
            false
        }
    }

    internal fun renderEdit(): MessageEditData {
        log.debug { logMessage("Starting edit render of page $currentPage") }
        val builder: MessageEditBuilder
        val timeMs = measureTimeMillis {
            builder = MessageEditBuilder().apply {
                commonRender(this)
            }
        }
        log.debug { logMessage("Rendered edit in $timeMs ms. ${callbacks.size} callbacks currently registered") }
        return builder.build()
    }

    internal fun renderCreate(): MessageCreateData {
        log.debug { logMessage("Starting create render of page $currentPage") }
        val builder: MessageCreateBuilder
        val timeMs = measureTimeMillis {
            builder = MessageCreateBuilder().apply {
                commonRender(this)
            }
        }
        log.debug { logMessage("Rendered create in $timeMs ms. ${callbacks.size} callbacks currently registered") }
        return builder.build()
    }

    private fun commonRender(builder: AbstractMessageBuilder<*, *>) {
        callbacks.clear()

        val targetPageBuilder = pageBuilders[this.currentPage]
        checkNotNull(targetPageBuilder) { "Attempting to render a page that is not declared" }
        val pageBuilder = PageBuilder().apply {
            targetPageBuilder(this@Menu)
        }
        log.trace {
            logMessage(
                "Registering callbacks with ids [${
                    pageBuilder.interactionCallbacks.keys.joinToString(
                        ","
                    )
                }]"
            )
        }
        callbacks.putAll(pageBuilder.interactionCallbacks)
        pageBuilder.build(builder)
    }

    private fun logMessage(message: String): String = "[$id]: $message"

    override fun toString() = "Menu<$id>"

}