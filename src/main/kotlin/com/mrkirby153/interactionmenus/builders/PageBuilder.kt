package com.mrkirby153.interactionmenus.builders

import com.mrkirby153.interactionmenus.Menu
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ButtonStyle

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
    var onChange: ((InteractionHook) -> Unit)? = null
    var options = mutableListOf<SelectOptionBuilder>()

    var min = 1
    var max = 1
    var placeholder = ""

    inline fun option(id: String? = null, builder: SelectOptionBuilder.() -> Unit) {
        options.add(SelectOptionBuilder(id).apply(builder))
    }

    fun onChange(hook: (InteractionHook) -> Unit) {
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