package bayern.kickner.demo.vault

import bayern.kickner.klogger.infoLog
import kotnexlib.IterableSize
import kotnexlib.forEachDoLast
import kotnexlib.handleSizes
import kotnexlib.splitFilter

object VaultReport {

    fun print(users: List<UserAccount>) {
        infoLog { "--- Vault report: collection extensions (handleSizes / splitFilter / forEachDoLast) ---" }

        when (val sizeInfo = users.handleSizes()) {
            is IterableSize.IsEmpty -> infoLog { "No users registered yet." }
            is IterableSize.IsOne -> infoLog { "Exactly one user registered: ${sizeInfo.entry.username}" }
            is IterableSize.HasMany -> infoLog { "${sizeInfo.entries.size} users registered." }
        }

        val (modern, legacy) = users.splitFilter { it is UserAccount.GcmAccount }
        infoLog { "Modern AES-GCM accounts: ${modern.size}, legacy AES-ECB accounts: ${legacy.size}" }

        users.forEachDoLast { user, isLast ->
            val branch = if (isLast) "└─" else "├─"
            println("  $branch ${user.username} [${user.cipherLabel}]")
        }
    }
}
