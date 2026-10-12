package com.paulneja.quorvi.agent.policy

object Watch {
    private val commandTools = setOf("shell.exec", "ssh.exec", "remote.run", "android.shell")
    private val rootTools = setOf("android.priv_shell", "android.root", "android.power_hook")
    private val root = Regex("(^|[\\s;&|(\"'`\\[,])(su|sudo|tsu|doas)([\\s\"',\\]]|$)")

    private val dangers = listOf(
        Regex("\\brm\\s+(-[a-zA-Z]*([rR][a-zA-Z]*f|f[a-zA-Z]*[rR])|-[rR]\\s+-f|-f\\s+-[rR]|--recursive|-[a-zA-Z]*[rR]\\s+(/|~|\\*))") to "deletes files recursively and without asking",
        Regex("\\bmkfs|\\bdd\\b[^\\n]*\\bof=/dev/|>\\s*/dev/(sd|block|mmcblk|nvme)") to "writes straight to a disk device",
        Regex("\\b(curl|wget)\\b[^|\\n]*\\|\\s*(sudo\\s+)?(ba|z|da)?sh\\b") to "runs a script downloaded from the internet",
        Regex("base64\\s+(-d|--decode)[^|\\n]*\\|\\s*(ba)?sh") to "runs hidden (encoded) commands",
        Regex(":\\(\\)\\s*\\{") to "can hang the system (fork bomb)",
        Regex("\\b(shutdown|reboot|poweroff|halt)\\b") to "restarts or shuts the machine down",
        Regex("\\bchmod\\s+-R\\s+0?777|\\bchown\\s+-R\\b") to "changes permissions of many files at once",
        Regex("\\b(ip6?tables)\\s+-F\\b") to "clears the firewall",
        Regex("\\bkill\\s+-9\\s+-1\\b|\\bkillall\\b|\\bpkill\\s+-9\\b") to "kills many processes",
        Regex("\\bcrontab\\s+-r\\b|\\bhistory\\s+-c\\b") to "erases schedules or history",
        Regex("\\bnc\\b[^\\n]*\\s-e\\s|/dev/tcp/") to "opens a remote shell",
        Regex("\\bpm\\s+(uninstall|clear|disable)|\\bsettings\\s+put\\b") to "changes apps or system settings",
    )
    private val outside = Regex("(^|[\\s\"'=:])(/data/data/(?!quorvi\\.)|/data/app|/data/user/|/sdcard/Android/data|~/\\.ssh|\\.ssh/|id_rsa|\\.gnupg)")

    fun usesRoot(tool: String, resource: String): Boolean = tool in rootTools || (tool in commandTools && root.containsMatchIn(resource))

    fun unusual(tool: String, resource: String): String? {
        if (tool !in commandTools) return null
        dangers.firstOrNull { (re, _) -> re.containsMatchIn(resource) }?.let { return "This command ${it.second}." }
        if (outside.containsMatchIn(resource)) return "This command reaches files outside the job's workspace."
        return null
    }
}
