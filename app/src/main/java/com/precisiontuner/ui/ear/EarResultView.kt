package com.precisiontuner.ui.ear

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.precisiontuner.ear.EarSessionState
import com.precisiontuner.ear.ResultTier

/**
 * 闯关结算: tier label + trophy, an accuracy ring with the rolling score in
 * the center, a stats card (per-mode notes) and restart / back actions.
 */
@Composable
fun EarResultView(
    session: EarSessionState,
    onRestart: () -> Unit,
    onBack: () -> Unit,
) {
    val accuracy = session.accuracy
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = ResultTier.label(accuracy),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))

        Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            AccuracyRing(accuracy = accuracy, modifier = Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val animatedScore by animateIntAsState(session.correctCount, tween(400), label = "resultScore")
                Text(
                    text = "$animatedScore",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "答对 / 共 ${session.answeredCount} 题",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                ResultStatRow("总答题数", "${session.answeredCount}")
                ResultStatRow("答对", "${session.correctCount}")
                ResultStatRow("正确率", "${(accuracy * 100).toInt()}%")
                ResultStatRow("最高连对", "${session.bestStreak}")
                when (session.endedReason) {
                    EarSessionState.EndedReason.LIVES_OVER -> ResultStatRow("结束原因", "生命耗尽")
                    EarSessionState.EndedReason.COMPLETED -> ResultStatRow("完成", "全部 ${session.questionLimit} 题")
                    EarSessionState.EndedReason.MANUAL_END,
                    null -> Unit
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("再来一局", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("返回设置")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ResultStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
