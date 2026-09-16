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
    val Undo = glyph("undo", "M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3")
    val Redo = glyph("redo", "M15 14l5-5-5-5", "M20 9H10a6 6 0 0 0 0 12h3")
    val Quorvi = glyph("quorvi", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M14.5 14.5l5 5")
    val Agent = glyph("agent", "M12 3l1.8 4.6L18.5 9l-4.7 1.4L12 15l-1.8-4.6L5.5 9l4.7-1.4z", "M18 15l.8 2.2L21 18l-2.2.8L18 21l-.8-2.2L15 18l2.2-.8z")
    val Shortcut = glyph("shortcut", "M5 12h12", "M13 8l4 4-4 4")
    val Bento = glyph("bento", "M4 4h16v7H4z", "M4 13h7v7H4z", "M13 13h7v7h-7z")
    val Back = glyph("back", "M15 6l-6 6 6 6")
    val Shield = glyph("shield", "M12 3l7 3v5c0 5-3.5 8.5-7 10c-3.5-1.5-7-5-7-10V6z")
    val Photo = glyph("photo", "M4 5h16v14H4z", "M4 16l5-5 4 4 3-3 4 4", "M15.5 9h.01")
}
