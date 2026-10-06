package com.paulneja.quorvi.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object Glyphs {
    private fun glyph(name: String, vararg paths: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    val Search = glyph("search", "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z", "M15.5 15.5L20 20")
    val Close = glyph("close", "M6 6l12 12", "M18 6L6 18")
    val Plus = glyph("plus", "M12 5v14", "M5 12h14")
    val Gear = glyph("gear", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8")
    val Widgets = glyph("widgets", "M4 4h7v7H4z", "M13 4h7v7h-7z", "M4 13h7v7H4z", "M13 13h7v7h-7z")
    val Wallpaper = glyph("wallpaper", "M4 5h16v14H4z", "M4 16l5-5 4 4 3-3 4 4", "M15.5 8.5h.01")
    val Info = glyph("info", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 11v6", "M12 7.5h.01")
    val Trash = glyph("trash", "M5 7h14", "M10 7V5h4v2", "M7 7l1 13h8l1-13")
    val Remove = glyph("remove", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M8 12h8")
    val Lock = glyph("lock", "M6 11h12v9H6z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3")
    val Unlock = glyph("unlock", "M6 11h12v9H6z", "M8.5 11V8a3.5 3.5 0 0 1 6.8-1.2")
    val Hide = glyph("hide", "M3 12s3.5-6 9-6s9 6 9 6s-3.5 6-9 6s-9-6-9-6z", "M4 4l16 16")
    val Folder = glyph("folder", "M3 7h6l2 2h10v10H3z")
    val Edit = glyph("edit", "M4 20h4L19 9l-4-4L4 16z", "M13 7l4 4")
    val Studio = glyph("studio", "M12 3a9 9 0 1 0 0 18c1 0 1.5-.8 1.5-1.6c0-1.4 1-2.4 2.4-2.4H18a3 3 0 0 0 3-3c0-6-4-11-9-11z", "M7.5 11h.01M10 7.5h.01M14.5 7.5h.01")
    val Labs = glyph("labs", "M9 3h6", "M10 3v6L5 19h14L14 9V3")
    val Alarm = glyph("alarm", "M12 6a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M12 10v3l2 2", "M4 5l3-2M20 5l-3-2")
    val Contact = glyph("contact", "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M4 20c1.5-3.5 4.5-5 8-5s6.5 1.5 8 5")
    val Calc = glyph("calc", "M6 3h12v18H6z", "M9 7h6", "M9 12h.01M12 12h.01M15 12h.01M9 16h.01M12 16h.01M15 16h.01")
    val Undo = glyph("undo", "M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3")
    val Redo = glyph("redo", "M15 14l5-5-5-5", "M20 9H10a6 6 0 0 0 0 12h3")
    val Quorvi = glyph("quorvi", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M14.5 14.5l5 5")
    val Agent = glyph("agent", "M12 3l1.8 4.6L18.5 9l-4.7 1.4L12 15l-1.8-4.6L5.5 9l4.7-1.4z", "M18 15l.8 2.2L21 18l-2.2.8L18 21l-.8-2.2L15 18l2.2-.8z")
    val Stop = glyph("stop", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M9 9h6v6H9z")
    val Play = glyph("play", "M8 5v14l11-7z")
    val Pause = glyph("pause", "M8 5v14", "M16 5v14")
    val Next = glyph("next", "M6 6l9 6-9 6z", "M18 6v12")
    val Previous = glyph("previous", "M18 6l-9 6 9 6z", "M6 6v12")
    val Shortcut = glyph("shortcut", "M5 12h12", "M13 8l4 4-4 4")
    val Home = glyph("home", "M4 11l8-6.5 8 6.5", "M6.5 9.5V19h11V9.5")
    val Bento = glyph("bento", "M4 4h16v7H4z", "M4 13h7v7H4z", "M13 13h7v7h-7z")
    val Sliders = glyph("sliders", "M5 7h9M18 7h1M5 17h3M12 17h7", "M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M10 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z")
    val Apps = glyph("apps", "M7 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M17 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M7 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M17 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z")
    val Git = glyph("git", "M6 4a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M6 16a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M18 6a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M6 8v8M18 10c0 4-6 3-10.5 7")
    val PullRequest = glyph("pr", "M6 4a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M6 16a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M18 16a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M6 8v8M18 16V9a3 3 0 0 0-3-3h-4M13 4l-2 2 2 2")
    val Mic = glyph("mic", "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z", "M5 11a7 7 0 0 0 14 0M12 18v3M8 21h8")
    val Wave = glyph("wave", "M4 10v4M8 7v10M12 4v16M16 7v10M20 10v4")
    val Network = glyph("network", "M8 4v16M8 4L4.5 7.5M8 4l3.5 3.5", "M16 20V4M16 20l-3.5-3.5M16 20l3.5-3.5")
    val Chip = glyph("chip", "M6 6h12v12H6z", "M9.5 9.5h5v5h-5z", "M9 3v3M15 3v3M9 18v3M15 18v3M3 9h3M3 15h3M18 9h3M18 15h3")
    val Chevron = glyph("chevron", "M9 6l6 6-6 6")
    val Back = glyph("back", "M15 6l-6 6 6 6")
    val Down = glyph("down", "M6 9l6 6 6-6")
    val Wifi = glyph("wifi", "M3 9.5a13 13 0 0 1 18 0M6 13a8.5 8.5 0 0 1 12 0M9 16.5a4 4 0 0 1 6 0", "M12 19.4h.01")
    val Cellular = glyph("cellular", "M5 19v-3M9.5 19v-6M14 19V9M18.5 19V5")
    val Bluetooth = glyph("bluetooth", "M7 7l10 10-5 4V3l5 4L7 17")
    val Send = glyph("send", "M5 12h13", "M13 6l6 6-6 6")
    val Model = glyph("model", "M4 4h16v16H4z", "M9 9h6v6H9z")
    val Shield = glyph("shield", "M12 3l7 3v5c0 5-3.5 8.5-7 10c-3.5-1.5-7-5-7-10V6z")
    val Lan = glyph("lan", "M9 3h6v5H9z", "M3 16h6v5H3z", "M15 16h6v5h-6z", "M12 8v4M6 16v-4h12v4")
    val Bolt = glyph("bolt", "M13 3L5 13.5h6L10 21l8-10.5h-6z")
    val Hotspot = glyph("hotspot", "M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M8 8a5.6 5.6 0 0 0 0 8M16 8a5.6 5.6 0 0 1 0 8M5.5 5.5a9.2 9.2 0 0 0 0 13M18.5 5.5a9.2 9.2 0 0 1 0 13")
    val Vpn = glyph("vpn", "M5 11h14v9H5z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3")
    val Globe = glyph("globe", "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z", "M3.5 12h17M12 3.5c2.5 2.5 3.5 5.5 3.5 8.5s-1 6-3.5 8.5c-2.5-2.5-3.5-5.5-3.5-8.5s1-6 3.5-8.5z")
    val Terminal = glyph("terminal", "M3.5 5h17v14h-17z", "M7.5 10l3 2.5-3 2.5M12.5 15h4")
    val Brightness = glyph("brightness", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4")
    val Volume = glyph("volume", "M4 10v4h4l5 4V6L8 10z", "M16.5 9a4 4 0 0 1 0 6")
    val Torch = glyph("torch", "M8 3h8v4l-2 3v11h-4V10L8 7z", "M12 13v2")
    val Moon = glyph("moon", "M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z")
    val Rotate = glyph("rotate", "M4 12a8 8 0 0 1 14-5.3M20 12a8 8 0 0 1-14 5.3", "M18 3v4h-4M6 21v-4h4")
    val Leaf = glyph("leaf", "M5 19c0-8 5-13 14-14c-1 9-6 14-14 14z", "M5 19l7-7")
    val Hook = glyph("hook", "M12 3v9a4 4 0 1 1-4-4")
    val Bell = glyph("bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0")
    val Calendar = glyph("calendar", "M4 5h16v15H4z", "M4 10h16M9 3v4M15 3v4")
    val Note = glyph("note", "M5 4h14v16H5z", "M8.5 9h7M8.5 13h7M8.5 17h4")
    val Timer = glyph("timer", "M12 7a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M12 11v3.5M9.5 3h5M18.5 6.5l1.5-1.5")
    val Photo = glyph("photo", "M4 5h16v14H4z", "M4 16l5-5 4 4 3-3 4 4", "M15.5 9h.01")
    val Server = glyph("server", "M4 4h16v6H4z", "M4 14h16v6H4z", "M8 7h.01M8 17h.01")
    val House = glyph("house", "M4 11l8-7 8 7v9H4z", "M10 20v-5h4v5")
    val Sun = glyph("sun", "M12 7.5a4.5 4.5 0 1 0 0 9a4.5 4.5 0 1 0 0-9z", "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4")
    val Cloud = glyph("cloud", "M7.5 19h10a4 4 0 0 0 .5-8a6 6 0 0 0-11.5 1.5A3.3 3.3 0 0 0 7.5 19z")
    val CloudSun = glyph("cloudsun", "M8 3.5v1.6M3.5 8h1.6M4.8 4.8l1.1 1.1M11.2 4.8l-1.1 1.1", "M5.6 10.4A3.2 3.2 0 1 1 11 7.2", "M8.5 19h9a3.5 3.5 0 0 0 .4-7a5 5 0 0 0-9.6 1.4A2.8 2.8 0 0 0 8.5 19z")
    val Rain = glyph("rain", "M7.5 15h10a4 4 0 0 0 .5-8a6 6 0 0 0-11.5 1.5A3.3 3.3 0 0 0 7.5 15z", "M9 18l-1 2.5M13 18l-1 2.5M17 18l-1 2.5")
    val Snow = glyph("snow", "M7.5 15h10a4 4 0 0 0 .5-8a6 6 0 0 0-11.5 1.5A3.3 3.3 0 0 0 7.5 15z", "M9 19h.01M12.5 20h.01M16 19h.01")
    val Storm = glyph("storm", "M7.5 15h10a4 4 0 0 0 .5-8a6 6 0 0 0-11.5 1.5A3.3 3.3 0 0 0 7.5 15z", "M12.5 15l-2 3.5h3l-2 3.5")
    val Fog = glyph("fog", "M4 10h16M6 14h12M8 18h8")
    val Drop = glyph("drop", "M12 4s6 6.5 6 10.5a6 6 0 0 1-12 0C6 10.5 12 4 12 4z")
    val Check = glyph("check", "M5 12.5l4.5 4.5L19 7.5")
    val Menu = glyph("menu", "M4 7h16", "M4 12h10", "M4 17h16")
    val Compose = glyph("compose", "M12 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-6", "M17.5 3.5l3 3L12 15l-4 1l1-4z")
    val Copy = glyph("copy", "M9 9h11v11H9z", "M5 15H4V4h11v1")
    val Share = glyph("share", "M12 3v12", "M7.5 7.5L12 3l4.5 4.5", "M5 13v7h14v-7")
    val Retry = glyph("retry", "M4 12a8 8 0 1 0 2.5-5.8", "M4 4v4h4")
    val Brain = glyph("brain", "M9 4a3 3 0 0 0-3 3a3 3 0 0 0-2 5a3 3 0 0 0 2 5a3 3 0 0 0 6 1V6a2 2 0 0 0-3-2z", "M15 4a3 3 0 0 1 3 3a3 3 0 0 1 2 5a3 3 0 0 1-2 5a3 3 0 0 1-6 1")
    val Sparkle = glyph("sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z", "M19 16l.7 1.8l1.8.7l-1.8.7L19 21l-.7-1.8l-1.8-.7l1.8-.7z")
    val Memory = glyph("memory", "M12 3a7 7 0 0 0-4 12.7V19h8v-3.3A7 7 0 0 0 12 3z", "M9.5 21.5h5")
    val Orchestra = glyph("orchestra", "M12 4a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z", "M5 15a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z", "M19 15a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z", "M10.5 8.5L6.5 15M13.5 8.5l4 6.5M7.5 17.5h9")
    val Pen = glyph("pen", "M4 20l4-1L19 8l-3-3L5 16z", "M14 7l3 3")
    val Image = glyph("image", "M4 5h16v14H4z", "M4 16l5-5 4 4 3-3 4 4", "M15.5 8.5h.01")
    val File = glyph("file", "M6 3h8l4 4v14H6z", "M14 3v4h4")
}
