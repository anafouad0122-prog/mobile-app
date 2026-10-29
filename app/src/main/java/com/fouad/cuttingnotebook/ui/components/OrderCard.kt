package com.fouad.cuttingnotebook.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fouad.cuttingnotebook.data.db.OrderStatus
import com.fouad.cuttingnotebook.ui.Ar
import com.fouad.cuttingnotebook.ui.model.OrderCardUi
import com.fouad.cuttingnotebook.ui.theme.AppColors
import java.io.File

/**
 * The card on the home and history lists: model, what it is made of, and the three numbers
 * that matter while cutting (spec 5.2).
 */
@Composable
fun OrderCard(
    card: OrderCardUi,
    imageFileFor: (String) -> File,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val image = card.imagePath?.let(imageFileFor)?.takeIf { it.exists() }
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = card.modelName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = buildSubtitle(card)
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                StatusPill(card)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatBlock(Ar.REQUIRED, card.totals.required)
                StatBlock(Ar.CUT, card.totals.cut, MaterialTheme.colorScheme.primary)
                StatBlock(
                    label = Ar.REMAINING,
                    value = card.totals.remaining,
                    color = if (card.totals.remaining == 0) AppColors.done else AppColors.remaining,
                )
                if (card.totals.excess > 0) {
                    StatBlock(Ar.EXCESS, card.totals.excess, AppColors.excess)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(card: OrderCardUi) {
    when {
        card.status == OrderStatus.CLOSED -> Pill(
            text = Ar.STATUS_CLOSED,
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        card.totals.isComplete -> Pill(
            text = Ar.COMPLETE_BADGE,
            container = AppColors.done.copy(alpha = 0.18f),
            content = AppColors.done,
        )

        else -> Box(Modifier.size(0.dp))
    }
}

private fun buildSubtitle(card: OrderCardUi): String? {
    val parts = buildList {
        card.modelCode?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (!card.isDetailed) {
            add(Ar.SINGLE_TOTAL)
        } else {
            if (card.colors.isNotEmpty()) add(card.colors.joinToString("، "))
            if (card.sizes.isNotEmpty()) add("${Ar.SIZES_SUMMARY}: ${card.sizes.joinToString("، ")}")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
