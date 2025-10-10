package com.shibaprasadsahu.billingkit

/**
 * Log level for BillingKit logging
 *
 * Controls the verbosity of logging output. Only logs at or above the configured level will be printed.
 *
 * @since 1.0.0
 */
enum class LogLevel {
    /**
     * Verbose logging - all details including fine-grained informational events
     */
    VERBOSE,

    /**
     * Debug logging - detailed information for debugging
     */
    DEBUG,

    /**
     * Info logging - general informational messages
     */
    INFO,

    /**
     * Warning logging - potential issues or important notices
     */
    WARN,

    /**
     * Error logging - errors and critical issues only
     */
    ERROR,

    /**
     * No logging - completely disables all logs
     */
    NONE
}
