package com.mrkirby153.interactionsmenu.testbot

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.botcore.coroutine.enableCoroutines
import com.mrkirby153.botcore.modal.ModalManager
import com.mrkirby153.botcore.modal.await
import com.mrkirby153.interactionmenus.Menu
import com.mrkirby153.interactionmenus.MenuManager
import com.mrkirby153.interactionmenus.StatefulMenu
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger { }
private val modalManager = ModalManager()

fun main() {
    val logger = LoggerFactory.getLogger("com.mrkirby153.interactionmenus") as Logger
    logger.level = Level.TRACE


    val token = System.getenv("BOT_TOKEN")?.trim()
    requireNotNull(token) { "Token must be provided" }

    log.info { "Starting up..." }
    val shardManager = DefaultShardManagerBuilder.createDefault(token).enableCoroutines().build()
    shardManager.shards.forEach { it.awaitReady() }
    log.info { "Ready!" }

    val dslExecutor = DslCommandExecutor()
    shardManager.addEventListener(dslExecutor.getListener())

    val menuManager = MenuManager()
    shardManager.addEventListener(menuManager)

    shardManager.addEventListener(modalManager)

    dslExecutor.registerCommands {
        slashCommand("test-menu") {
            run {
                val hook = deferReply(true).await()
                menuManager.show(makeMenu(), hook).await()
            }
        }
        slashCommand("test-menu2") {
            run {
                menuManager.show(statefulMenu(), deferReply(true).await()).await()
            }
        }
        slashCommand("test-menu3") {
            run {
                menuManager.show(timeoutMenu(), deferReply(true).await()).await()
            }
        }
        slashCommand("modal") {
            run {
                menuManager.show(modalMenu(), deferReply(true).await()).await()
            }
        }
    }

    val guilds = (System.getenv("SLASH_COMMAND_GUILDS")?.trim() ?: "").split(",")
    require(guilds.isNotEmpty()) { "Slash command guilds not provided" }
    log.info { "Committing slash commands to guilds: $guilds" }
    dslExecutor.commit(shardManager.shards[0], *guilds.toTypedArray()).thenRun {
        log.info { "Slash commands committed!" }
    }
}

enum class Pages {
    ONE,
    TWO,
    THREE
}

data class MenuState(var counter: Int = 0)

private fun statefulMenu(): StatefulMenu<Pages, MenuState> {
    return StatefulMenu(Pages.ONE, stateBuilder = ::MenuState) {
        page(Pages.ONE) {
            text("The count is: ${state.counter}")
            actionRow {
                button("-") {
                    onClick {
                        state.counter -= 1
                        markDirty()
                    }
                }
                button("+") {
                    onClick {
                        state.counter += 1
                        markDirty()
                    }
                }
                button("0") {
                    onClick {
                        state.counter = 0
                        markDirty()
                    }
                }
                button("E") {
                    onClick {
                        log.info { "This is an error!" }
                    }
                }
            }
        }
    }
}

private fun timeoutMenu() = Menu(Pages.ONE) {
    page(Pages.ONE) {
        renderTimeout(50, TimeUnit.MILLISECONDS)
        delay(1_000)
        text("This is text!")
    }
}

private fun modalMenu() = Menu(Pages.ONE) {
    page(Pages.ONE) {
        text("This is a modal!")
        actionRow {
            button("Open Modal") {
                onClick {
                    val modal = modalManager.build {
                        title = "test modal"
                        textInput("test") {
                            name = "Testing"
                            placeholder = "Enter something"
                        }
                    }
                    it.displayModal(modal)
                    val bla = modalManager.await(modal)
                    bla.reply("The end").await()
                    currentPage = Pages.TWO
                }
            }
        }
    }
    page(Pages.TWO) {
        text("This is the second page!")
        actionRow {
            button("Back") {
                onClick {
                    currentPage = Pages.ONE
                }
            }
        }
    }
}

private fun makeMenu(): Menu<Pages> {
    return Menu(Pages.ONE) {
        page(Pages.ONE) {
            text("This is the first page!")
            actionRow {
                button("Next Page!") {
                    onClick {
                        currentPage = Pages.TWO
                    }
                }
            }
        }
        page(Pages.TWO) {
            text("This is the second page!")
            actionRow {
                stringSelect {
                    placeholder = "Select a page to go to!"
                    option("One") {
                        onSelect {
                            currentPage = Pages.ONE
                        }
                    }
                    option("Two") {
                        onSelect {
                            currentPage = Pages.TWO
                        }
                    }
                    option("Three") {
                        onSelect {
                            delay(5_000)
                            currentPage = Pages.THREE
                        }
                    }
                }
            }
        }
        page(Pages.THREE) {
            text("This is the third page!")
            actionRow {
                button("First Page") {
                    onClick {
                        currentPage = Pages.ONE
                    }
                }
            }
        }
    }
}