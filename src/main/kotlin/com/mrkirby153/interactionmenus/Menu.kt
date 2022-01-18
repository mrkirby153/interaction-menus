package com.mrkirby153.interactionmenus

import com.mrkirby153.interactionmenus.builders.PageBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu
import java.util.UUID

/**
 *
 */
class Menu<T : Enum<*>>(
    initialPage: T
) {
    private var currentPage = initialPage
    private var pages = mutableMapOf<T, PageBuilder.(Menu<T>) -> Unit>()

    private val registeredCallbacks = mutableMapOf<String, (InteractionHook) -> Unit>()

    var needsRender = false

    fun page(page: T, builder: PageBuilder.(Menu<T>) -> Unit) {
        pages[page] = builder
    }

    fun setPage(page: T) {
        this.currentPage = page
        rerender()
    }

    /**
     * The menu should be rerendered
     */
    fun rerender() {
        needsRender = true
    }

    /**
     * Renders the menu into a Message
     */
    fun render(): Message {
        val currPageBuilder =
            pages[currentPage] ?: throw IllegalStateException("Current page is not registered")

        // Clear out the registered callbacks
        registeredCallbacks.clear()

        val built = PageBuilder()
        built.currPageBuilder(this)

        return MessageBuilder().apply {
            this.setContent(built.text)
            this.setEmbeds(built.getEmbeds())
            this.setActionRows(built.actionRows.map {
                ActionRow.of(
                    *it.buttons.map { buttonBuilder ->
                        val callbackId = buttonBuilder.id ?: UUID.randomUUID().toString()
                        buttonBuilder.onClick?.run {
                            registeredCallbacks[callbackId] = this
                        }
                        Button.of(
                            buttonBuilder.style,
                            callbackId,
                            buttonBuilder.value,
                            buttonBuilder.emoji
                        )
                    }.toTypedArray(),
                    *it.selects.map { selectBuilder ->
                        val selectId = selectBuilder.id ?: UUID.randomUUID().toString()
                        selectBuilder.onChange?.run {
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

    fun triggerCallback(id: String, hook: InteractionHook): Boolean {
        val callback = this.registeredCallbacks[id] ?: return false
        callback.invoke(hook)
        return true
    }
}