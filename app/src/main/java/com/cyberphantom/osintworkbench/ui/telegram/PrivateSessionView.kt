package com.cyberphantom.osintworkbench.ui.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.NoteBlock

/**
 * Settings entry point for the single persistent Telegram WebView mounted by WorkbenchApp.
 * It does not create a Dialog/WebView and therefore never detaches or replaces the saved session.
 */
@Composable
fun PrivateSessionSettingsPanel() {
    val focusManager = LocalFocusManager.current
    val sessionVisible by PrivateSessionHost.sessionVisible

    IntelPanel(accent = IntelColors.Signal) {
        RowHeader()
        Text(
            "يستخدم التطبيق جلسة Telegram Web الموجودة على هذا الجهاز. بما أنك سجلت الدخول سابقاً، " +
                "لن ننشئ جلسة جديدة ولن نطلب تسجيل الدخول مع كل بحث. افتح نافذة الجلسة نفسها للتصفح؛ " +
                "وعند إغلاقها تبقى WebView متصلة ويظل تخزين Telegram كما هو.",
            color = IntelColors.TextDim,
            fontSize = 9.sp,
            lineHeight = 14.sp
        )
        GhostButton(
            text = if (sessionVisible) "العودة إلى واجهة التطبيق" else "فتح جلسة Telegram الحالية",
            onClick = {
                focusManager.clearFocus()
                if (sessionVisible) PrivateSessionHost.hideSession()
                else PrivateSessionHost.showSession()
            },
            icon = Icons.Filled.Public,
            accent = IntelColors.Signal,
            modifier = Modifier.fillMaxWidth()
        )
        NoteBlock(
            "لا يتم حذف Cookies أو Local Storage أو IndexedDB. البحث الآلي يستخدم نفس WebView ونفس ملف بيانات الموقع.",
            IntelColors.Signal,
            "SINGLE PERSISTENT SESSION"
        )
    }
}

@Composable
private fun RowHeader() {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.Public,
            contentDescription = null,
            tint = IntelColors.Signal
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                "جلسة Telegram المحفوظة",
                color = IntelColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            MicroLabel("PERSISTENT WEB SESSION · SAME DEVICE PROFILE", IntelColors.TextFaint)
        }
    }
}
