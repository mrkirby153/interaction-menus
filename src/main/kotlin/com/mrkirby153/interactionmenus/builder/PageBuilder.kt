package com.mrkirby153.interactionmenus.builder

import com.mrkirby153.interactionmenus.EntitySelectCallback
import com.mrkirby153.interactionmenus.InteractionCallback
import com.mrkirby153.interactionmenus.StringSelectCallback
import mu.KotlinLogging
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ItemComponent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import net.dv8tion.jda.api.utils.messages.AbstractMessageBuilder
import java.net.URI
import java.util.UUID


@DslMarker
annotation class PageDsl

@PageDsl
class PageBuilder {

    private var text = ""

    private val embeds = mutableListOf<MessageEmbed>()
    private val actionRows = mutableListOf<ActionRow>()
    internal val interactionCallbacks = mutableMapOf<String, InteractionCallback>()
    internal val stringSelectCallbacks = mutableMapOf<String, StringSelectCallback>()
    internal val entitySelectCallbacks =
        mutableMapOf<String, EntitySelectCallback<out IMentionable>>()

    fun build(builder: AbstractMessageBuilder<*, *>) {
        builder.setContent(text)
        builder.setComponents(actionRows)
        builder.setEmbeds(embeds)
    }

    fun addEmbed(embed: MessageEmbed) {
        embeds.add(embed)
    }

    fun actionRow(builder: ActionRowBuilder.() -> Unit) {
        val arb = ActionRowBuilder().apply(builder)
        actionRows.add(arb.build())
        interactionCallbacks.putAll(arb.interactionCallbacks)
        stringSelectCallbacks.putAll(arb.stringSelectCallbacks)
        entitySelectCallbacks.putAll(arb.entitySelectCallbacks)
    }

    fun text(builder: StringBuilder.() -> Unit) {
        text = StringBuilder().apply(builder).toString()
    }

    fun text(text: String) {
        this.text = text
    }
}

@PageDsl
class ActionRowBuilder : Builder<ActionRow> {

    private val buttons = mutableListOf<Button>()
    private val selects = mutableListOf<SelectMenu>()

    internal val interactionCallbacks = mutableMapOf<String, InteractionCallback>()
    internal val stringSelectCallbacks = mutableMapOf<String, StringSelectCallback>()
    internal val entitySelectCallbacks =
        mutableMapOf<String, EntitySelectCallback<out IMentionable>>()


    override fun build(): ActionRow {
        val components = mutableListOf<ItemComponent>()
        components.addAll(buttons)
        components.addAll(selects)
        return ActionRow.of(components)
    }

    fun button(text: String, builder: ButtonBuilder.() -> Unit) {
        check(selects.size == 0) { "Can't mix buttons and selects in the same action row" }
        val bb = ButtonBuilder(text).apply(builder)
        val callback = bb.onClick
        if (callback != null) {
            interactionCallbacks[bb.id] = callback
        }
        buttons.add(bb.build())
    }

    fun stringSelect(builder: StringSelectBuilder.() -> Unit) {
        check(buttons.size == 0) { "Can't mix selects and buttons in the same action row" }
        val ssb = StringSelectBuilder().apply(builder)
        val onChange = ssb.onChange
        if (onChange != null)
            stringSelectCallbacks[ssb.id] = onChange
        interactionCallbacks.putAll(ssb.callbacks)
        selects.add(ssb.build())
    }

    fun mentionableSelect(builder: EntitySelectBuilder<IMentionable>.() -> Unit) {
        check(buttons.size == 0) { "Can't mix selects and buttons in the same action row" }
        val esb =
            EntitySelectBuilder<IMentionable>(SelectTarget.ROLE, SelectTarget.USER).apply(builder)
        val onSelect = esb.onSelect
        if (onSelect != null) {
            entitySelectCallbacks[esb.id] = onSelect
        }
        selects.add(esb.build())
    }

    fun userSelect(builder: EntitySelectBuilder<User>.() -> Unit) {
        check(buttons.size == 0) { "Can't mix selects and buttons in the same action row" }
        val esb = EntitySelectBuilder<User>(SelectTarget.ROLE, SelectTarget.USER).apply(builder)
        val onSelect = esb.onSelect
        if (onSelect != null) {
            @Suppress("UNCHECKED_CAST")
            entitySelectCallbacks[esb.id] = onSelect as EntitySelectCallback<out IMentionable>
        }
        selects.add(esb.build())
    }

