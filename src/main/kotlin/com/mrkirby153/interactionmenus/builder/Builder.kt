package com.mrkirby153.interactionmenus.builder

/**
 * Common interface for builders
 */
internal interface Builder<T> {

    /**
     * Builds the object and returns it
     */
    fun build(): T
}