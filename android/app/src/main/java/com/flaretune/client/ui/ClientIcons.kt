package com.flaretune.client.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** One small, consistent 24dp geometric icon family. */
object ClientIcons {
    private fun lines(name: String, strokeWidth: Float = 1.8f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun filled(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathBuilder = block)
        }.build()

    // Rounded solid play/pause modeled after web SolidRoundedPlay / SolidRoundedPause
    val Play = filled("Play") {
        moveTo(7f, 6.35f)
        curveTo(7f, 5.17f, 8.3f, 4.45f, 9.3f, 5.08f)
        lineTo(19.4f, 11.46f)
        curveTo(20.3f, 12.03f, 20.3f, 13.37f, 19.4f, 13.94f)
        lineTo(9.3f, 20.32f)
        curveTo(8.3f, 20.95f, 7f, 20.23f, 7f, 19.05f)
        close()
    }
    val Pause = filled("Pause") {
        // Left pill bar
        moveTo(7.5f, 4f); lineTo(9.5f, 4f)
        curveTo(10.6f, 4f, 11.5f, 4.9f, 11.5f, 6f)
        lineTo(11.5f, 18f)
        curveTo(11.5f, 19.1f, 10.6f, 20f, 9.5f, 20f)
        lineTo(7.5f, 20f)
        curveTo(6.4f, 20f, 5.5f, 19.1f, 5.5f, 18f)
        lineTo(5.5f, 6f)
        curveTo(5.5f, 4.9f, 6.4f, 4f, 7.5f, 4f); close()
        // Right pill bar
        moveTo(14.5f, 4f); lineTo(16.5f, 4f)
        curveTo(17.6f, 4f, 18.5f, 4.9f, 18.5f, 6f)
        lineTo(18.5f, 18f)
        curveTo(18.5f, 19.1f, 17.6f, 20f, 16.5f, 20f)
        lineTo(14.5f, 20f)
        curveTo(13.4f, 20f, 12.5f, 19.1f, 12.5f, 18f)
        lineTo(12.5f, 6f)
        curveTo(12.5f, 4.9f, 13.4f, 4f, 14.5f, 4f); close()
    }
    val Next = filled("Next") {
        moveTo(5.2f, 6.2f)
        curveTo(5.2f, 5.15f, 6.38f, 4.54f, 7.26f, 5.12f)
        lineTo(15.2f, 10.92f)
        curveTo(15.96f, 11.48f, 15.96f, 12.52f, 15.2f, 13.08f)
        lineTo(7.26f, 18.88f)
        curveTo(6.38f, 19.46f, 5.2f, 18.85f, 5.2f, 17.8f); close()
        moveTo(18f, 5f); lineTo(19f, 5f)
        curveTo(19.55f, 5f, 20f, 5.45f, 20f, 6f)
        lineTo(20f, 18f)
        curveTo(20f, 18.55f, 19.55f, 19f, 19f, 19f)
        lineTo(18f, 19f)
        curveTo(17.45f, 19f, 17f, 18.55f, 17f, 18f)
        lineTo(17f, 6f)
        curveTo(17f, 5.45f, 17.45f, 5f, 18f, 5f); close()
    }
    val Previous = filled("Previous") {
        moveTo(18.8f, 6.2f)
        curveTo(18.8f, 5.15f, 17.62f, 4.54f, 16.74f, 5.12f)
        lineTo(8.8f, 10.92f)
        curveTo(8.04f, 11.48f, 8.04f, 12.52f, 8.8f, 13.08f)
        lineTo(16.74f, 18.88f)
        curveTo(17.62f, 19.46f, 18.8f, 18.85f, 18.8f, 17.8f); close()
        moveTo(5f, 5f); lineTo(6f, 5f)
        curveTo(6.55f, 5f, 7f, 5.45f, 7f, 6f)
        lineTo(7f, 18f)
        curveTo(7f, 18.55f, 6.55f, 19f, 6f, 19f)
        lineTo(5f, 19f)
        curveTo(4.45f, 19f, 4f, 18.55f, 4f, 18f)
        lineTo(4f, 6f)
        curveTo(4f, 5.45f, 4.45f, 5f, 5f, 5f); close()
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
    val Queue = lines("Queue") {
        moveTo(4f, 5f); lineTo(20f, 5f)
        moveTo(4f, 11f); lineTo(20f, 11f)
        moveTo(4f, 17f); lineTo(12f, 17f)
        moveTo(17f, 15f); lineTo(21f, 18f); lineTo(17f, 21f); close()
    }
    val ChevronDown = lines("ChevronDown") {
        moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f)
    }
    val Background = lines("Background") {
        moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
        moveTo(4f, 15f); lineTo(9f, 10f); lineTo(13f, 14f); lineTo(16f, 11f); lineTo(20f, 15f)
    }
    val Close = lines("Close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val Shuffle = lines("Shuffle") {
        moveTo(3f, 6f); lineTo(6f, 6f); lineTo(17f, 18f); lineTo(21f, 18f)
        moveTo(3f, 18f); lineTo(6f, 18f); lineTo(10f, 14f)
        moveTo(14f, 10f); lineTo(17f, 6f); lineTo(21f, 6f)
        moveTo(18f, 3f); lineTo(21f, 6f); lineTo(18f, 9f)
        moveTo(18f, 15f); lineTo(21f, 18f); lineTo(18f, 21f)
    }
    val Sequence = lines("Sequence") {
        moveTo(3f, 7f); lineTo(20f, 7f)
        moveTo(16f, 3f); lineTo(20f, 7f); lineTo(16f, 11f)
        moveTo(3f, 17f); lineTo(20f, 17f)
        moveTo(16f, 13f); lineTo(20f, 17f); lineTo(16f, 21f)
    }
    val Repeat = lines("Repeat") {
        // Lucide Repeat
        moveTo(17f, 2f); lineTo(21f, 6f); lineTo(17f, 10f)
        moveTo(3f, 11f); lineTo(3f, 10f); curveTo(3f, 7.8f, 4.8f, 6f, 7f, 6f); lineTo(21f, 6f)
        moveTo(7f, 22f); lineTo(3f, 18f); lineTo(7f, 14f)
        moveTo(21f, 13f); lineTo(21f, 14f); curveTo(21f, 16.2f, 19.2f, 18f, 17f, 18f); lineTo(3f, 18f)
    }
    val RepeatOne = lines("RepeatOne") {
        // Lucide Repeat1: repeat loop with crisp integrated "1" in the center
        moveTo(17f, 2f); lineTo(21f, 6f); lineTo(17f, 10f)
        moveTo(3f, 11f); lineTo(3f, 10f); curveTo(3f, 7.8f, 4.8f, 6f, 7f, 6f); lineTo(21f, 6f)
        moveTo(7f, 22f); lineTo(3f, 18f); lineTo(7f, 14f)
        moveTo(21f, 13f); lineTo(21f, 14f); curveTo(21f, 16.2f, 19.2f, 18f, 17f, 18f); lineTo(3f, 18f)
        moveTo(10.8f, 10.2f); lineTo(12.2f, 9.5f); lineTo(12.2f, 14.5f)
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
    val History = lines("History") {
        moveTo(4f, 12f)
        curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
        curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
        curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
        curveTo(8.7f, 20f, 5.87f, 18f, 4.65f, 15.15f)
        moveTo(4f, 7f); lineTo(4f, 12f); lineTo(9f, 12f)
        moveTo(12f, 8f); lineTo(12f, 12.2f); lineTo(15f, 14f)
    }
    val Lyrics = lines("Lyrics") {
        moveTo(4f, 6f); lineTo(20f, 6f)
        moveTo(4f, 11f); lineTo(16f, 11f)
        moveTo(4f, 16f); lineTo(13f, 16f)
        moveTo(18f, 15f); lineTo(18f, 19.5f)
        moveTo(18f, 15f); lineTo(21f, 14.2f)
    }
    val Translate = lines("Translate") {
        // Lucide Languages path
        moveTo(5f, 8f); lineTo(11f, 14f)
        moveTo(4f, 14f); lineTo(10f, 8f); lineTo(12f, 5f)
        moveTo(2f, 5f); lineTo(14f, 5f)
        moveTo(7f, 2f); lineTo(7f, 5f)
        moveTo(14f, 18f); lineTo(18f, 9f); lineTo(22f, 18f)
        moveTo(15.2f, 15.5f); lineTo(20.8f, 15.5f)
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
            moveTo(13.5f, y); curveTo(13.5f, y + 2f, 10.5f, y + 2f, 10.5f, y)
            curveTo(10.5f, y - 2f, 13.5f, y - 2f, 13.5f, y); close()
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
}
