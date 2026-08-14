package me.sourov.quicksale.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The app's spacing scale.
 *
 * Screens used to pick padding freehand — 12, 14, 16, 20, 22 and 28dp all appeared within one
 * card — which is most of why the UI read as untidy. Everything now steps through these.
 *
 * Tuned for the handhelds this actually runs on: 720×1440 at 280–293dpi, which is ~393–411dp
 * across a screen physically about 2.5″ × 4.9″. Values sized for a phone spent a quarter of that
 * on furniture before a single row of catalog appeared, so the composite steps below are
 * deliberately tighter than Material's defaults. The reading steps — [xs] through [xxl] — are
 * unchanged; only what is built out of them moved.
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

    /** The inside of a dense list row — tighter than [md], which reads as a card. */
    val rowPadding = 10.dp

    /** Gap between a section heading and its content. */
    val sectionGap = 8.dp

    /** Gap between one section and the next. */
    val sectionSpacing = 20.dp
}

/** Corner radii, kept in one place so cards, sheets and badges agree. */
object Corners {
    val badge = 8.dp

    /** List rows are ~60dp tall now, and an 18dp radius ate their corners. */
    val card = 12.dp
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
    val avatar = 40.dp
    val avatarSmall = 32.dp

    /** The monogram at the head of a detail page, where it is the subject rather than a bullet. */
    val avatarLarge = 56.dp
    val icon = 20.dp
    val iconLarge = 24.dp
    val thumbnail = 48.dp

    /** Primary buttons. 48dp is Material's touch minimum, and a till has no need of more. */
    val button = 48.dp

    /** Empty states are the least valuable pixels on any screen. */
    val emptyStateIcon = 56.dp

    /** The shell's top bar, below the status bar. Material's default 64dp is a phone's. */
    val topBar = 48.dp

    /** The bottom navigation bar. Material defaults to 80dp; 64 still holds an icon over a label. */
    val navBar = 64.dp
}
