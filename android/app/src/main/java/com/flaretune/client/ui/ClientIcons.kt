package com.flaretune.client.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** One small, consistent 24dp geometric icon family. */
object ClientIcons {
    private fun lines(name: String, strokeWidth: Float = 2.2f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun filled(
        name: String,
        fillType: PathFillType = PathFillType.NonZero,
        block: PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = fillType, pathBuilder = block)
        }.build()

    // Full-weight rounded solid transport glyphs
    val Play = filled("Play") {
        moveTo(6.5f, 5.45f)
        curveTo(6.5f, 4.18f, 7.90f, 3.41f, 8.98f, 4.08f)
        lineTo(19.65f, 10.63f)
        curveTo(20.65f, 11.25f, 20.65f, 12.75f, 19.65f, 13.37f)
        lineTo(8.98f, 19.92f)
        curveTo(7.90f, 20.59f, 6.5f, 19.82f, 6.5f, 18.55f)
        close()
    }
    val Pause = filled("Pause") {
        // Left pill bar
        moveTo(7.3f, 4f); lineTo(8.5f, 4f)
        curveTo(9.49f, 4f, 10.3f, 4.81f, 10.3f, 5.8f)
        lineTo(10.3f, 18.2f)
        curveTo(10.3f, 19.19f, 9.49f, 20f, 8.5f, 20f)
        lineTo(7.3f, 20f)
        curveTo(6.31f, 20f, 5.5f, 19.19f, 5.5f, 18.2f)
        lineTo(5.5f, 5.8f)
        curveTo(5.5f, 4.81f, 6.31f, 4f, 7.3f, 4f); close()
        // Right pill bar
        moveTo(15.5f, 4f); lineTo(16.7f, 4f)
        curveTo(17.69f, 4f, 18.5f, 4.81f, 18.5f, 5.8f)
        lineTo(18.5f, 18.2f)
        curveTo(18.5f, 19.19f, 17.69f, 20f, 16.7f, 20f)
        lineTo(15.5f, 20f)
        curveTo(14.51f, 20f, 13.7f, 19.19f, 13.7f, 18.2f)
        lineTo(13.7f, 5.8f)
        curveTo(13.7f, 4.81f, 14.51f, 4f, 15.5f, 4f); close()
    }
    val Next = filled("Next") {
        moveTo(4.5f, 5.6f)
        curveTo(4.5f, 4.46f, 5.76f, 3.79f, 6.72f, 4.42f)
        lineTo(15.35f, 10.82f)
        curveTo(16.16f, 11.42f, 16.16f, 12.58f, 15.35f, 13.18f)
        lineTo(6.72f, 19.58f)
        curveTo(5.76f, 20.21f, 4.5f, 19.54f, 4.5f, 18.4f); close()
        moveTo(18.1f, 4.5f); lineTo(18.9f, 4.5f)
        curveTo(19.62f, 4.5f, 20.2f, 5.08f, 20.2f, 5.8f)
        lineTo(20.2f, 18.2f)
        curveTo(20.2f, 18.92f, 19.62f, 19.5f, 18.9f, 19.5f)
        lineTo(18.1f, 19.5f)
        curveTo(17.38f, 19.5f, 16.8f, 18.92f, 16.8f, 18.2f)
        lineTo(16.8f, 5.8f)
        curveTo(16.8f, 5.08f, 17.38f, 4.5f, 18.1f, 4.5f); close()
    }
    val Previous = filled("Previous") {
        moveTo(19.5f, 5.6f)
        curveTo(19.5f, 4.46f, 18.24f, 3.79f, 17.28f, 4.42f)
        lineTo(8.65f, 10.82f)
        curveTo(7.84f, 11.42f, 7.84f, 12.58f, 8.65f, 13.18f)
        lineTo(17.28f, 19.58f)
        curveTo(18.24f, 20.21f, 19.5f, 19.54f, 19.5f, 18.4f); close()
        moveTo(5.1f, 4.5f); lineTo(5.9f, 4.5f)
        curveTo(6.62f, 4.5f, 7.2f, 5.08f, 7.2f, 5.8f)
        lineTo(7.2f, 18.2f)
        curveTo(7.2f, 18.92f, 6.62f, 19.5f, 5.9f, 19.5f)
        lineTo(5.1f, 19.5f)
        curveTo(4.38f, 19.5f, 3.8f, 18.92f, 3.8f, 18.2f)
        lineTo(3.8f, 5.8f)
        curveTo(3.8f, 5.08f, 4.38f, 4.5f, 5.1f, 4.5f); close()
    }
    val Search = lines("Search") {
        moveTo(16f, 10f); curveTo(16f, 13.3f, 13.3f, 16f, 10f, 16f)
        curveTo(6.7f, 16f, 4f, 13.3f, 4f, 10f)
        curveTo(4f, 6.7f, 6.7f, 4f, 10f, 4f)
        curveTo(13.3f, 4f, 16f, 6.7f, 16f, 10f); close()
        moveTo(14.5f, 14.5f); lineTo(20f, 20f)
    }
    val Refresh = lines("Refresh") {
        moveTo(19f, 7f); curveTo(16f, 2.5f, 9f, 2.5f, 5.5f, 7f)
        curveTo(2f, 11.5f, 4f, 18f, 9f, 20f)
        curveTo(13f, 21.5f, 17.5f, 19.5f, 19f, 16f)
        moveTo(19f, 3f); lineTo(19f, 8f); lineTo(14f, 8f)
    }
    val Logout = lines("Logout") {
        moveTo(9f, 4f); lineTo(4f, 4f); lineTo(4f, 20f); lineTo(9f, 20f)
        moveTo(10f, 12f); lineTo(21f, 12f)
        moveTo(17f, 8f); lineTo(21f, 12f); lineTo(17f, 16f)
    }
    val Queue = ImageVector.Builder("Queue", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.3f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f)
            moveTo(3.5f, 11.5f); lineTo(20.5f, 11.5f)
            moveTo(3.5f, 17.5f); lineTo(12.5f, 17.5f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(15.8f, 14.3f)
            curveTo(15.8f, 13.7f, 16.45f, 13.35f, 16.95f, 13.68f)
            lineTo(21.15f, 16.48f)
            curveTo(21.6f, 16.78f, 21.6f, 17.42f, 21.15f, 17.72f)
            lineTo(16.95f, 20.52f)
            curveTo(16.45f, 20.85f, 15.8f, 20.5f, 15.8f, 19.9f)
            close()
        }
    }.build()
    val ChevronDown = lines("ChevronDown", strokeWidth = 2.4f) {
        moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f)
    }
    val Background = lines("Background") {
        moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
        moveTo(4f, 15f); lineTo(9f, 10f); lineTo(13f, 14f); lineTo(16f, 11f); lineTo(20f, 15f)
    }
    val Close = lines("Close", strokeWidth = 2.3f) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val Shuffle = lines("Shuffle", strokeWidth = 2.3f) {
        moveTo(3f, 6.5f); lineTo(6.5f, 6.5f); lineTo(16.8f, 17.5f); lineTo(21f, 17.5f)
        moveTo(3f, 17.5f); lineTo(6.5f, 17.5f); lineTo(10.2f, 13.6f)
        moveTo(13.8f, 9.9f); lineTo(16.8f, 6.5f); lineTo(21f, 6.5f)
        moveTo(17.8f, 3.3f); lineTo(21f, 6.5f); lineTo(17.8f, 9.7f)
        moveTo(17.8f, 14.3f); lineTo(21f, 17.5f); lineTo(17.8f, 20.7f)
    }
    val Sequence = lines("Sequence", strokeWidth = 2.3f) {
        moveTo(3f, 7.5f); lineTo(20.5f, 7.5f)
        moveTo(16.8f, 3.8f); lineTo(20.5f, 7.5f); lineTo(16.8f, 11.2f)
        moveTo(3f, 16.5f); lineTo(20.5f, 16.5f)
        moveTo(16.8f, 12.8f); lineTo(20.5f, 16.5f); lineTo(16.8f, 20.2f)
    }
    val Repeat = lines("Repeat", strokeWidth = 2.3f) {
        moveTo(17f, 2.5f); lineTo(20.8f, 6f); lineTo(17f, 9.5f)
        moveTo(3.2f, 11.2f); lineTo(3.2f, 10f); curveTo(3.2f, 7.8f, 5f, 6f, 7.2f, 6f); lineTo(20.8f, 6f)
        moveTo(7f, 21.5f); lineTo(3.2f, 18f); lineTo(7f, 14.5f)
        moveTo(20.8f, 12.8f); lineTo(20.8f, 14f); curveTo(20.8f, 16.2f, 19f, 18f, 16.8f, 18f); lineTo(3.2f, 18f)
    }
    val RepeatOne = lines("RepeatOne", strokeWidth = 2.3f) {
        moveTo(17f, 2.5f); lineTo(20.8f, 6f); lineTo(17f, 9.5f)
        moveTo(3.2f, 11.2f); lineTo(3.2f, 10f); curveTo(3.2f, 7.8f, 5f, 6f, 7.2f, 6f); lineTo(20.8f, 6f)
        moveTo(7f, 21.5f); lineTo(3.2f, 18f); lineTo(7f, 14.5f)
        moveTo(20.8f, 12.8f); lineTo(20.8f, 14f); curveTo(20.8f, 16.2f, 19f, 18f, 16.8f, 18f); lineTo(3.2f, 18f)
        moveTo(10.6f, 10.3f); lineTo(12.2f, 9.3f); lineTo(12.2f, 14.7f)
    }
    val Music = lines("Music") {
        moveTo(9f, 17f); lineTo(9f, 5f); lineTo(20f, 3f); lineTo(20f, 15f)
        moveTo(9f, 9f); lineTo(20f, 7f)
        moveTo(9f, 17f); curveTo(9f, 21f, 3f, 22f, 3f, 18.5f); curveTo(3f, 15.8f, 9f, 14f, 9f, 17f); close()
        moveTo(20f, 15f); curveTo(20f, 19f, 14f, 20f, 14f, 16.5f); curveTo(14f, 13.8f, 20f, 12f, 20f, 15f); close()
    }
    val Home = filled("Home") {
        moveTo(3f, 10f); lineTo(12f, 2.5f); lineTo(21f, 10f); lineTo(21f, 20.5f)
        lineTo(15f, 20.5f); lineTo(15f, 14f); lineTo(9f, 14f); lineTo(9f, 20.5f); lineTo(3f, 20.5f); close()
    }
    val HomeOutlined = lines("HomeOutlined") {
        moveTo(3f, 10f); lineTo(12f, 2.5f); lineTo(21f, 10f); lineTo(21f, 20.5f)
        lineTo(15f, 20.5f); lineTo(15f, 14f); lineTo(9f, 14f); lineTo(9f, 20.5f); lineTo(3f, 20.5f); close()
    }
    val Discover = lines("Discover") {
        moveTo(12f, 3f)
        curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
        curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
        curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
        curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f); close()
        moveTo(15.6f, 8.4f); lineTo(13.8f, 13.8f); lineTo(8.4f, 15.6f); lineTo(10.2f, 10.2f); close()
    }
    val Stats = lines("Stats") {
        moveTo(5f, 20f); lineTo(5f, 13f)
        moveTo(10f, 20f); lineTo(10f, 7f)
        moveTo(15f, 20f); lineTo(15f, 11f)
        moveTo(20f, 20f); lineTo(20f, 4f)
    }
    val Library = lines("Library") {
        moveTo(4f, 4f); lineTo(4f, 20f)
        moveTo(8.5f, 4f); lineTo(8.5f, 20f)
        moveTo(13f, 4.5f); lineTo(19f, 6.5f); lineTo(19f, 20.5f); lineTo(13f, 18.5f); close()
    }
    val Heart = filled("Heart") {
        moveTo(12f, 21f); curveTo(9f, 18f, 2f, 13f, 2f, 7.5f)
        curveTo(2f, 2f, 9f, 1f, 12f, 6f); curveTo(15f, 1f, 22f, 2f, 22f, 7.5f)
        curveTo(22f, 13f, 15f, 18f, 12f, 21f); close()
    }
    val HeartOutlined = lines("HeartOutlined") {
        moveTo(12f, 20.5f); curveTo(9.2f, 17.8f, 2.5f, 13f, 2.5f, 7.8f)
        curveTo(2.5f, 2.8f, 9f, 1.8f, 12f, 6.4f); curveTo(15f, 1.8f, 21.5f, 2.8f, 21.5f, 7.8f)
        curveTo(21.5f, 13f, 14.8f, 17.8f, 12f, 20.5f); close()
    }
    val PlaylistAdd = lines("PlaylistAdd") {
        moveTo(3f, 6f); lineTo(16f, 6f)
        moveTo(3f, 11f); lineTo(16f, 11f)
        moveTo(3f, 16f); lineTo(11f, 16f)
        moveTo(17f, 12.5f); lineTo(17f, 19.5f)
        moveTo(13.5f, 16f); lineTo(20.5f, 16f)
    }
    val Star = lines("Star") {
        moveTo(12f, 3f); lineTo(14.8f, 8.6f); lineTo(21f, 9.5f)
        lineTo(16.5f, 13.9f); lineTo(17.6f, 20.1f); lineTo(12f, 17.2f)
        lineTo(6.4f, 20.1f); lineTo(7.5f, 13.9f); lineTo(3f, 9.5f)
        lineTo(9.2f, 8.6f); close()
    }
    val StarFilled = filled("StarFilled") {
        // Scaled from web collection-star.svg (256x256) into 24x24 viewport
        moveTo(12.22f, 2.93f)
        lineTo(14.50f, 8.89f)
        quadTo(14.64f, 9.25f, 15.03f, 9.27f)
        lineTo(21.40f, 9.60f)
        quadTo(22.02f, 9.63f, 21.54f, 10.02f)
        lineTo(16.58f, 14.03f)
        quadTo(16.27f, 14.28f, 16.37f, 14.65f)
        lineTo(18.03f, 20.82f)
        quadTo(18.19f, 21.42f, 17.68f, 21.08f)
        lineTo(12.33f, 17.60f)
        quadTo(12.00f, 17.39f, 11.67f, 17.60f)
        lineTo(6.32f, 21.08f)
        quadTo(5.81f, 21.42f, 5.97f, 20.82f)
        lineTo(7.63f, 14.65f)
        quadTo(7.73f, 14.28f, 7.42f, 14.03f)
        lineTo(2.46f, 10.02f)
        quadTo(1.98f, 9.63f, 2.60f, 9.60f)
        lineTo(8.97f, 9.27f)
        quadTo(9.36f, 9.25f, 9.50f, 8.89f)
        lineTo(11.78f, 2.93f)
        quadTo(12.00f, 2.35f, 12.22f, 2.93f)
        close()
    }
    val History = lines("History") {
        moveTo(4f, 12f)
        curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
        curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
        curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
        curveTo(8.7f, 20f, 5.87f, 18f, 4.65f, 15.15f)
        moveTo(4f, 7f); lineTo(4f, 12f); lineTo(9f, 12f)
        moveTo(12f, 8f); lineTo(12f, 12.2f); lineTo(15f, 14f)
    }
    // Filled rounded speech-bubble with double-quote cutouts — unmistakably distinct from Translate ("文/A").
    val Lyrics = filled("Lyrics", fillType = PathFillType.EvenOdd) {
        moveTo(6.8f, 3f)
        lineTo(17.2f, 3f)
        curveTo(19.3f, 3f, 21f, 4.7f, 21f, 6.8f)
        lineTo(21f, 13.7f)
        curveTo(21f, 15.8f, 19.3f, 17.5f, 17.2f, 17.5f)
        lineTo(10.2f, 17.5f)
        lineTo(5.6f, 20.8f)
        curveTo(4.7f, 21.4f, 3.6f, 20.6f, 3.9f, 19.5f)
        lineTo(4.6f, 17.2f)
        curveTo(3.6f, 16.5f, 3f, 15.2f, 3f, 13.7f)
        lineTo(3f, 6.8f)
        curveTo(3f, 4.7f, 4.7f, 3f, 6.8f, 3f)
        close()
        // Left quote cutout
        moveTo(9.1f, 6.8f)
        curveTo(10.3f, 6.8f, 11.2f, 7.7f, 11.2f, 8.9f)
        curveTo(11.2f, 10.9f, 9.9f, 12.6f, 7.9f, 13.4f)
        curveTo(7.4f, 13.6f, 7.0f, 13.2f, 7.2f, 12.7f)
        curveTo(7.7f, 11.8f, 8.1f, 11.1f, 8.2f, 10.5f)
        curveTo(7.4f, 10.2f, 7.0f, 9.6f, 7.0f, 8.8f)
        curveTo(7.0f, 7.7f, 7.9f, 6.8f, 9.1f, 6.8f)
        close()
        // Right quote cutout
        moveTo(14.9f, 6.8f)
        curveTo(16.1f, 6.8f, 17.0f, 7.7f, 17.0f, 8.9f)
        curveTo(17.0f, 10.9f, 15.7f, 12.6f, 13.7f, 13.4f)
        curveTo(13.2f, 13.6f, 12.8f, 13.2f, 13.0f, 12.7f)
        curveTo(13.5f, 11.8f, 13.9f, 11.1f, 14.0f, 10.5f)
        curveTo(13.2f, 10.2f, 12.8f, 9.6f, 12.8f, 8.8f)
        curveTo(12.8f, 7.7f, 13.7f, 6.8f, 14.9f, 6.8f)
        close()
    }
    val Translate = lines("Translate", strokeWidth = 2.25f) {
        // Bold bilingual "文/A" glyph
        moveTo(5f, 8.5f); lineTo(10.5f, 14f)
        moveTo(4f, 14f); lineTo(9.8f, 8.2f); lineTo(11.6f, 5.2f)
        moveTo(2.6f, 5.2f); lineTo(13.4f, 5.2f)
        moveTo(7.5f, 2.6f); lineTo(7.5f, 5.2f)
        moveTo(13.6f, 19.2f); lineTo(17.5f, 10f); lineTo(21.4f, 19.2f)
        moveTo(14.9f, 16.4f); lineTo(20.1f, 16.4f)
    }
    val Globe = lines("Globe") {
        // Circle, equator and two meridians: interface language, distinct from the translate glyph.
        moveTo(12f, 3f)
        curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
        curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
        curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
        curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
        moveTo(3f, 12f); lineTo(21f, 12f)
        moveTo(12f, 3f)
        curveTo(9.6f, 5.5f, 8.3f, 8.6f, 8.3f, 12f)
        curveTo(8.3f, 15.4f, 9.6f, 18.5f, 12f, 21f)
        moveTo(12f, 3f)
        curveTo(14.4f, 5.5f, 15.7f, 8.6f, 15.7f, 12f)
        curveTo(15.7f, 15.4f, 14.4f, 18.5f, 12f, 21f)
    }
    val ChevronLeft = lines("ChevronLeft") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    val ChevronRight = lines("ChevronRight") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    val Trash = lines("Trash") {
        moveTo(4f, 6f); lineTo(20f, 6f)
        moveTo(9f, 6f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 6f)
        moveTo(6f, 6f); lineTo(7f, 19.5f); curveTo(7f, 20.3f, 7.7f, 21f, 8.5f, 21f)
        lineTo(15.5f, 21f); curveTo(16.3f, 21f, 17f, 20.3f, 17f, 19.5f); lineTo(18f, 6f)
        moveTo(10f, 10.5f); lineTo(10f, 16.5f)
        moveTo(14f, 10.5f); lineTo(14f, 16.5f)
    }
    val DragHandle = ImageVector.Builder("DragHandle", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3.5f, 8.5f); lineTo(20.5f, 8.5f)
            moveTo(3.5f, 15.5f); lineTo(20.5f, 15.5f)
        }
    }.build()
    val Plus = lines("Plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }
    val Disc = lines("Disc") {
        moveTo(12f, 3f)
        curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
        curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
        curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
        curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f); close()
        moveTo(12f, 10f)
        curveTo(13.1f, 10f, 14f, 10.9f, 14f, 12f)
        curveTo(14f, 13.1f, 13.1f, 14f, 12f, 14f)
        curveTo(10.9f, 14f, 10f, 13.1f, 10f, 12f)
        curveTo(10f, 10.9f, 10.9f, 10f, 12f, 10f); close()
    }
    val Artist = lines("Artist") {
        moveTo(12f, 4f)
        curveTo(14.21f, 4f, 16f, 5.79f, 16f, 8f)
        curveTo(16f, 10.21f, 14.21f, 12f, 12f, 12f)
        curveTo(9.79f, 12f, 8f, 10.21f, 8f, 8f)
        curveTo(8f, 5.79f, 9.79f, 4f, 12f, 4f); close()
        moveTo(5f, 20f)
        curveTo(5f, 16.13f, 8.13f, 13f, 12f, 13f)
        curveTo(15.87f, 13f, 19f, 16.13f, 19f, 20f)
    }
    val More = filled("More") {
        for (y in listOf(5f, 12f, 19f)) {
            moveTo(14.05f, y)
            curveTo(14.05f, y + 2.73f, 9.95f, y + 2.73f, 9.95f, y)
            curveTo(9.95f, y - 2.73f, 14.05f, y - 2.73f, 14.05f, y)
            close()
        }
    }
    val VolumeLow = lines("VolumeLow") {
        moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 5f); lineTo(12f, 19f)
        lineTo(7f, 15f); lineTo(3f, 15f); close()
        moveTo(16f, 9f); curveTo(18f, 11f, 18f, 13f, 16f, 15f)
    }
    val VolumeHigh = lines("VolumeHigh") {
        moveTo(2f, 9f); lineTo(6f, 9f); lineTo(11f, 5f); lineTo(11f, 19f)
        lineTo(6f, 15f); lineTo(2f, 15f); close()
        moveTo(15f, 9f); curveTo(17f, 11f, 17f, 13f, 15f, 15f)
        moveTo(18f, 6f); curveTo(22f, 9f, 22f, 15f, 18f, 18f)
    }
    val Settings = lines("Settings") {
        // Minimal Lucide-inspired gear outline + center ring
        moveTo(12f, 9f)
        curveTo(13.66f, 9f, 15f, 10.34f, 15f, 12f)
        curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
        curveTo(10.34f, 15f, 9f, 13.66f, 9f, 12f)
        curveTo(9f, 10.34f, 10.34f, 9f, 12f, 9f); close()
        moveTo(12f, 3f); lineTo(13.4f, 5.2f); lineTo(16f, 4.7f); lineTo(16.6f, 7.2f)
        lineTo(19f, 8.2f); lineTo(18.5f, 10.8f); lineTo(20.5f, 12f); lineTo(18.5f, 13.2f)
        lineTo(19f, 15.8f); lineTo(16.6f, 16.8f); lineTo(16f, 19.3f); lineTo(13.4f, 18.8f)
        lineTo(12f, 21f); lineTo(10.6f, 18.8f); lineTo(8f, 19.3f); lineTo(7.4f, 16.8f)
        lineTo(5f, 15.8f); lineTo(5.5f, 13.2f); lineTo(3.5f, 12f); lineTo(5.5f, 10.8f)
        lineTo(5f, 8.2f); lineTo(7.4f, 7.2f); lineTo(8f, 4.7f); lineTo(10.6f, 5.2f); close()
    }
    val SleepTimer = lines("SleepTimer") {
        // Crisp clock / timer circle with hands
        moveTo(12f, 4f)
        curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
        curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
        curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
        curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f); close()
        moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15.2f, 13.8f)
    }
    val Check = lines("Check") {
        moveTo(5f, 12.5f); lineTo(9.5f, 17f); lineTo(19f, 7f)
    }
    val Copy = lines("Copy") {
        moveTo(9f, 9f); lineTo(19f, 9f); lineTo(19f, 20f); lineTo(9f, 20f); close()
        moveTo(15f, 9f); lineTo(15f, 5f); lineTo(5f, 5f); lineTo(5f, 16f); lineTo(9f, 16f)
    }
    val Edit = lines("Edit") {
        moveTo(12f, 20f); lineTo(21f, 20f)
        moveTo(16.5f, 3.5f)
        curveTo(17.3f, 2.7f, 18.7f, 2.7f, 19.5f, 3.5f)
        curveTo(20.3f, 4.3f, 20.3f, 5.7f, 19.5f, 6.5f)
        lineTo(7f, 19f); lineTo(3f, 20f); lineTo(4f, 16f); close()
    }
    val Download = lines("Download") {
        moveTo(21f, 15f); lineTo(21f, 19f)
        curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
        lineTo(5f, 21f)
        curveTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
        lineTo(3f, 15f)
        moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        moveTo(12f, 15f); lineTo(12f, 3f)
    }
    val Sun = lines("Sun") {
        moveTo(12f, 8f)
        curveTo(14.21f, 8f, 16f, 9.79f, 16f, 12f)
        curveTo(16f, 14.21f, 14.21f, 16f, 12f, 16f)
        curveTo(9.79f, 16f, 8f, 14.21f, 8f, 12f)
        curveTo(8f, 9.79f, 9.79f, 8f, 12f, 8f); close()
        moveTo(12f, 2f); lineTo(12f, 4f)
        moveTo(12f, 20f); lineTo(12f, 22f)
        moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
        moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
        moveTo(2f, 12f); lineTo(4f, 12f)
        moveTo(20f, 12f); lineTo(22f, 12f)
        moveTo(6.34f, 17.66f); lineTo(4.93f, 19.07f)
        moveTo(19.07f, 4.93f); lineTo(17.66f, 6.34f)
    }
    val Moon = lines("Moon") {
        moveTo(20.985f, 12.486f)
        curveTo(20.1f, 17.8f, 14.8f, 21.3f, 9.4f, 20.1f)
        curveTo(5.1f, 19.1f, 2.3f, 14.8f, 3.3f, 10.3f)
        curveTo(4.1f, 6.5f, 7.3f, 3.8f, 11.1f, 3.1f)
        curveTo(9.4f, 6.2f, 9.9f, 10.2f, 12.6f, 12.7f)
        curveTo(15.0f, 15.0f, 18.4f, 15.2f, 20.985f, 12.486f); close()
    }
}
