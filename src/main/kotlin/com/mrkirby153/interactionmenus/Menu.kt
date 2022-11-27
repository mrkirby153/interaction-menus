package com.mrkirby153.interactionmenus

import com.mrkirby153.interactionmenus.builder.PageBuilder
import mu.KotlinLogging
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.utils.messages.AbstractMessageBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.api.utils.messages.MessageEditData
import java.util.UUID
import kotlin.system.measureTimeMillis


internal typealias InteractionCallback = (InteractionHook) -> Unit
internal typealias StringSelectCallback = (InteractionHook, List<SelectOption>) -> Unit
private typealias PageCallback<Pages> = PageBuilder.(Menu<Pages>) -> Unit

/**
 * A menu
 */
class Menu<Pages : Enum<*>>(
    initialPage: Pages,
    builder: (Menu<Pages>.() -> Unit)? = null
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

    private val stringSelectCallbacks = mutableMapOf<String, StringSelectCallback>()

    /**
     * A list of pages registered in this menu
     */
    private val pageBuilders = mutableMapOf<Pages, PageCallback<Pages>>()

    /**
     * If the contents of this menu changed, and it needs to be re-rendered
     */
    internal var dirty = false

    init {
        if (builder != null) {
            builder(this)
        }
    }

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

    fun triggerStringSelectCallback(
        id: String,
        selected: List<SelectOption>,
        hook: InteractionHook
    ): Boolean {
        var executed = false
        try {
            this.stringSelectCallbacks[id]?.apply {
                executed = true
                invoke(hook, selected)
                log.trace { logMessage("Triggered onChange for component $id") }
            }
            selected.forEach {
                this.callbacks[it.value]?.apply {
                    executed = true
                    invoke(hook)
                    log.trace { logMessage("Triggered onSelect for $id") }
                }
            }
        } catch (e: Exception) {
            log.error(e) { logMessage("Caught exception invoking select callback $id") }
        }
        return executed
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
        stringSelectCallbacks.putAll(pageBuilder.stringSelectCallbacks)
        pageBuilder.build(builder)
    }

    private fun logMessage(message: String): String = "[$id]: $message"

    override fun toString() = "Menu<$id>"

}