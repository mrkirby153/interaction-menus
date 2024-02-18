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


/**
 * Page Builder DSL marker annotation
 */
@DslMarker
annotation class PageDsl

/**
 * A page builder is used to construct a page of a menu
 */
@PageDsl
class PageBuilder {

    private var text = ""

    private val embeds = mutableListOf<MessageEmbed>()
    private val actionRows = mutableListOf<ActionRow>()
    internal val interactionCallbacks = mutableMapOf<String, InteractionCallback>()
    internal val stringSelectCallbacks = mutableMapOf<String, StringSelectCallback>()
    internal val entitySelectCallbacks =
        mutableMapOf<String, EntitySelectCallback<out IMentionable>>()

    internal var onShowHook: (() -> Unit)? = null

    /**
     * Builds the page into the given [builder]. This modifies the builder in-place.
     */
    fun build(builder: AbstractMessageBuilder<*, *>) {
        builder.setContent(text)
        builder.setComponents(actionRows)
        builder.setEmbeds(embeds)
    }

    /**
     * Adds an embed to the page
     */
    fun addEmbed(embed: MessageEmbed) {
        embeds.add(embed)
    }

    /**
     * Adds an action row to the page
     */
    fun actionRow(builder: ActionRowBuilder.() -> Unit) {
        val arb = ActionRowBuilder().apply(builder)
        actionRows.add(arb.build())
        interactionCallbacks.putAll(arb.interactionCallbacks)
        stringSelectCallbacks.putAll(arb.stringSelectCallbacks)
        entitySelectCallbacks.putAll(arb.entitySelectCallbacks)
    }

    /**
     * Sets the text displayed on this page
     */
    fun text(builder: StringBuilder.() -> Unit) {
        text = StringBuilder().apply(builder).toString()
    }

    /**
     * Sets the text displayed on this page to the given [text]
     */
    fun text(text: String) {
        this.text = text
    }

    /**
     * Runs the provided [callback] when the page is first rendered
     */
    fun onShow(callback: () -> Unit) {
        this.onShowHook = callback
    }
}

/**
 * A builder for an action row
 */
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

    /**
     * Adds a button with the given [text] to the action row
     */
    fun button(text: String, id: String? = null, builder: ButtonBuilder.() -> Unit) {
        check(selects.size == 0) { "Can't mix buttons and selects in the same action row" }
        val bb = ButtonBuilder(text, id).apply(builder)
        val callback = bb.onClick
        if (callback != null) {
            interactionCallbacks[bb.id] = callback
        }
        buttons.add(bb.build())
    }

    /**
     * Adds a select with customizable strings to the action row
     */
    fun stringSelect(id: String? = null, builder: StringSelectBuilder.() -> Unit) {
        check(buttons.size == 0) { "Can't mix selects and buttons in the same action row" }
        val ssb = StringSelectBuilder(id).apply(builder)
        val onChange = ssb.onChange
        if (onChange != null)
            stringSelectCallbacks[ssb.id] = onChange
        interactionCallbacks.putAll(ssb.callbacks)
        selects.add(ssb.build())
    }

    /**
     * Adds a mentionable select (Roles + Users) to the action row
     */
    fun mentionableSelect(id: String? = null, builder: EntitySelectBuilder<IMentionable>.() -> Unit) {
        check(buttons.size == 0) { "Can't mix selects and buttons in the same action row" }
        val esb =
            EntitySelectBuilder<IMentionable>(SelectTarget.ROLE, SelectTarget.USER, id = id).apply(builder)
        val onSelect = esb.onSelect
        if (onSelect != null) {
            entitySelectCallbacks[esb.id] = onSelect
        }
        selects.add(esb.build())
    }

    /**
     * Adds a user select to the action row
     */
    fun userSelect(id: String? = null, builder: EntitySelectBuilder<User>.() -> Unit) {
        check(buttons.size == 0) { "Can't mix selects and buttons in the same action row" }
        val esb = EntitySelectBuilder<User>(SelectTarget.ROLE, SelectTarget.USER, id = id).apply(builder)
        val onSelect = esb.onSelect
        if (onSelect != null) {
            @Suppress("UNCHECKED_CAST")
            entitySelectCallbacks[esb.id] = onSelect as EntitySelectCallback<out IMentionable>
        }
        selects.add(esb.build())
    }

    /**
     * Adds a role select to the action row
     */
    fun roleSelect(id: String? = null, builder: EntitySelectBuilder<Role>.() -> Unit) {
        check(buttons.size == 0) { "Can't mix selects and buttons in the same action row" }
        val esb = EntitySelectBuilder<Role>(SelectTarget.ROLE, id = id).apply(builder)
        val onSelect = esb.onSelect
        if (onSelect != null) {
            @Suppress("UNCHECKED_CAST")
            entitySelectCallbacks[esb.id] = onSelect as EntitySelectCallback<out IMentionable>
        }
        selects.add(esb.build())
    }

    /**
     * Adds a channel select to the action row
     */
    fun channelSelect(id: String? = null, vararg type: ChannelType, builder: ChannelSelectBuilder.() -> Unit) {
        check(buttons.size == 0) { "Can't mix selects and buttons in the same action row" }
        val esb = ChannelSelectBuilder(*type, id = id).apply(builder)
        val onSelect = esb.onSelect
        if (onSelect != null) {
            @Suppress("UNCHECKED_CAST")
            entitySelectCallbacks[esb.id] = onSelect as EntitySelectCallback<out IMentionable>
        }
        selects.add(esb.build())
    }

}

