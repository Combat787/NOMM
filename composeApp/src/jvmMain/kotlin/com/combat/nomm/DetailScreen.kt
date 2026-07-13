package com.combat.nomm

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import nuclearoptionmodmanager.composeapp.generated.resources.close_24px
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun DetailScreen(
    backStack: NavBackStack<NavKey>,
    currentKey: NavKey,
    keys: List<Triple<NavKey, String, DrawableResource>>,
    title: String,
    subtitle: String,
    details: @Composable () -> Unit,
    buttons: @Composable (controlSize: Dp, iconSize: Dp) -> Unit,
    onBack: () -> Unit,
    content: @Composable (NavBackStack<NavKey>) -> ((NavKey) -> NavEntry<NavKey>)
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        DetailScreenTitleCard(title, subtitle, details, buttons, onBack)
        DetailScreenNavigationBar(
            currentKey,
            backStack,
            keys
        )

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                entryProvider = content(backStack)
            )

        }
    }
}

@Composable
fun DetailScreenTitleCard(
    title: String,
    subtitle: String,
    details: @Composable () -> Unit,
    buttons: @Composable (controlSize: Dp, iconSize: Dp) -> Unit,
    onBack: () -> Unit
) {

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))

                        details()
                }
            }


            val controlSize = 40.dp
            val iconSize = 28.dp

            buttons(controlSize, iconSize)

            IconButton(
                onClick = onBack, colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(controlSize).clip(CircleShape).clipToBounds()
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.close_24px),
                    contentDescription = "Close",
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun DetailScreenNavigationBar(
    currentKey: NavKey,
    backStack: NavBackStack<NavKey>,
    keys: List<Triple<NavKey, String, DrawableResource>>
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CircleShape,
        modifier = Modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            keys.forEach { (key, name, icon) ->
                NavItem(
                    selected = currentKey == key,
                    label = name,
                    icon = icon,
                ) {
                    backStack.clear()
                    backStack.add(key)
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    selected: Boolean,
    label: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier.Companion,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else Color.Transparent

    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier.fillMaxHeight().clip(CircleShape).background(backgroundColor)
            .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
            .padding(8.dp), contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}