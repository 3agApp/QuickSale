package me.sourov.quicksale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.sourov.quicksale.ui.theme.BrandGradient

/** Gradient app mark — a rounded square with a lightning bolt for the "Quick" in QuickSale. */
@Composable
fun QuickSaleLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.64f),
        )
    }
}

/** Two-tone "QuickSale" wordmark. */
@Composable
fun QuickSaleWordmark(modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("Quick") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Sale") }
        },
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
    )
}

/** Logo + wordmark lockup used in the app bar. */
@Composable
fun QuickSaleBrandLockup(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        QuickSaleLogo(size = 30.dp)
        Spacer(Modifier.width(10.dp))
        QuickSaleWordmark()
    }
}
