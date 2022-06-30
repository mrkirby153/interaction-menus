package com.mrkirby153.interactionmenus

import com.mrkirby153.interactionmenus.builders.PageBuilder
import com.mrkirby153.interactionmenus.builders.SubPageBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import org.apache.logging.log4j.LogManager
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * A menu is a list of pages that users can interact with
 *
 * @param initialPage The initial page of this menu
 * @param T The enum type representing the menu's page
 */
class Menu<T : Enum<*>>(
    initialPage: T,
    builder: (Menu<T>.() -> Unit)? = null
) {
    /**
     * This menu's ID
     */
    var id = UUID.randomUUID().toString()
    private val log = LogManager.getLogger("${this::class.java}:$id")

    /**
     * The current page that this menu is on
     */
    var currentPage = initialPage
    private var pages = mutableMapOf<T, PageBuilder.(Menu<T>) -> Unit>()

    private val registeredCallbacks = mutableMapOf<String, (InteractionHook) -> Unit>()
    private val selectCallbacks =
        mutableMapOf<String, (InteractionHook, List<SelectOption>) -> Unit>()

    /**
     * A global state repository for menu global states
     */
    val state = mutableMapOf<Any, Any?>()

    /**
     * The repository for pages state
     */
    val pageState = mutableMapOf<T, MutableMap<Any, Any?>>()

    /**
     * If the page should be re-rendered
     */
    var needsRender = false

    init {
        if (builder != null) {
            builder(this)
        }
    }

    /**
     * Adds the provided [page] to the menu
     */
    fun page(page: T, builder: PageBuilder.(Menu<T>) -> Unit) {
        log.trace("Registering builder for $page")
        pages[page] = builder
    }

    fun PageBuilder.subPage(name: String, builder: SubPageBuilder.() -> Unit) {
        subPage(getState(name), { setState(name, it) }, builder)
    }

    @JvmName("subPageBuilder")
    fun subPage(pageBuilder: PageBuilder, name: String, builder: SubPageBuilder.() -> Unit) {
        pageBuilder.subPage(name, builder)
    }

    /**
     * Sets the menu's page to the provided [page] and queues a rerender
     */
    fun setPage(page: T) {
        log.trace("Setting page to $page")
        this.currentPage = page
        // Clear the current page's state
        pageState[page]?.clear()
        rerender()
    }

    /**
     * Marks the menu as dirty and needs a re-render
     */
    fun rerender() {
        log.trace("Marking for re-render")
        needsRender = true
    }

    /**
     * Renders the menu into a Message
     */
    fun render(): Message {
        log.debug("Starting render")
        val rendered: Message
        val renderTime = measureTimeMillis {
            val currPageBuilder =
                pages[currentPage] ?: throw IllegalStateException("Current page is not registered")

            // Clear out the registered callbacks
            registeredCallbacks.clear()
            selectCallbacks.clear()

            val built = PageBuilder()
            built.currPageBuilder(this)

            rendered = MessageBuilder().apply {
                this.setContent(built.text)
                this.setEmbeds(built.getEmbeds())
                this.setActionRows(built.actionRows.map {
                    ActionRow.of(
                        *it.buttons.map { buttonBuilder ->
                            val callbackId = buttonBuilder.id ?: UUID.randomUUID().toString()
                            buttonBuilder.onClick?.run {
                                log.trace("Registering callback for button $callbackId")
                                registeredCallbacks[callbackId] = this
                            }
                            Button.of(
                                buttonBuilder.style,
                                callbackId,
                                buttonBuilder.value,
                                buttonBuilder.emoji
                            ).withDisabled(!buttonBuilder.enabled)
                        }.toTypedArray(),
                        *it.selects.map { selectBuilder ->
                            val selectId = selectBuilder.id ?: UUID.randomUUID().toString()
                            selectBuilder.onChange?.run {
                                log.trace("Registering menu callback for menu $selectId")
                                selectCallbacks[selectId] = this
                            }
                            SelectMenu.create(selectId).apply {
                                minValues = selectBuilder.min
                                maxValues = selectBuilder.max
                                if (selectBuilder.placeholder != "")
                                    placeholder = selectBuilder.placeholder

                                addOptions(
                                    selectBuilder.options.map { selectOptionBuilder ->
                                        val optionId =
                                            selectOptionBuilder.id ?: UUID.randomUUID().toString()
                                        selectOptionBuilder.onSelect?.run {
                                            log.trace("Registering onSelect callback for $optionId")
                                            registeredCallbacks[optionId] = this
                                        }
                                        SelectOption.of(selectOptionBuilder.value, optionId)
                                            .withDescription(selectOptionBuilder.description)
                                            .withDefault(selectOptionBuilder.default)
                                            .withEmoji(selectOptionBuilder.icon)
                                    }
                                )
                            }.build()
                        }.toTypedArray()
                    )
                })
            }.build()
        }
        log.debug("Rendered in $renderTime. ${registeredCallbacks.size} callbacks registered")
        return rendered
    }

    /**
     * Invokes the button callback for the provided [id] and [hook]
     */
    fun triggerButtonCallback(id: String, hook: InteractionHook): Boolean {
        this.registeredCallbacks[id]?.invoke(hook) ?: return false
        log.trace("Triggered button callback $id")
        return true
    }

    /**
     * Invokes the select callback for the provided [id], [selectedOptions] and [hook]
     */
    fun triggerSelectCallback(
        id: String,
        selectedOptions: List<SelectOption>,
        hook: InteractionHook
    ): Boolean {
        var executed = false
        this.selectCallbacks[id]?.apply {
            executed = true
            this.invoke(hook, selectedOptions)
            log.trace("Triggered change callback for menu $id")
        }
        selectedOptions.forEach {
            this.registeredCallbacks[it.value]?.apply {
                executed = true
                this.invoke(hook)
                log.trace("Triggered onSelect callback for option $id")
            }
        }
        return executed
    }

    /**
     * Retrieves the value from the state with the specified [key]
     */
    inline fun <reified T> getState(key: Any): T? {
        val data = pageState.computeIfAbsent(currentPage) { mutableMapOf() }[key] ?: return null
        if (data is T) {
            return data
        } else {
            throw ClassCastException("Cannot cast $data to provided type")
        }
    }

    /**
     * Retrieves the value from the global state with the specified [key]
     */
    inline fun <reified T> getGlobalState(key: Any): T? {
        val data = state[key] ?: return null
        if (data is T) {
            return data
        } else {
            throw ClassCastException("Cannot cast $data to provided type")
        }
    }

    /**
     * Sets the provided [key] in the page's state to the given [value] and queues a rerender
     */
    fun setState(key: Any, value: Any?) {
        pageState.computeIfAbsent(currentPage) { mutableMapOf() }[key] = value
        rerender()
    }

    /**
     * Sets the provided [key] in the menu's global state to the given [value] and queues a rerender
     */
    fun setStateGlobalState(key: Any, value: Any?) {
        state[key] = value
        rerender()
    }
}