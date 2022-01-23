package com.mrkirby153.interactionmenus

import com.mrkirby153.interactionmenus.builders.PageBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu
import org.apache.logging.log4j.LogManager
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * A menu is a list of pages that users can interact with
 */
class Menu<T : Enum<*>>(
    initialPage: T
) {
    var id = UUID.randomUUID().toString()
    private val log = LogManager.getLogger("${this::class.java}:$id")
    private var currentPage = initialPage
    private var pages = mutableMapOf<T, PageBuilder.(Menu<T>) -> Unit>()

    private val registeredCallbacks = mutableMapOf<String, (InteractionHook) -> Unit>()

    val state = mutableMapOf<Any, Any?>()

    var needsRender = false

    fun page(page: T, builder: PageBuilder.(Menu<T>) -> Unit) {
        log.trace("Registering builder for $page")
        pages[page] = builder
    }

    fun setPage(page: T) {
        log.trace("Setting page to $page")
        this.currentPage = page
        rerender()
    }

    /**
     * The menu should be rerendered
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
                                registeredCallbacks[selectId] = this
                            }
                            SelectionMenu.create(selectId).apply {
                                minValues = selectBuilder.min
                                maxValues = selectBuilder.max

                                addOptions(
                                    selectBuilder.options.map { selectOptionBuilder ->
                                        val optionId =
                                            selectOptionBuilder.id ?: UUID.randomUUID().toString()
                                        selectOptionBuilder.onChange?.run {
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

    fun triggerButtonCallback(id: String, hook: InteractionHook): Boolean {
        this.registeredCallbacks[id]?.invoke(hook) ?: return false
        log.trace("Triggered button callback $id")
        return true
    }

    fun triggerSelectCallback(
        id: String,
        selectedOptions: List<SelectOption>,
        hook: InteractionHook
    ): Boolean {
        var executed = false
        this.registeredCallbacks[id]?.apply {
            executed = true
            this.invoke(hook)
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

    inline fun <reified T> getState(key: Any): T? {
        val data = state[key] ?: return null
        if (data is T) {
            return data
        } else {
            throw ClassCastException("Cannot cast $data to provided type")
        }
    }

    fun setState(key: Any, value: Any?) {
        state[key] = value
    }
}