/**
 * A builder for a button in an action row
 */
@PageDsl
class ButtonBuilder(
    val text: String,
    id: String? = null
) : Builder<Button> {
    private val log = KotlinLogging.logger { }

    internal var id = id ?: UUID.randomUUID().toString()

    /**
     * The style of the button
     */
    var style = ButtonStyle.SECONDARY
        set(value) {
            check(!isLink) { "Links cannot have their style changed" }
            field = value
        }

    internal var onClick: InteractionCallback? = null

    /**
     * The emoji to display in the button
     */
    var emoji: Emoji? = null

    /**
     * If the button is enabled
     */
    var enabled = true

    private var isLink = false

    /**
     * Registers the given [hook] as the onClick callback for this button
     */
    fun onClick(hook: InteractionCallback) {
        check(!isLink) { "Can't define an onClick action for a button defined as a link" }
        if (this.onClick != null) {
            log.warn { "Re-defining on-click for button $id ($text)" }
        }
        this.onClick = hook
    }

    /**
     * Sets the URL that this button will open when cliked
     */
    fun url(uri: URI) {
        this.id = uri.toASCIIString()
        this.style = ButtonStyle.LINK
        this.isLink = true
    }

    override fun build(): Button {
        return Button.of(style, id, text, emoji).withDisabled(!enabled)
    }
}

/**
 * Parent class for all select builders specifying common options
 */
sealed class SelectBuilder {
    /**
     * The placeholder that is displayed when nothing is selected
     */
    var placeholder: String? = null

    /**
     * If this select is disabled
     */
    var disabled = false

    /**
     * The minimum number of options that can be selected
     */
    var min = 1
        set(value) {
            check(value <= max) { "Min must be less than or equal to max" }
            field = value
        }

    /**
     * The maximum number of options that can be selected
     */
    var max = 1
        set(value) {
            check(max >= min) { "Max must be greater than or equal to min" }
            field = value
        }
}

/**
 * A builder for an entity select
 */
@PageDsl
open class EntitySelectBuilder<T : IMentionable>(
    private vararg val types: SelectTarget,
    id: String? = null
) : SelectBuilder(), Builder<EntitySelectMenu> {
    init {
        if (types.size == 2) {
            check(SelectTarget.ROLE in types && SelectTarget.USER in types) { "Invalid combination of select types: [$types]" }
        }
    }

    internal val id = id ?: UUID.randomUUID().toString()

    internal var onSelect: EntitySelectCallback<out T>? = null

    override fun build(): EntitySelectMenu {
        return EntitySelectMenu.create(id, types.toList()).setRequiredRange(min, max)
            .setPlaceholder(placeholder)
            .setDisabled(disabled).build()
    }

    /**
     * Registers the given [hook] as a callback for when the select is changed
     */
    fun onSelect(hook: EntitySelectCallback<T>) {
        this.onSelect = hook
    }
}

/**
 * A builder for selecting channels
 */
class ChannelSelectBuilder(
    private vararg val channelType: ChannelType,
    id: String? = null
) :
    EntitySelectBuilder<Channel>(SelectTarget.CHANNEL, id = id) {
    override fun build(): EntitySelectMenu {
        return super.build().createCopy().setChannelTypes(*channelType).build()
    }
}

/**
 * A string select builder
 */
@PageDsl
class StringSelectBuilder(
    id: String? = null
) : SelectBuilder(), Builder<SelectMenu> {

    internal val id = id ?: UUID.randomUUID().toString()

    internal var onChange: StringSelectCallback? = null

    internal var callbacks = mutableMapOf<String, InteractionCallback>()

    private val options = mutableListOf<SelectOption>()

    override fun build() =
        StringSelectMenu.create(id).addOptions(options).setPlaceholder(placeholder)
            .setRequiredRange(min, max).setDisabled(disabled).build()

    /**
     * Registers the given [hook] that is fired when this select changes
     */
    fun onChange(hook: StringSelectCallback) {
        this.onChange = hook
    }

    /**
     * Adds an option of the given [value] to this select
     */
    fun option(value: String, id: String? = null, builder: (StringSelectOptionBuilder.() -> Unit)? = null) {
        val optionBuilder = StringSelectOptionBuilder(value, id)
        builder?.invoke(optionBuilder)
        options.add(optionBuilder.build())
        val callback = optionBuilder.onSelect
        if (callback != null) {
            callbacks[optionBuilder.id] = callback
        }
    }
}

/**
 * A builder for string select options
 */
@PageDsl
class StringSelectOptionBuilder(
    /**
     * The name of the option
     */
    val value: String,
    id: String? = null
) : Builder<SelectOption> {
    private val log = KotlinLogging.logger { }

    internal val id = id ?: UUID.randomUUID().toString()

    internal var onSelect: InteractionCallback? = null

    /**
     * If this option is default selected
     */
    var default = false

    /**
     * The option's description
     */
    var description: String? = null

    /**
     * The option's emoji icon
     */
    var icon: Emoji? = null

    /**
     * Registers the given [hook] as a callback fired when this item is selected
     */
    fun onSelect(hook: InteractionCallback) {
        if (this.onSelect != null) {
            log.warn { "Redefining onSelect for option $id ($value)" }
        }
        this.onSelect = hook
    }

    override fun build() =
        SelectOption.of(value, id).withDefault(default).withEmoji(icon).withDescription(description)
}