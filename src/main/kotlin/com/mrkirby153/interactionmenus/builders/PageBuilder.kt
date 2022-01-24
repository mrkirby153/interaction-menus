package com.mrkirby153.interactionmenus.builders

import com.mrkirby153.interactionmenus.Menu
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import java.util.UUID

@DslMarker
annotation class PageDsl

fun <T : Enum<*>> page(builder: PageBuilder.(Menu<T>) -> Unit) = builder

@PageDsl
class PageBuilder {
    var text = ""
    private var embeds = mutableListOf<MessageEmbed>()
    val actionRows = mutableListOf<ActionRowBuilder>()

    fun addEmbed(embed: MessageEmbed) {
        embeds.add(embed)
    }

    fun getEmbeds() = embeds

    inline fun text(builder: StringBuilder.() -> Unit) {
        text = StringBuilder().apply(builder).toString()
    }

    inline fun actionRow(builder: ActionRowBuilder.() -> Unit) {
        actionRows.add(ActionRowBuilder().apply(builder))
    }

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

@PageDsl
class ActionRowBuilder {
    val buttons = mutableListOf<ButtonBuilder>()
    val selects = mutableListOf<SelectMenuBuilder>()

    inline fun button(id: String? = null, builder: ButtonBuilder.() -> Unit) {
        if (selects.size > 0) {
            throw IllegalArgumentException("Can't mix buttons and selects")
        }
        buttons.add(ButtonBuilder(id).apply(builder))
    }

    inline fun select(id: String? = null, builder: SelectMenuBuilder.() -> Unit) {
        if (buttons.size > 0) {
            throw IllegalArgumentException("Can't mix buttons and selects")
        }
        selects.add(SelectMenuBuilder(id).apply(builder))
    }
}

@PageDsl
class ButtonBuilder(
    val id: String? = null
) {
    var value = ""
    var style = ButtonStyle.SECONDARY
    var onClick: ((InteractionHook) -> Unit)? = null
    var emoji: Emoji? = null
    var enabled = true

    fun onClick(hook: (InteractionHook) -> Unit) {
        this.onClick = hook
    }
}

@PageDsl
class SelectMenuBuilder(
    val id: String? = null
) {
    var onChange: ((InteractionHook, List<SelectOption>) -> Unit)? = null
    var options = mutableListOf<SelectOptionBuilder>()

    var min = 1
    var max = 1
    var placeholder = ""

    inline fun option(id: String? = null, builder: SelectOptionBuilder.() -> Unit) {
        options.add(SelectOptionBuilder(id).apply(builder))
    }

    fun onChange(hook: (InteractionHook, List<SelectOption>) -> Unit) {
        this.onChange = hook
    }
}

@PageDsl
class SelectOptionBuilder(
    val id: String? = null
) {
    var default = false
    var description: String? = null
    var value: String = ""
    var icon: Emoji? = null
    var onChange: ((InteractionHook) -> Unit)? = null

    fun onSelect(hook: (InteractionHook) -> Unit) {
        this.onChange = hook
    }
}

@PageDsl
class SubPageBuilder(
    val onChange: (String) -> Unit
) {

    val pages = mutableMapOf<String, SelectOptionBuilder>()
    val subPages = mutableMapOf<String, SubPageContentsBuilder>()

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

    fun build(currentPage: String?): List<ActionRowBuilder> {
        return subPages[currentPage]?.actionRows ?: mutableListOf()
    }

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

    @PageDsl
    class SubPageContentsBuilder {

        val actionRows = mutableListOf<ActionRowBuilder>()

        inline fun actionRow(builder: ActionRowBuilder.() -> Unit) {
            this.actionRows.add(ActionRowBuilder().apply(builder))
        }
    }
}