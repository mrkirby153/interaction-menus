package com.mrkirby153.interactionmenus.spring

import com.mrkirby153.interactionmenus.MenuManager
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit


/**
 * Enables a [MenuManager] bean for spring
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Import(MenuManagerAutoConfiguration::class)
annotation class EnableMenuManager

/**
 * Automatically configures a [MenuManager] in a spring context
 */
@Configuration
open class MenuManagerAutoConfiguration(
    @Autowired(required = false) private val threadFactory: ThreadFactory?,
    @Autowired(required = false) private val shardManager: ShardManager?,
    @Value("\${interactionmenus.manager.gc.period:1}") private val gcPeriod: Long,
    @Value("\${interactionmenus.manager.gc.interval:SECONDS}") private val gcUnits: TimeUnit,
    @Value("\${interactionmenus.manager.gc.priority:${Thread.MIN_PRIORITY}}") private val gcPriority: Int,
) {
    @Bean
    @ConditionalOnMissingBean
    open fun menuManager(): MenuManager {
        val manager = MenuManager(this.threadFactory, this.gcPeriod, this.gcUnits, this.gcPriority)
        this.shardManager?.addEventListener(manager)
        return manager
    }
}