package com.paulneja.quorvi.launcher.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

data class IconPack(val packageName: String, val label: String)

class IconPackMapping(private val resources: Resources, private val packageName: String, private val map: Map<ComponentName, String>) {
    val size: Int get() = map.size

    @SuppressLint("DiscouragedApi")
    fun drawable(component: ComponentName, densityDpi: Int): Drawable? {
        val name = map[component] ?: return null
        val id = resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 } ?: return null
        return runCatching { resources.getDrawableForDensity(id, densityDpi, null) }.getOrNull()
    }
}

@Singleton
class IconPacks @Inject constructor(@ApplicationContext private val context: Context) {
    private val pm = context.packageManager
    private var cached: Pair<String, IconPackMapping?>? = null

    fun installed(): List<IconPack> = THEME_ACTIONS.flatMap { action ->
        pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA).map { it.activityInfo.packageName }
    }.distinct().map { pkg ->
        IconPack(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg))
    }.sortedBy { it.label.lowercase() }

    @Synchronized
    fun mapping(packageName: String): IconPackMapping? {
        cached?.let { (pkg, map) -> if (pkg == packageName) return map }
        val map = runCatching { load(packageName) }.getOrNull()
        cached = packageName to map
        return map
    }

    @SuppressLint("DiscouragedApi")
    private fun load(packageName: String): IconPackMapping? {
        val resources = pm.getResourcesForApplication(packageName)
        val parser: XmlPullParser = resources.getIdentifier("appfilter", "xml", packageName).takeIf { it != 0 }?.let(resources::getXml)
            ?: runCatching { resources.assets.open("appfilter.xml") }.getOrNull()?.let { stream -> Xml.newPullParser().apply { setInput(stream, "UTF-8") } }
            ?: return null
        return IconPackMapping(resources, packageName, parseAppFilter(parser))
    }

    companion object {
        val THEME_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")

        @Throws(XmlPullParserException::class, IOException::class)
        fun parseAppFilter(parser: XmlPullParser): Map<ComponentName, String> {
            val result = mutableMapOf<ComponentName, String>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    parseComponent(component)?.let { if (!drawable.isNullOrBlank()) result[it] = drawable }
                }
                event = parser.next()
            }
            return result
        }

        fun parseComponent(raw: String?): ComponentName? {
            val inner = raw?.trim()?.removePrefix("ComponentInfo{")?.removeSuffix("}") ?: return null
            val slash = inner.indexOf('/')
            if (slash <= 0 || slash == inner.lastIndex) return null
            val pkg = inner.substring(0, slash)
            val cls = inner.substring(slash + 1).let { if (it.startsWith(".")) pkg + it else it }
            return ComponentName(pkg, cls)
        }
    }
}
