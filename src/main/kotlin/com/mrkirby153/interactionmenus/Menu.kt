package com.mrkirby153.interactionmenus

import com.mrkirby153.interactionmenus.builder.PageBuilder
import mu.KotlinLogging
import net.dv8tion.jda.api.entities.IMentionable
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
internal typealias EntitySelectCallback<Mentionable> = (InteractionHook, List<Mentionable>) -> Unit

private typealias PageCallback<Pages> = PageBuilder.(Menu<Pages>) -> Unit

/**
 * A menu
 */
open class Menu<Pages : Enum<*>>(
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
    private val entitySelectCallbacks =
        mutableMapOf<String, EntitySelectCallback<out IMentionable>>()

    /**
     * A list of pages registered in this menu
     */
    private val pageBuilders = mutableMapOf<Pages, PageCallback<Pages>>()

    /**
     * If the contents of this menu changed, and it needs to be re-rendered
     */
    internal var dirty = false

    private var onShowPage: Pages? = null

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

    /**
     * Marks the menu as dirty and queues it for re-rendering
     */
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

    internal fun triggerCallback(id: String, hook: InteractionHook): Boolean {
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

    internal fun triggerStringSelectCallback(
        id: String,
        selected: List<SelectOption>,
        hook: InteractionHook
    ): Boolean {
        var executed = false
        try {
            selected.forEach {
                this.callbacks[it.value]?.apply {
                    executed = true
                    invoke(hook)
                    log.trace { logMessage("Triggered onSelect for $id") }
                }
            }
            this.stringSelectCallbacks[id]?.apply {
                executed = true
                invoke(hook, selected)
                log.trace { logMessage("Triggered onChange for component $id") }
            }
        } catch (e: Exception) {
            log.error(e) { logMessage("Caught exception invoking select callback $id") }
        }
        return executed
    }

    internal fun triggerEntitySelectCallback(
        id: String,
        selected: List<IMentionable>,
        hook: InteractionHook
    ): Boolean {
        try {
            this.entitySelectCallbacks[id]?.apply {
                invoke(hook, selected)
                return true
            }
        } catch (e: Exception) {
            log.error(e) { logMessage("Caught exception invoking select callback $id") }
        }
        return false
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
        entitySelectCallbacks.putAll(pageBuilder.entitySelectCallbacks)
        pageBuilder.build(builder)
        callOnChange(pageBuilder)
    }

    private fun logMessage(message: String): String = "[$id]: $message"

    override fun toString() = "Menu<$id>"
    private fun callOnChange(builder: PageBuilder) {
        if (onShowPage != currentPage) {
            log.trace { "Invoking onShow for page $currentPage" }
            builder.onShowHook?.invoke()
            onShowPage = currentPage
        }
    }

}

/**
 * A menu but with state that is stored in-memory
 *
 * @param initialState The initial state of the menu
 * @param stateBuilder A builder that creates an initial state for this menu
 * @param initialPage The initial page of the menu
 */
class StatefulMenu<Pages : Enum<Pages>, State : Any> private constructor(
    initialPage: Pages,
    stateBuilder: (() -> State)? = null,
    initialState: State? = null
) : Menu<Pages>(initialPage) {
    /**
     * The menu's state
     */
    var state = stateBuilder?.invoke() ?: initialState
    ?: throw IllegalArgumentException("Initial state must be provided")

    constructor(
        initialPage: Pages,
        state: State,
        builder: (StatefulMenu<Pages, State>.() -> Unit)? = null
    ) : this(initialPage, null, state) {
        builder?.invoke(this)
    }

    constructor(
        initialPage: Pages,
        stateBuilder: (() -> State)?,
        builder: (StatefulMenu<Pages, State>.() -> Unit) = {}
    ) : this(initialPage, stateBuilder, null) {
        builder.invoke(this)
    }

}