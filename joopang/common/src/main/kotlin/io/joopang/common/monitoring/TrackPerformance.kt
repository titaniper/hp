package io.joopang.services.common.monitoring

/**
 * Marks methods where fine-grained performance needs to be logged via AOP.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TrackPerformance(
    val label: String = "",
)
