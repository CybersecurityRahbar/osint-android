package com.cyberphantom.osintworkbench.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

 /* ---- structure ------------------------------------------------------- */

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = IntelColors.Hairline) {
    Box(
        modifier.fillMaxWidth().height(1.dp).background(
            Brush.horizontalGradient(
                listOf(IntelColors.SteelDark, color, IntelColors.SteelBright.copy(alpha = 0.42f), color, IntelColors.SteelDark)
            )
        )
    )
}

@Composable
fun IntelPanel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    contentPadding: Int = 14,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(IntelColors.SteelBright.copy(alpha = 0.72f), IntelColors.HairlineStrong, IntelColors.SteelDark)
            )
        )
    ) {
        Box(
            Modifier.background(
                Brush.verticalGradient(
                    listOf(IntelColors.PanelHigh.copy(alpha = 0.98f), IntelColors.Panel, IntelColors.PanelInset)
                )
            )
        ) {
            Row {
                if (accent != null) {
                    Box(
                        Modifier.width(3.dp).heightIn(min = 40.dp).background(
                            Brush.verticalGradient(listOf(IntelColors.SteelBright.copy(alpha = 0.85f), accent, accent.copy(alpha = 0.55f)))
                        )
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(contentPadding.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun MicroLabel(text: String, color: Color = IntelColors.TextFaint, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SectionBar(code: String, title: String, meta: String? = null, accent: Color = IntelColors.Signal) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier.size(3.dp, 13.dp).background(accent)
            )
            MicroLabel(code, accent)
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IntelColors.Text)
            Spacer(Modifier.weight(1f))
            if (!meta.isNullOrBlank()) MicroLabel(meta, IntelColors.TextFaint)
        }
        Hairline()
    }
}

@Composable
fun DataText(
    text: String,
    color: Color = IntelColors.Text,
    size: Int = 13,
    weight: FontWeight = FontWeight.Medium,
    maxLines: Int = 3,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = (size + 5).sp,
        fontFamily = FontFamily.Monospace,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/* ---- data display ---------------------------------------------------- */

@Composable
fun StatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(IntelColors.SteelBright.copy(alpha = 0.65f), IntelColors.HairlineStrong, IntelColors.SteelDark)
            )
        )
    ) {
        Box(
            Modifier.background(
                Brush.verticalGradient(
                    listOf(IntelColors.PanelHigh, IntelColors.Panel, IntelColors.PanelInset)
                )
            )
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(5.dp).clip(RoundedCornerShape(1.dp)).background(
                            Brush.verticalGradient(listOf(IntelColors.SteelGlow, accent))
                        )
                    )
                    MicroLabel(label, IntelColors.TextDim)
                }
                DataText(value, IntelColors.Text, size = 19, weight = FontWeight.Bold, maxLines = 1)
                if (caption != null) {
                    Text(caption, color = IntelColors.TextFaint, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color = IntelColors.Signal, filled: Boolean = false) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(color.copy(alpha = 0.72f), color.copy(alpha = 0.24f), IntelColors.SteelDark)
            )
        )
    ) {
        Box(
            Modifier.background(
                if (filled) Brush.verticalGradient(listOf(color.copy(alpha = 0.24f), color.copy(alpha = 0.07f)))
                else Brush.verticalGradient(listOf(IntelColors.PanelHigh.copy(alpha = 0.85f), IntelColors.PanelInset))
            )
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.4.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = IntelColors.Text) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(key, color = IntelColors.TextFaint, fontSize = 10.sp, modifier = Modifier.width(112.dp))
        DataText(value, valueColor, size = 11, maxLines = 4, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ConfidenceMeter(value: Int, accent: Color = IntelColors.Signal, showLabel: Boolean = true) {
    val clamped = value.coerceIn(0, 100)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier
                .width(46.dp)
                .height(3.dp)
                .background(IntelColors.Hairline)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(clamped / 100f)
                    .height(3.dp)
                    .background(accent)
            )
        }
        if (showLabel) {
            DataText("$clamped", accent, size = 9, weight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun SeverityDot(level: String) {
    Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp)).background(IntelColors.severity(level)))
}

/* ---- controls -------------------------------------------------------- */

@Composable
fun IntelField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    mono: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(3.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                fontSize = 13.sp,
                color = IntelColors.Text
            ),
            label = { Text(label, fontSize = 11.sp) },
            placeholder = placeholder?.let { { Text(it, fontSize = 11.sp, color = IntelColors.TextFaint) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IntelColors.Signal,
                unfocusedBorderColor = IntelColors.HairlineStrong,
                focusedLabelColor = IntelColors.Signal,
                unfocusedLabelColor = IntelColors.TextFaint,
                focusedTextColor = IntelColors.Text,
                unfocusedTextColor = IntelColors.Text,
                cursorColor = IntelColors.Cursor,
                focusedContainerColor = IntelColors.PanelInset,
                unfocusedContainerColor = IntelColors.PanelInset
            )
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    accent: Color = IntelColors.Signal
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(45.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(3.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color(0xFF031714),
            disabledContainerColor = IntelColors.PanelHigh,
            disabledContentColor = IntelColors.TextFaint
        )
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.5.dp, color = Color(0xFF031714))
            Spacer(Modifier.width(8.dp))
            Text("جارٍ التنفيذ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Color = IntelColors.TextDim
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        enabled = enabled,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, IntelColors.HairlineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ModeChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = IntelColors.Signal
) {
    Surface(
        modifier = modifier.height(33.dp).clickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(3.dp),
        border = if (selected) {
            BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(IntelColors.SteelGlow, accent, accent.copy(alpha = 0.5f), IntelColors.SteelDark)
                )
            )
        } else {
            BorderStroke(1.dp, IntelColors.Hairline)
        }
    ) {
        Box(
            Modifier
                .background(
                    if (selected) {
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.23f), IntelColors.PanelHigh, accent.copy(alpha = 0.08f))
                        )
                    } else {
                        Brush.verticalGradient(listOf(IntelColors.PanelHigh.copy(alpha = 0.8f), IntelColors.PanelInset))
                    }
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (selected) accent else IntelColors.TextDim,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    detail: String,
    accent: Color = IntelColors.Signal
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(IntelColors.PanelInset)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (checked) accent.copy(alpha = 0.9f) else Color.Transparent)
                .then(Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (!checked) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(IntelColors.HairlineStrong))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = IntelColors.Text)
            if (detail.isNotBlank()) {
                Text(detail, fontSize = 9.sp, color = IntelColors.TextFaint, lineHeight = 13.sp)
            }
        }
        MicroLabel(if (checked) "ON" else "OFF", if (checked) accent else IntelColors.TextFaint)
    }
}

@Composable
fun EmptyBlock(icon: ImageVector, title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = IntelColors.Panel,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, IntelColors.Hairline)
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(icon, contentDescription = null, tint = IntelColors.TextFaint, modifier = Modifier.size(20.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IntelColors.Text)
            Text(
                message,
                color = IntelColors.TextFaint,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun NoteBlock(text: String, accent: Color = IntelColors.Amber, label: String = "NOTE") {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.06f),
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            MicroLabel(label, accent)
            Text(text, color = IntelColors.TextDim, fontSize = 10.sp, lineHeight = 16.sp)
        }
    }
}
