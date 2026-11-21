package com.cyberphantom.osintworkbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.EntityRow
import com.cyberphantom.osintworkbench.data.RelationRow
import com.cyberphantom.osintworkbench.ui.design.ConfidenceMeter
import com.cyberphantom.osintworkbench.ui.design.DataText
import com.cyberphantom.osintworkbench.ui.design.EmptyBlock
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelField
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.ModeChip
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.Pill

@Composable
fun NetworkScreen(entities: List<EntityRow>, relations: List<RelationRow>) {
    var showRelations by rememberSaveable { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val needle = query.trim().lowercase()
    val visibleEntities = remember(entities, needle) {
        entities.filter { needle.isBlank() || it.value.lowercase().contains(needle) || it.kind.lowercase().contains(needle) }
    }
    val visibleRelations = remember(relations, needle) {
        relations.filter { needle.isBlank() || it.fromValue.lowercase().contains(needle) || it.toValue.lowercase().contains(needle) }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            ScreenLead(
                code = "MODULE 04 / GRAPH",
                title = "شبكة العلاقات",
                subtitle = "كل صف هو دليل موثق بمصدره ودرجة ثقته؛ لا يُستنتج ارتباط لم يُرصد",
                icon = Icons.Filled.AccountTree,
                accent = IntelColors.Amber
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ModeChip(
                    selected = !showRelations,
                    label = "الكيانات (${entities.size})",
                    onClick = { showRelations = false },
                    modifier = Modifier.weight(1f),
                    accent = IntelColors.Amber
                )
                ModeChip(
                    selected = showRelations,
                    label = "العلاقات (${relations.size})",
                    onClick = { showRelations = true },
                    modifier = Modifier.weight(1f),
                    accent = IntelColors.Amber
                )
            }
            IntelField(
                value = query,
                onValueChange = { query = it },
                label = "تصفية",
                placeholder = "نطاق، نوع، مصدر…"
            )
        }

        if (showRelations) {
            if (visibleRelations.isEmpty()) {
                Column(Modifier.padding(horizontal = 13.dp)) {
                    EmptyBlock(
                        icon = Icons.Filled.Hub,
                        title = "لا توجد علاقات",
                        message = "شغّل استطلاعاً أو بحثاً لتُبنى العلاقات تلقائياً."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 13.dp, end = 13.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(visibleRelations, key = { "${it.fromValue}|${it.relation}|${it.toValue}|${it.observedAt}" }) { edge ->
                        RelationCard(edge)
                    }
                }
            }
        } else {
            if (visibleEntities.isEmpty()) {
                Column(Modifier.padding(horizontal = 13.dp)) {
                    EmptyBlock(
                        icon = Icons.Filled.Hub,
                        title = "لا توجد كيانات محفوظة",
                        message = "ابدأ باستطلاع أصل أو بحث معرّف؛ تُحفظ النتائج محلياً مع مصادرها."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 13.dp, end = 13.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(visibleEntities, key = { it.id }) { entity -> EntityCard(entity) }
                }
                NoteBlock(
                    "المعروض أول 600 كيان للأداء. صدّر المساحة من الإعدادات للاطلاع على الكل.",
                    IntelColors.TextDim,
                    "DISPLAY"
                )
            }
        }
    }
}

@Composable
private fun EntityCard(entity: EntityRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IntelColors.Panel, RoundedCornerShape(2.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(entity.kind.substringBefore('@'), kindColor(entity.kind))
            Spacer(Modifier.weight(1f))
            ConfidenceMeter(entity.confidence)
        }
        DataText(entity.value, IntelColors.Text, size = 11, maxLines = 3)
        MicroLabel(entity.source.ifBlank { "—" }, IntelColors.TextFaint)
        entity.kind.substringAfter('@', "").takeIf { it.isNotBlank() }?.let { scope ->
            MicroLabel("SCOPE: $scope", IntelColors.TextFaint)
        }
    }
}

@Composable
private fun RelationCard(edge: RelationRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IntelColors.Panel, RoundedCornerShape(2.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(edge.relation.replace('_', ' '), IntelColors.Amber)
            Spacer(Modifier.weight(1f))
            ConfidenceMeter(edge.confidence)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DataText(edge.fromValue, IntelColors.Text, size = 10, maxLines = 2)
                MicroLabel(edge.fromKind.substringBefore('@'), IntelColors.TextFaint)
            }
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = IntelColors.Signal, modifier = Modifier.size(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DataText(edge.toValue, IntelColors.Text, size = 10, maxLines = 2)
                MicroLabel(edge.toKind.substringBefore('@'), IntelColors.TextFaint)
            }
        }
        MicroLabel(edge.source.ifBlank { "—" }, IntelColors.TextFaint)
    }
}

private fun kindColor(kind: String) = when {
    kind.startsWith("DNS") -> IntelColors.Signal
    kind.startsWith("EMAIL") -> IntelColors.Violet
    kind.startsWith("ARCHIVE") || kind.startsWith("WAYBACK") -> IntelColors.Blue
    kind.startsWith("NEWS") -> IntelColors.Blue
    kind.startsWith("ACCOUNT") || kind.startsWith("PUBLIC") -> IntelColors.Amber
    kind.startsWith("IDENTIFIER") -> IntelColors.TextDim
    else -> IntelColors.TextDim
}
