package me.sourov.quicksale.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The app's spacing scale.
 *
 * Screens used to pick padding freehand — 12, 14, 16, 20, 22 and 28dp all appeared within one
 * card — which is most of why the UI read as untidy. Everything now steps through these.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Standard screen gutter. */
    val screen = 16.dp

    /** Gap between a section heading and its content. */
    val sectionGap = 12.dp

    /** Gap between one section and the next. */
    val sectionSpacing = 28.dp
}

/** Corner radii, kept in one place so cards, sheets and badges agree. */
object Corners {
    val badge = 8.dp
    val card = 18.dp
    val hero = 24.dp
    val control = 14.dp
}

val QuickSaleShapes = Shapes(
    extraSmall = RoundedCornerShape(Corners.badge),
    small = RoundedCornerShape(Corners.control),
    medium = RoundedCornerShape(Corners.card),
    large = RoundedCornerShape(Corners.hero),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Fixed sizes that recur across screens. */
object Sizes {
    val avatar = 44.dp
    val avatarSmall = 38.dp
    val icon = 20.dp
    val iconLarge = 24.dp
    val thumbnail = 64.dp
    val button = 52.dp
    val emptyStateIcon = 76.dp
}
