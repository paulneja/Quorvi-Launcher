package com.paulneja.quorvi.core.command

import android.provider.Settings

data class SettingEntry(val action: String, val en: String, val es: String, val keywords: List<String>)

object SettingsCatalog {
    val entries = listOf(
        SettingEntry(Settings.ACTION_WIFI_SETTINGS, "Wi‑Fi", "Wi‑Fi", listOf("wifi", "wi-fi", "red", "internet", "network")),
        SettingEntry(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth", "Bluetooth", listOf("bluetooth", "bt", "auriculares", "headphones")),
        SettingEntry(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane mode", "Modo avión", listOf("avion", "airplane", "vuelo", "flight")),
        SettingEntry(Settings.ACTION_DATA_ROAMING_SETTINGS, "Mobile data", "Datos móviles", listOf("datos", "data", "movil", "mobile", "roaming", "4g", "5g")),
        SettingEntry(Settings.ACTION_DISPLAY_SETTINGS, "Display", "Pantalla", listOf("pantalla", "display", "brillo", "brightness", "tema", "oscuro", "dark")),
        SettingEntry(Settings.ACTION_SOUND_SETTINGS, "Sound", "Sonido", listOf("sonido", "sound", "volumen", "volume", "tono", "ringtone", "vibracion")),
        SettingEntry(Settings.ACTION_BATTERY_SAVER_SETTINGS, "Battery saver", "Ahorro de batería", listOf("bateria", "battery", "ahorro", "saver", "energia")),
        SettingEntry(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Location", "Ubicación", listOf("ubicacion", "location", "gps")),
        SettingEntry(Settings.ACTION_APPLICATION_SETTINGS, "Apps", "Aplicaciones", listOf("apps", "aplicaciones", "applications")),
        SettingEntry(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "Storage", "Almacenamiento", listOf("almacenamiento", "storage", "espacio", "memoria")),
        SettingEntry(Settings.ACTION_SECURITY_SETTINGS, "Security", "Seguridad", listOf("seguridad", "security", "bloqueo", "lock", "huella", "fingerprint")),
        SettingEntry(Settings.ACTION_PRIVACY_SETTINGS, "Privacy", "Privacidad", listOf("privacidad", "privacy", "permisos", "permissions")),
        SettingEntry(Settings.ACTION_ACCESSIBILITY_SETTINGS, "Accessibility", "Accesibilidad", listOf("accesibilidad", "accessibility", "talkback")),
        SettingEntry(Settings.ACTION_LOCALE_SETTINGS, "Languages", "Idiomas", listOf("idioma", "language", "lenguaje", "locale")),
        SettingEntry(Settings.ACTION_DATE_SETTINGS, "Date & time", "Fecha y hora", listOf("fecha", "hora", "date", "time", "zona")),
        SettingEntry(Settings.ACTION_NFC_SETTINGS, "NFC", "NFC", listOf("nfc", "pago", "contactless")),
        SettingEntry(Settings.ACTION_WIRELESS_SETTINGS, "Network & internet", "Redes e internet", listOf("redes", "hotspot", "anclaje", "tethering", "vpn")),
        SettingEntry(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, "Do not disturb", "No molestar", listOf("molestar", "disturb", "dnd", "silencio", "focus")),
        SettingEntry(Settings.ACTION_APP_NOTIFICATION_SETTINGS, "Notifications", "Notificaciones", listOf("notificaciones", "notifications")),
        SettingEntry(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "Developer options", "Opciones de desarrollador", listOf("desarrollador", "developer", "adb", "depuracion", "debug")),
        SettingEntry(Settings.ACTION_DEVICE_INFO_SETTINGS, "About phone", "Información del teléfono", listOf("acerca", "about", "informacion", "version", "android")),
        SettingEntry(Settings.ACTION_HOME_SETTINGS, "Default home app", "App de inicio predeterminada", listOf("launcher", "inicio", "home", "predeterminada")),
        SettingEntry(Settings.ACTION_SETTINGS, "Settings", "Ajustes", listOf("ajustes", "settings", "configuracion", "config")),
    )
}