    fun roleSelect(builder: EntitySelectBuilder<Role>.() -> Unit) {
        check(buttons.size == 0) { "Can't mix selects and buttons in the same action row" }
        val esb = EntitySelectBuilder<Role>(SelectTarget.ROLE).apply(builder)
        val onSelect = esb.onSelect
        if (onSelect != null) {
            @Suppress("UNCHECKED_CAST")
            entitySelectCallbacks[esb.id] = onSelect as EntitySelectCallback<out IMentionable>
        }
        selects.add(esb.build())
    }

    fun channelSelect(vararg type: ChannelType, builder: EntitySelectBuilder<Channel>.() -> Unit) {
        check(buttons.size == 0) { "Can't mix selects and buttons in the same action row" }
        val esb = ChannelSelectBuilder(*type).apply(builder)
        val onSelect = esb.onSelect
        if (onSelect != null) {
            @Suppress("UNCHECKED_CAST")
            entitySelectCallbacks[esb.id] = onSelect as EntitySelectCallback<out IMentionable>
        }
        selects.add(esb.build())
    }

}

@PageDsl
class ButtonBuilder(
    val text: String
) : Builder<Button> {
    private val log = KotlinLogging.logger { }

    internal var id = UUID.randomUUID().toString()

    var style = ButtonStyle.SECONDARY
        set(value) {
            check(!isLink) { "Links cannot have their style changed" }
            field = value
        }

    internal var onClick: InteractionCallback? = null

    var emoji: Emoji? = null

    var enabled = true

    private var isLink = false

    fun onClick(hook: InteractionCallback) {
        check(!isLink) { "Can't define an onClick action for a button defined as a link" }
        if (this.onClick != null) {
            log.warn { "Re-defining on-click for button $id ($text)" }
        }
        this.onClick = hook
    }

    fun url(uri: URI) {
        this.id = uri.toASCIIString()
        this.style = ButtonStyle.LINK
        this.isLink = true
    }

    override fun build(): Button {
        return Button.of(style, id, text, emoji).withDisabled(!enabled)
    }
}

@PageDsl
open class EntitySelectBuilder<T : IMentionable>(
    private vararg val types: SelectTarget
) : Builder<EntitySelectMenu> {
    init {
        if (types.size == 2) {
            check(SelectTarget.ROLE in types && SelectTarget.USER in types) { "Invalid combination of select types: [$types]" }
        }
    }

    internal val id = UUID.randomUUID().toString()

    internal var onSelect: EntitySelectCallback<out T>? = null

    var placeholder: String? = null
    var disabled = false
    var min = 1
    var max = 1
    override fun build(): EntitySelectMenu {
        return EntitySelectMenu.create(id, types.toList()).setRequiredRange(min, max)
            .setPlaceholder(placeholder)
            .setDisabled(disabled).build()
    }

    fun onSelect(hook: EntitySelectCallback<T>) {
        this.onSelect = hook
    }
}

class ChannelSelectBuilder(private vararg val channelType: ChannelType) :
    EntitySelectBuilder<Channel>(SelectTarget.CHANNEL) {
    override fun build(): EntitySelectMenu {
        return super.build().createCopy().setChannelTypes(*channelType).build()
    }
}

@PageDsl
class StringSelectBuilder : Builder<SelectMenu> {

    internal val id = UUID.randomUUID().toString()

    internal var onChange: StringSelectCallback? = null

    var min = 1

    var max = 1

    var placeholder: String? = null

    var disabled = false

    internal var callbacks = mutableMapOf<String, InteractionCallback>()

    private val options = mutableListOf<SelectOption>()

    override fun build() =
        StringSelectMenu.create(id).addOptions(options).setPlaceholder(placeholder)
            .setRequiredRange(min, max).setDisabled(disabled).build()

    fun onChange(hook: StringSelectCallback) {
        this.onChange = hook
    }

    fun option(value: String, builder: StringSelectOptionBuilder.() -> Unit) {
        val optionBuilder = StringSelectOptionBuilder(value)
        builder(optionBuilder)
        options.add(optionBuilder.build())
        val callback = optionBuilder.onSelect
        if (callback != null) {
            callbacks[optionBuilder.id] = callback
        }
    }
}

@PageDsl
class StringSelectOptionBuilder(
    val value: String
) : Builder<SelectOption> {
    private val log = KotlinLogging.logger { }

    internal val id = UUID.randomUUID().toString()

    internal var onSelect: InteractionCallback? = null

    var default = false
    var description: String? = null
    var icon: Emoji? = null

    fun onSelect(hook: InteractionCallback) {
        if (this.onSelect != null) {
            log.warn { "Redefining onSelect for option $id ($value)" }
        }
        this.onSelect = hook
    }

    override fun build() =
        SelectOption.of(value, id).withDefault(default).withEmoji(icon).withDescription(description)
}