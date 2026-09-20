package bayern.kickner.demo.logging

import bayern.kickner.klogger.KLogger
import kotnexlib.ifTrue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Central Klogger setup for this demo: the console plus two custom interceptors, an in-memory audit trail and an
 * alert counter. Klogger dispatches every log call to all configured destinations, so the three run side by side
 * for the same call.
 */
object DemoLogging {

    /** One log call as the audit interceptor records it. */
    data class AuditEntry(val level: KLogger.Level, val tag: String, val message: String)

    private val alertCount = AtomicInteger(0)

    /**
     * Every log call since [configure], in order. The backing [CopyOnWriteArrayList] makes it safe to expose the
     * list itself instead of a copy: readers iterate over a snapshot while logging continues.
     */
    val audit: List<AuditEntry>
        field = CopyOnWriteArrayList<AuditEntry>()

    /** Number of WARN, ERROR and CRASH calls since [configure]. */
    val alerts: Int get() = alertCount.get()

    /** Registers the three destinations. Call once at startup, before the first log call. */
    fun configure() {
        KLogger.configure {
            logToConsole()

            logToCustom { level, tag, message -> audit.add(AuditEntry(level, tag, message)) }

            // A second, independent interceptor: destinations accumulate, nothing replaces the first one
            logToCustom { level, tag, message ->
                val isAlertWorthy = level >= KLogger.Level.WARN
                isAlertWorthy.ifTrue {
                    alertCount.incrementAndGet()
                    println("  ⚠ ALERT[$level] $tag: $message")
                }
            }

            minLevel = KLogger.Level.DEBUG
            debug = false
        }
    }
}
