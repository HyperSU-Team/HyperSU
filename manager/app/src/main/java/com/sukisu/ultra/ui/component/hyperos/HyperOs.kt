package com.sukisu.ultra.ui.component.hyperos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The page chrome of HyperOS: a light (or near-black) vertical gradient, a 56dp title bar with
 * the back arrow on the left, and content made of 20dp cards separated by section captions.
 *
 * These screens are deliberately drawn over their own opaque page rather than over the app
 * wallpaper: that is how a HyperOS settings page reads, and it is what makes the white cards
 * legible at all.
 */

private val LightPageTop = Color(0xFFF7F7F9)
private val LightPageBottom = Color(0xFFEDEDF1)
private val DarkPageTop = Color(0xFF0F0F11)
private val DarkPageBottom = Color(0xFF17171A)

internal val HyperAccent = Color(0xFF3482FF)

@Composable
internal fun hyperCardColor(): Color = if (isSystemInDarkTheme()) Color(0xFF1D1D20) else Color.White

@Composable
internal fun hyperTextColor(): Color = if (isSystemInDarkTheme()) Color(0xFFF2F2F5) else Color(0xFF191919)

@Composable
internal fun hyperSecondaryColor(): Color =
    if (isSystemInDarkTheme()) Color(0x99F2F2F5) else Color(0x99191919)

@Composable
internal fun hyperSeparatorColor(): Color =
    if (isSystemInDarkTheme()) Color(0x1FFFFFFF) else Color(0x14000000)

@Composable
fun HyperOsPage(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gradient = if (isSystemInDarkTheme()) {
        listOf(DarkPageTop, DarkPageBottom)
    } else {
        listOf(LightPageTop, LightPageBottom)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient))
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = hyperTextColor(),
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = hyperTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            content()
            Spacer(Modifier.height(32.dp))
        }

        if (bottomBar != null) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                bottomBar()
            }
        }
    }
}

/** One rounded card. Rows inside carry their own padding and dividers. */
@Composable
fun HyperCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(hyperCardColor()),
        content = content,
    )
}

/** A caption above a card, in HyperOS's small grey section type. */
@Composable
fun HyperSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = hyperSecondaryColor(),
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
fun HyperSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        if (title != null) HyperSectionLabel(title)
        HyperCard { content() }
    }
}

@Composable
fun HyperDivider(startInset: Int = 16) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startInset.dp),
        thickness = 0.5.dp,
        color = hyperSeparatorColor(),
    )
}

/** A single-line label/value row: label left, value right in the secondary colour. */
@Composable
fun HyperRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
    valueColor: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = hyperTextColor(),
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor ?: hyperSecondaryColor(),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (showDivider) HyperDivider()
}

/** A two-line row for values too long to sit on the right edge. */
@Composable
fun HyperStackedRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
    valueColor: Color? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, fontSize = 13.sp, color = hyperSecondaryColor())
        Text(
            text = value,
            fontSize = 15.sp,
            color = valueColor ?: hyperTextColor(),
        )
    }
    if (showDivider) HyperDivider()
}

/** The header card: mark, app name, version - the about page's opening. */
@Composable
fun HyperHero(
    icon: Painter,
    name: String,
    version: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        HyperCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White),
                ) {
                    Image(
                        painter = icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                    )
                }
                Text(
                    text = name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = hyperTextColor(),
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    text = version,
                    fontSize = 13.sp,
                    color = hyperSecondaryColor(),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Two centred lines inside one card, used for the device's name and its system version. */
@Composable
fun HyperCenteredCard(
    title: String,
    subtitle: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = hyperTextColor(),
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = hyperSecondaryColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The filled pill HyperOS puts at the bottom of a task page. */
@Composable
fun HyperPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier)
            .height(52.dp)
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = HyperAccent,
            contentColor = Color.White,
            disabledContainerColor = HyperAccent.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun HyperTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = if (enabled) HyperAccent else hyperSecondaryColor(),
        )
    }
}
