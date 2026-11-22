package com.cyberphantom.osintworkbench.ui.telegram

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.NoteBlock

@Composable
fun PrivateSessionSettingsPanel() {
    val context = LocalContext.current
    val view = remember { PrivateSessionHost.getOrCreate(context) }
    var showSession by rememberSaveable { mutableStateOf(false) }

    IntelPanel(accent = IntelColors.Signal) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Public, contentDescription = null, tint = IntelColors.Signal, modifier = Modifier.size(17.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("جلسة المصدر", color = IntelColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                MicroLabel("PRIVATE SESSION · LOCAL WEB STORAGE", IntelColors.TextFaint)
            }
        }
        Text(
            "تسجيل الدخول وإدارة الجلسة يتمان هنا فقط. افتح النافذة المستقلة لإكمال تسجيل الدخول؛ تبقى الجلسة محفوظة على الجهاز وتستخدمها شاشة البحث بالمصادر الخاصة.",
            color = IntelColors.TextDim,
            fontSize = 9.sp,
            lineHeight = 14.sp
        )
        GhostButton(
            text = "فتح نافذة الجلسة",
            onClick = {
                view.onResume()
                showSession = true
            },
            icon = Icons.Filled.OpenInFull,
            accent = IntelColors.Signal
        )
    }

    if (showSession) {
        Dialog(
            onDismissRequest = {
                PrivateSessionHost.pause()
                showSession = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                color = IntelColors.Base,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, IntelColors.HairlineStrong)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("نافذة جلسة المصدر", color = IntelColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            MicroLabel("SESSION VIEW · FULL HEIGHT", IntelColors.Signal)
                        }
                        GhostButton(
                            text = "إغلاق",
                            onClick = {
                                PrivateSessionHost.pause()
                                showSession = false
                            },
                            icon = Icons.Filled.Close,
                            accent = IntelColors.TextDim
                        )
                    }
                    NoteBlock(
                        "استخدم التمرير داخل النافذة نفسها. لا توجد حاوية تمرير خارجية تحجب حركة الصفحة.",
                        IntelColors.Signal,
                        "SCROLL"
                    )
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { PrivateSessionHost.attach(view) },
                        update = { it.onResume() }
                    )
                    Spacer(Modifier.size(1.dp))
                }
            }
        }
    }
}
