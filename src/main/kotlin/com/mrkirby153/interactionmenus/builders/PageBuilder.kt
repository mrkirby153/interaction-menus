package com.mrkirby153.interactionmenus.builders

import com.mrkirby153.interactionmenus.Menu
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.interactions.components.selections.SelectOption

/**
 * Marker annotation for the Page DSL
 */
@DslMarker
annotation class PageDsl

/**
 * Inline function for creating a page
 */
fun <T : Enum<*>> page(builder: PageBuilder.(Menu<T>) -> Unit) = builder

/**
 * Builds a page in a menu
 */
@PageDsl
class PageBuilder {
    /**
     * The raw text to show on the page
     */
    var text = ""
    private var embeds = mutableListOf<MessageEmbed>()

    /**
     * A list of action rows to render on the page
     */
    val actionRows = mutableListOf<ActionRowBuilder>()

    /**
     * Adds the given [embed] to the page
     */
    fun addEmbed(embed: MessageEmbed) {
        embeds.add(embed)
    }

    /**
     * Gets the embeds for this page
     */
    fun getEmbeds() = embeds

    /**
     * Sets this page's text to the text returned by the [StringBuilder]
     */
    inline fun text(builder: StringBuilder.() -> Unit) {
        text = StringBuilder().apply(builder).toString()
    }

    /**
     * Adds an action row to the page
     */
    inline fun actionRow(builder: ActionRowBuilder.() -> Unit) {
        actionRows.add(ActionRowBuilder().apply(builder))
    }

    /**
     * Creates a sub-page for this page with the provided [name][currentPage].
     *
     * [onChange] will be invoked when the page dropdown is changed
     */
    inline fun subPage(
        currentPage: String?,
        noinline onChange: (String) -> Unit,
        builder: SubPageBuilder.() -> Unit
    ) {
        val subPage = SubPageBuilder(onChange).apply(builder)
        actionRows.add(subPage.options(currentPage))
        actionRows.addAll(subPage.build(currentPage))
    }
}

/**
 * Builder for an action row
 */
@PageDsl
class ActionRowBuilder {
    /**
     * A list of buttons in this action row
     */
    val buttons = mutableListOf<ButtonBuilder>()

    /**
     * A list of selects in this action row
     */
    val selects = mutableListOf<SelectMenuBuilder>()

    /**
     * Adds a button to the action row. If [id] is not specified, a random one will be generated
     */
    inline fun button(id: String? = null, builder: ButtonBuilder.() -> Unit) {
        if (selects.size > 0) {
            throw IllegalArgumentException("Can't mix buttons and selects")
        }
        buttons.add(ButtonBuilder(id).apply(builder))
    }

    /**
     * Adds a select dropdown to the action row. If [id] is not specified, a random one will be
     * generated
     */
    inline fun select(id: String? = null, builder: SelectMenuBuilder.() -> Unit) {
        if (buttons.size > 0) {
            throw IllegalArgumentException("Can't mix buttons and selects")
        }
        selects.add(SelectMenuBuilder(id).apply(builder))
    }
}

/**
 * A builder for buttons
 *
 * @param id The id of the button
 */
@PageDsl
class ButtonBuilder(
    val id: String? = null
) {
    /**
     * The value shown on the button to users
     */
    var value = ""

    /**
     * The style of the button
     */
    var style = ButtonStyle.SECONDARY

    /**
     * The action that's ran when the button is clicked
     */
    var onClick: ((InteractionHook) -> Unit)? = null

    /**
     * The emoji to show on the button
     */
    var emoji: Emoji? = null

    /**
     * If the button is enabled
     */
    var enabled = true

    /**
     * Runs the provided [hook] when the button is clicked
     */
    fun onClick(hook: (InteractionHook) -> Unit) {
        this.onClick = hook
    }
}

/**
 * A builder for a Select menu
 *
 * @param id The id of the select menu
 */
@PageDsl
class SelectMenuBuilder(
    val id: String? = null
) {
    /**
     * The action ran when the select changes
     */
    var onChange: ((InteractionHook, List<SelectOption>) -> Unit)? = null

    /**
     * A list of options ot render in the select menu
     */
    var options = mutableListOf<SelectOptionBuilder>()

    /**
     * The minimum number of options that can be selected
     */
    var min = 1

    /**
     * The maximum number of options that can be selected
     */
    var max = 1

    /**
     * Placeholder text to show when no option is selected
     */
    var placeholder = ""

    /**
     * Adds an option with the provided [id] to this page. If no id is provided a random one will
     * be generated
     */
    inline fun option(id: String? = null, builder: SelectOptionBuilder.() -> Unit) {
        options.add(SelectOptionBuilder(id).apply(builder))
    }

    /**
     * Runs the provided [hook] when the select menu changes
     */
    fun onChange(hook: (InteractionHook, List<SelectOption>) -> Unit) {
        this.onChange = hook
    }
}

/**
 * A builder for a select option
 *
 * @param id The id of the select option
 */
@PageDsl
class SelectOptionBuilder(
    val id: String? = null
) {
    /**
     * If this option should be selected by default
     */
    var default = false

    /**
     * The long description of this option
     */
    var description: String? = null

    /**
     * The text rendered in the dropdown to users
     */
    var value: String = ""

    /**
     * The [Emoji] icon that this select option has
     */
    var icon: Emoji? = null

    /**
     * A callback ran when this option is selected
     */
    var onSelect: ((InteractionHook) -> Unit)? = null

    /**
     * Runs the provided [hook] when this option is selected
     */
    fun onSelect(hook: (InteractionHook) -> Unit) {
        this.onSelect = hook
    }
}

/**
 * A builder for a sub page
 *
 * @param onChange A callback ran when the sub-page select dropdown changes
 */
@PageDsl
class SubPageBuilder(
    val onChange: (String) -> Unit
) {

    /**
     * A list of pages and their builders
     */
    val pages = mutableMapOf<String, SelectOptionBuilder>()

    /**
     * A list of the actual sub-pages
     */
    val subPages = mutableMapOf<String, SubPageContentsBuilder>()

    /**
     * Adds a sub-page with the provided [name], [description] and [icon]
     */
    inline fun page(
        name: String,
        description: String,
        icon: Emoji? = null,
        builder: SubPageContentsBuilder.() -> Unit
    ) {
        val optionBuilder = SelectOptionBuilder().apply {
            this.value = name
            this.description = description
            this.icon = icon
            onSelect {
                this@SubPageBuilder.onChange.invoke(name)
            }
        }
        pages[name] = optionBuilder
        subPages[name] = SubPageContentsBuilder().apply(builder)
    }

    /**
     * Builds the page builder for the [currentPage]
     */
    fun build(currentPage: String?): List<ActionRowBuilder> {
        return subPages[currentPage]?.actionRows ?: mutableListOf()
    }

    /**
     * Generates the page selector with the [currentPage] selected
     */
    fun options(currentPage: String?): ActionRowBuilder {
        return ActionRowBuilder().apply {
            select {
                this@SubPageBuilder.pages.forEach { (page, builder) ->
                    if (page == currentPage) {
                        builder.default = true
                    }
                    options.add(builder)
                }
            }
        }
    }

    /**
     * A builder for the sub-page's content
     */
    @PageDsl
    class SubPageContentsBuilder {

        /**
         * A list of action rows on this page
         */
        val actionRows = mutableListOf<ActionRowBuilder>()

        /**
         * Adds an action row to the page
         */
        inline fun actionRow(builder: ActionRowBuilder.() -> Unit) {
            this.actionRows.add(ActionRowBuilder().apply(builder))
        }
    }
}