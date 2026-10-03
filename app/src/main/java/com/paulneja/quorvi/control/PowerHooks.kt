package com.paulneja.quorvi.control

data class HookParam(val name: String, val hint: String, val default: String = "")

data class PowerHook(
    val id: String,
    val title: String,
    val description: String,
    val level: RootLevel,
    val template: String,
    val params: List<HookParam> = emptyList(),
    val revert: String? = null,
) {
    fun render(args: Map<String, String>): String {
        var cmd = template
        params.forEach { p ->
            val raw = args[p.name]?.ifBlank { p.default } ?: p.default
            if (p.name == "target" && raw.isBlank()) { cmd = cmd.replace("{${p.name}}", ""); return@forEach }
            require(raw.isNotBlank()) { "Missing \"${p.name}\"" }

            require(raw.none { it in "'\"`$;&|<>\n\r(){}" }) { "\"${p.name}\" has characters that are not allowed" }
            cmd = cmd.replace("{${p.name}}", raw)
        }
        return cmd
    }
}

object PowerHooks {
    val catalog: List<PowerHook> = listOf(

        PowerHook("disable_app", "Freeze an app", "Disable any app, even system ones, without uninstalling.", RootLevel.CONTROL,
            "pm disable-user --user 0 {package}", listOf(HookParam("package", "package name")), revert = "pm enable {package}"),
        PowerHook("force_stop", "Force stop an app", "Kill an app and its background work now.", RootLevel.CONTROL,
            "am force-stop {package}", listOf(HookParam("package", "package name"))),
        PowerHook("clear_data", "Wipe an app's data", "Reset an app to a fresh install.", RootLevel.CONTROL,
            "pm clear {package}", listOf(HookParam("package", "package name"))),
        PowerHook("grant_permission", "Grant any permission", "Force-grant a runtime or even signature permission to an app.", RootLevel.CONTROL,
            "pm grant {package} {permission}", listOf(HookParam("package", "package name"), HookParam("permission", "android.permission.X"))),
        PowerHook("immersive", "Full immersive mode", "Hide the status and nav bars everywhere.", RootLevel.CONTROL,
            "settings put global policy_control immersive.full=*", revert = "settings put global policy_control null"),

        PowerHook("density", "Change screen density", "Make everything bigger or smaller (dpi).", RootLevel.MODIFY,
            "wm density {dpi}", listOf(HookParam("dpi", "e.g. 420", "420")), revert = "wm density reset"),
        PowerHook("resolution", "Change resolution", "Set a custom WxH resolution.", RootLevel.MODIFY,
            "wm size {size}", listOf(HookParam("size", "e.g. 1080x2400")), revert = "wm size reset"),
        PowerHook("peak_refresh", "Force peak refresh rate", "Pin the display to its highest refresh rate.", RootLevel.MODIFY,
            "settings put system min_refresh_rate 120; settings put system peak_refresh_rate 120"),
        PowerHook("cpu_governor", "Set CPU governor", "Force a governor on every core (e.g. performance).", RootLevel.MODIFY,
            "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo {governor} > \$g; done", listOf(HookParam("governor", "performance / schedutil", "performance"))),
        PowerHook("setprop", "Set a system property", "resetprop any build or feature prop (persists until reboot unless -p).", RootLevel.MODIFY,
            "resetprop {prop} {value}", listOf(HookParam("prop", "e.g. debug.hwui.renderer"), HookParam("value", "value"))),
        PowerHook("private_dns", "Set private DNS", "Point DNS-over-TLS at any provider.", RootLevel.MODIFY,
            "settings put global private_dns_mode hostname; settings put global private_dns_specifier {host}", listOf(HookParam("host", "e.g. dns.adguard-dns.com")),
            revert = "settings put global private_dns_mode off"),
        PowerHook("remount_write", "Write to /system", "Remount system read-write and drop a file into it.", RootLevel.MODIFY,
            "mount -o rw,remount / && mount -o rw,remount /system 2>/dev/null; echo {content} > {path}", listOf(HookParam("path", "/system/…"), HookParam("content", "file contents"))),

        PowerHook("selinux_permissive", "SELinux permissive", "Turn SELinux off until reboot. Lowers security.", RootLevel.EXPERIMENTAL,
            "setenforce 0", revert = "setenforce 1"),
        PowerHook("boot_script", "Install a boot script", "Run a script on every boot (Magisk service.d).", RootLevel.EXPERIMENTAL,
            "mkdir -p /data/adb/service.d && printf '#!/system/bin/sh\\n{script}\\n' > /data/adb/service.d/{name}.sh && chmod 755 /data/adb/service.d/{name}.sh",
            listOf(HookParam("name", "script name"), HookParam("script", "shell to run at boot"))),
        PowerHook("flash_module", "Flash a Magisk module", "Install a module zip already on the device.", RootLevel.EXPERIMENTAL,
            "magisk --install-module {zip}", listOf(HookParam("zip", "/sdcard/…zip"))),
        PowerHook("overwrite_hosts", "Replace the hosts file", "System-wide ad/host blocking by overwriting /system/etc/hosts.", RootLevel.EXPERIMENTAL,
            "mount -o rw,remount /system 2>/dev/null; cp {source} /system/etc/hosts", listOf(HookParam("source", "/sdcard/hosts"))),
        PowerHook("reboot", "Reboot", "Reboot the phone (also recovery/bootloader).", RootLevel.EXPERIMENTAL,
            "reboot {target}", listOf(HookParam("target", "blank, recovery or bootloader", ""))),
    )

    fun byId(id: String) = catalog.firstOrNull { it.id == id }
}
