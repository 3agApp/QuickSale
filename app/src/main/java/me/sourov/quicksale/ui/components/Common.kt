package me.sourov.quicksale.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/** A quiet heading above a group of related content, with optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** The app's default container: a flat, tinted card. Elevation is reserved for things that lift. */
@Composable
fun QuickSaleCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        content = content,
    )
}

/** A small pill of state — order status, organization status, stock. */
@Composable
fun StatusChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

/** A circular monogram, used wherever a person or organization needs a face. */
@Composable
fun Monogram(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.avatar,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
        )
    }
}

/** A round icon badge, for the leading slot of list rows and cards. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.avatar,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * A sync button that spins while a run is in flight.
 *
 * Every sync affordance in the app uses this, reading the same SyncManager state, so a sync
 * started on one screen visibly continues on the next.
 */
@Composable
fun SyncIconButton(
    syncing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Sync",
) {
    IconButton(onClick = onClick, enabled = !syncing, modifier = modifier) {
        SpinningSyncIcon(
            syncing = syncing,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The rotating sync glyph on its own, for buttons that supply their own container. */
@Composable
fun SpinningSyncIcon(
    syncing: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = Sizes.icon,
) {
    val rotation = if (syncing) {
        val transition = rememberInfiniteTransition(label = "sync")
        val angle by transition.animateFloat(
            initialValue = 0f,
            // Counter-clockwise: the Material sync glyph's arrows read as "going round" that way.
            targetValue = -360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "syncAngle",
        )
        angle
    } else {
        0f
    }

    Icon(
        imageVector = Icons.Outlined.Sync,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(size)
            .rotate(rotation),
    )
}

/** Centred spinner for a screen that has nothing to show yet. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * The app's one empty state. Every list used to carry its own near-identical copy; this one takes
 * an action so "nothing here yet" can offer the fix — usually a sync — instead of just saying so.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconBadge(
                icon = icon,
                size = Sizes.emptyStateIcon,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(Spacing.xs))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/**
 * A stepper button that keeps firing while it is held down.
 *
 * Twenty-four of something is twenty-three taps otherwise, and at a counter with a queue that is
 * the point where the operator gives up on the button. The first step lands on press so a tap still
 * feels immediate; the repeat only begins once the press has outlived [HOLD_BEFORE_REPEAT_MS], so
 * an ordinary tap is one step and never two. It speeds up while held, because someone still holding
 * after a second wants a much larger number rather than a slightly larger one.
 *
 * The step is driven from the press gesture rather than `onClick`, which would otherwise fire again
 * on release and leave the quantity one above what the operator watched it reach. `onClick` is left
 * to the accessibility action, where a single activation is exactly what is meant.
 */
@Composable
fun RepeatingStepperButton(
    onStep: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Asked before every repeated step, to stop a hold short of somewhere a tap may still go.
     *
     * The cart's − is the case it exists for: holding it should bring the quantity down and stop
     * at the last one, leaving removing the product to a deliberate tap on the bin. A lambda rather
     * than a flag so the answer can be read live — at 40ms a step, a frame of stale state is a line
     * already gone.
     */
    repeatWhileHeld: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    // Read through the latest composition's lambda: a stepper whose action closes over the current
    // value (`onChange(value + 1)`) hands us a new one every step, and the stale one would pin the
    // quantity at its second value for the whole hold.
    val step by rememberUpdatedState(onStep)
    val stepEnabled by rememberUpdatedState(enabled)
    val mayRepeat by rememberUpdatedState(repeatWhileHeld)

    FilledTonalIconButton(
        // Empty on purpose: the gesture below owns every step. Left in place so the button keeps
        // its ripple, focus and button role, with the activation an assistive tool sends restored
        // through the semantics action.
        onClick = {},
        enabled = enabled,
        modifier = modifier
            .semantics {
                onClick(label = contentDescription) {
                    if (stepEnabled) step()
                    true
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    val gestures = this
                    awaitEachGesture {
                        // requireUnconsumed = false is the whole trick: the button's own clickable
                        // sits further down the chain and has already consumed this press by the
                        // time it reaches here. We are watching it, not competing for it.
                        awaitFirstDown(requireUnconsumed = false)
                        if (!stepEnabled) return@awaitEachGesture

                        step()
                        val repeat = gestures.launch {
                            delay(HOLD_BEFORE_REPEAT_MS)
                            var interval = REPEAT_START_MS
                            // Stops on its own when the button disables under it — a copies
                            // stepper at its maximum — or when the hold has reached the last
                            // step it is allowed to take on its own.
                            while (stepEnabled && mayRepeat()) {
                                step()
                                delay(interval)
                                interval = (interval - REPEAT_STEP_MS).coerceAtLeast(REPEAT_MIN_MS)
                            }
                        }
                        // Returns null when the finger slides off instead of lifting; both end it.
                        waitForUpOrCancellation()
                        repeat.cancel()
                    }
                }
            },
        content = { content() },
    )
}

/** How long a press must be held before it starts repeating, in milliseconds. */
private const val HOLD_BEFORE_REPEAT_MS = 400L

/** The gap between the first repeated steps, before it accelerates. */
private const val REPEAT_START_MS = 140L

/** How much each repeat shortens the gap to the next one. */
private const val REPEAT_STEP_MS = 12L

/** The fastest the repeat gets — roughly 25 steps a second, still readable as it climbs. */
private const val REPEAT_MIN_MS = 40L
