package com.mrkirby153.interactionmenus

import com.mrkirby153.interactionmenus.MenuManager.MenuCallbackResult
import kotlinx.coroutines.channels.Channel
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.modals.Modal

class MenuInteractionHook(hook: InteractionHook, private val editChannel: Channel<MenuCallbackResult?>) : InteractionHook by hook {

    internal var displayedModal = false

    suspend fun displayModal(modal: Modal) {
        check(!displayedModal) { "Cannot display multiple modals" }
        displayedModal = true
        editChannel.send(MenuCallbackResult.Modal(modal))
    }
}