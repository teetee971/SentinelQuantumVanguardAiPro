package com.sentinel.quantum.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SentinelEvidenceNode(
    val id: String,
    val label: String,
    val detail: String,
    val status: SentinelStatus,
    val source: String,
    val freshness: String
)

data class SentinelEvidenceEdge(
    val from: String,
    val to: String,
    val proofReference: String? = null
) {
    val isBackedByEvidence: Boolean
        get() = !proofReference.isNullOrBlank()
}

/** A compact proof graph. Edges without a reference are intentionally not presented as proof. */
@Composable
fun SentinelEvidenceGraph(
    nodes: List<SentinelEvidenceNode>,
    edges: List<SentinelEvidenceEdge>,
    modifier: Modifier = Modifier,
    onNodeClick: (SentinelEvidenceNode) -> Unit = {}
) {
    val visibleNodes = remember(nodes) { nodes.take(8) }
    val columns = 2
    val rowHeight = 92f
    val horizontalPadding = 10f
    val gap = 8f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
    ) {
        val nodeWidth = ((maxWidth.value - horizontalPadding * 2f - gap) / columns).coerceAtLeast(116f)
        val positions = visibleNodes.mapIndexed { index, _ ->
            val column = index % columns
            val row = index / columns
            Offset(
                x = horizontalPadding + column * (nodeWidth + gap),
                y = 14f + row * rowHeight
            )
        }
        val graphHeight = (14f + ((visibleNodes.size + columns - 1) / columns) * rowHeight).dp

        Box(modifier = Modifier.fillMaxWidth().heightIn(min = graphHeight)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                edges.filter { it.isBackedByEvidence }.forEach { edge ->
                    val fromIndex = visibleNodes.indexOfFirst { it.id == edge.from }
                    val toIndex = visibleNodes.indexOfFirst { it.id == edge.to }
                    if (fromIndex >= 0 && toIndex >= 0) {
                        val from = positions[fromIndex]
                        val to = positions[toIndex]
                        drawLine(
                            color = SentinelD1.Cyan.copy(alpha = 0.62f),
                            start = Offset(from.x + nodeWidth / 2f, from.y + 36f),
                            end = Offset(to.x + nodeWidth / 2f, to.y + 36f),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            visibleNodes.forEachIndexed { index, node ->
                val position = positions[index]
                Surface(
                    modifier = Modifier
                        .offset(x = position.x.dp, y = position.y.dp)
                        .fillMaxWidth(0.48f)
                        .clickable(role = Role.Button, onClick = { onNodeClick(node) })
                        .semantics {
                            contentDescription = "${node.label} · ${node.status.visual().label} · source ${node.source}"
                            role = Role.Button
                        },
                    shape = RoundedCornerShape(SentinelVisualTokens.CompactCornerRadiusDp.dp),
                    color = SentinelD1.Card.copy(alpha = 0.94f),
                    contentColor = node.status.accentColor()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(node.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(node.detail, fontSize = 11.sp, maxLines = 2)
                        Text(
                            text = "${node.source} · ${node.freshness}",
                            fontSize = 9.sp,
                            color = SentinelVisualTokens.TextSecondary
                        )
                    }
                }
            }
        }
    }
}
