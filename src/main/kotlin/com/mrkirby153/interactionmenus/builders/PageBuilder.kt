package com.mrkirby153.interactionmenus.builders

import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ButtonStyle

@DslMarker
annotation class PageDsl

fun page(builder: PageBuilder.() -> Unit) = builder

@PageDsl
class PageBuilder {
    var text = ""
    private var embeds = mutableListOf<MessageEmbed>()
    val actionRows = mutableListOf<ActionRowBuilder.() -> Unit>()

    fun addEmbed(embed: MessageEmbed) {
        embeds.add(embed)
    }

    inline fun text(builder: StringBuilder.() -> Unit) {
        text = StringBuilder().apply(builder).toString()
    }

    fun actionRow(builder: ActionRowBuilder.() -> Unit) {
        actionRows.add(builder)
    }
}

@PageDsl
class ActionRowBuilder {
    val buttons = mutableListOf<ButtonBuilder>()
    val selects = mutableListOf<SelectMenuBuilder>()

    inline fun button(builder: ButtonBuilder.() -> Unit) {
        if (selects.size > 0) {
            throw IllegalArgumentException("Can't mix buttons and selects")
        }
        buttons.add(ButtonBuilder().apply(builder))
    }

    inline fun select(builder: SelectMenuBuilder.() -> Unit) {
        if (buttons.size > 0) {
            throw IllegalArgumentException("Can't mix buttons and selects")
        }
        selects.add(SelectMenuBuilder().apply(builder))
    }
}

@PageDsl
class ButtonBuilder {
    var value = ""
    var style = ButtonStyle.SECONDARY
    var onClick: ((InteractionHook) -> Unit)? = null

    fun onClick(hook: (InteractionHook) -> Unit) {
        this.onClick = hook
    }
}

@PageDsl
class SelectMenuBuilder {
    var onChange: ((InteractionHook) -> Unit)? = null
    var options = mutableListOf<SelectOptionBuilder>()

    inline fun option(builder: SelectOptionBuilder.() -> Unit) {
        options.add(SelectOptionBuilder().apply(builder))
    }
}

@PageDsl
class SelectOptionBuilder {
    var default = false
    var description: String? = null
    var value: String? = null
    var icon: Emoji? = null
}