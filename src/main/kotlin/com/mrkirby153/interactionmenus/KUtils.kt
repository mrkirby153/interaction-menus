package com.mrkirby153.interactionmenus

import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.requests.RestAction

internal suspend fun <T> RestAction<T>.await(): T = submit().await()