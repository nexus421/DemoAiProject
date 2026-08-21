package bayern.kickner.demo.logging

import bayern.kickner.klogger.KLogger
import kotnexlib.ifTrue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Central Klogger setup for this demo.
 *
 * Klogger dispatches every log call to *all* configured destinations, so the three destinations
 * below (console, audit trail, alert watcher) all run for every single log call, side by side.
 * That's what "multiple destinations/interceptors at once" means in practice.
 */
object DemoLogging {

    data class AuditEntry(val level: KLogger.Level, val tag: String, val message: String)

    private val alertCount = AtomicInteger(0)

    val audit: List<AuditEntry>
        field = CopyOnWriteArrayList<AuditEntry>()
    val alerts: Int get() = alertCount.get()

    fun configure() {
        KLogger.configure {
            // 1) Built-in destination: plain STDIO (stdout, stderr for ERROR/CRASH).
            logToConsole()

            // 2) Custom interceptor #1: collect every log line into an in-memory audit trail.
            logToCustom { level, tag, message -> audit.add(AuditEntry(level, tag, message)) }

            // 3) Custom interceptor #2: a second, independent interceptor that only reacts to
            //    WARN/ERROR/CRASH. Proves that any number of interceptors can be combined freely.
            logToCustom { level, tag, message ->
                val isAlertWorthy = level == KLogger.Level.WARN || level == KLogger.Level.ERROR || level == KLogger.Level.CRASH
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
