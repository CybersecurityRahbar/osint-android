package com.cyberphantom.osintworkbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.ApiProviderGroup
import com.cyberphantom.osintworkbench.data.ApiProviderId
import com.cyberphantom.osintworkbench.data.ApiProviderSettingsStore
import com.cyberphantom.osintworkbench.ui.design.DataText
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.Pill
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import com.cyberphantom.osintworkbench.ui.design.ToggleRow

private data class ApiProviderInfo(
    val id: ApiProviderId,
    val name: String,
    val summary: String,
    val capabilities: String,
    val freeQuota: String,
    val setup: String,
    val signupUrl: String,
    val limitation: String = ""
)

private val apiProviderInfo = listOf(
    ApiProviderInfo(
        ApiProviderId.SERP_API, "SerpApi", "بحث Google منظم عبر API",
        "نتائج ويب Google، ويمكنه دعم صور/أخبار Google عبر إعدادات المحرك. يستخدمه هذا التكامل للبحث العام عن النتائج.",
        "250 بحثاً شهرياً في الخطة المجانية. شرط البطاقة عند التسجيل الحالي غير محسوم؛ تحقق منه في صفحة التسجيل.",
        "أنشئ حساباً، ثم انسخ API key من لوحة التحكم والصقه في أحد الحقول الثلاثة.",
        "https://serpapi.com/manage-api-key",
        "المفتاح يمر في معامل api_key داخل طلب HTTPS لأن هذا ما تتطلبه واجهة SerpApi."
    ),
    ApiProviderInfo(
        ApiProviderId.TAVILY, "Tavily", "محرك بحث عام مهيأ للاسترجاع",
        "بحث عام أو إخباري، مقتطفات صفحات، وفلاتر زمنية ونطاقات. هذا الموصل يستخدم البحث العام.",
        "1,000 رصيد API شهرياً؛ البحث الأساسي يستهلك رصيداً واحداً والمتقدم رصيدين. التسجيل المجاني دون بطاقة.",
        "أنشئ حساباً مجانياً، ثم انسخ مفتاح API من لوحة الحساب.",
        "https://app.tavily.com/home"
    ),
    ApiProviderInfo(
        ApiProviderId.TINYFISH, "TinyFish", "TinySearch للبحث المنظم",
        "نتائج ويب بعناوين وروابط ومقتطفات، مع إمكانية اختيار اللغة وفلاتر التاريخ والنطاقات.",
        "حتى 12,000 طلب بحث مجاني يومياً، مع حد افتراضي 30 طلباً في الدقيقة للحساب الشخصي. التسجيل المجاني دون بطاقة.",
        "أنشئ حساب TinyFish، وافتح لوحة مفاتيح TinySearch وانسخ X-API-Key.",
        "https://agent.tinyfish.ai/"
    ),
    ApiProviderInfo(
        ApiProviderId.FIRECRAWL, "Firecrawl", "بحث واستخراج صفحات",
        "بحث ويب مع إمكانية طلب أخبار أو صور واستخراج محتوى الصفحات إلى Markdown/JSON. هذا الموصل يطلب نتائج الويب.",
        "1,000 رصيد مجاني شهرياً؛ البحث يستهلك رصيدين لكل 10 نتائج تقريباً. التسجيل المجاني دون بطاقة.",
        "أنشئ حساباً ثم أنشئ API key من لوحة Firecrawl.",
        "https://www.firecrawl.dev/app"
    ),
    ApiProviderInfo(
        ApiProviderId.EXA, "Exa API", "بحث دلالي وفهرسة صفحات",
        "بحث دلالي عن الويب والأشخاص والشركات والمنشورات والأخبار، مع مقتطفات ونصوص موجزة.",
        "رصيد مجاني شهري معلن بقيمة 10 دولارات، إضافة إلى 10 دولارات ترحيبية لمرة واحدة؛ لا تتطلب البداية بطاقة دفع.",
        "أنشئ حساب Exa وانسخ المفتاح من لوحة API Keys.",
        "https://dashboard.exa.ai/api-keys",
        "الرصيد المالي المجاني يستهلك بحسب نوع الطلب وعدد النتائج؛ لا يعني عدداً ثابتاً من الطلبات."
    ),
    ApiProviderInfo(
        ApiProviderId.CURRENTS, "Currents", "محرك أخبار متعدد اللغات",
        "بحث في الأخبار مع تاريخ النشر والرابط والناشر والصورة واللغة، ويمكن تصفية اللغة والبلد.",
        "250 طلباً يومياً، وحتى 20 مقالة في الطلب الواحد ضمن الخطة المجانية.",
        "أنشئ حساباً مجانياً، ثم انسخ API key من لوحة التحكم.",
        "https://currentsapi.services/en"
    ),
    ApiProviderInfo(
        ApiProviderId.THE_NEWS_API, "TheNewsAPI", "بحث الأخبار المباشر والتاريخي",
        "بحث عن المقالات حسب العبارة واللغة والمصدر والتاريخ، مع بيانات الناشر والرابط والوصف.",
        "100 طلب يومياً، وحتى 3 مقالات في الطلب على الخطة المجانية.",
        "أنشئ حساباً ثم انسخ API token من لوحة التحكم.",
        "https://www.thenewsapi.com/"
    ),
    ApiProviderInfo(
        ApiProviderId.NEWSDATA, "NewsData.io", "أخبار عالمية وعربية",
        "نتائج أخبار مع اللغة والمصدر وتاريخ النشر والوصف والصورة.",
        "200 رصيد يومياً، حتى 10 مقالات لكل رصيد؛ الخطة المجانية تتأخر قرابة 12 ساعة وتغطي آخر 48 ساعة.",
        "أنشئ حساباً مجانياً وانسخ API key من لوحة الحساب.",
        "https://newsdata.io/"
    ),
    ApiProviderInfo(
        ApiProviderId.GNEWS, "GNews", "بحث أخبار بعناوين ومقتطفات",
        "بحث أخبار عام حسب العبارة واللغة والوقت، مع اسم المصدر والرابط والصورة.",
        "100 طلب يومياً، حتى 10 مقالات لكل طلب، مع تأخير يقارب 12 ساعة في الخطة المجانية.",
        "أنشئ حساباً، تحقق من البريد ثم انسخ مفتاح API من لوحة التحكم.",
        "https://gnews.io/",
        "الخطة المجانية مخصصة للاستخدام الشخصي/الأكاديمي والتقييم غير التجاري فقط."
    )
)

@Composable
fun ApiKeySourcesSettings() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val store = remember(context) { ApiProviderSettingsStore(context) }
    var route by rememberSaveable { mutableStateOf("closed") }

    val selectedProvider = route.substringAfter("provider:", "")
        .takeIf(String::isNotBlank)
        ?.let { raw -> runCatching { ApiProviderId.valueOf(raw) }.getOrNull() }

    if (route == "closed") {
        SectionBar("01", "إعداد مفاتيح المصادر الخاصة", "9 API", IntelColors.Violet)
        IntelPanel(accent = IntelColors.Violet) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Key, contentDescription = null, tint = IntelColors.Violet, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("مفاتيح البحث والأخبار الخارجية", color = IntelColors.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("إدارة تسعة مزودين، وثلاثة مفاتيح بديلة لكل مزود.", color = IntelColors.TextFaint, fontSize = 9.sp, lineHeight = 14.sp)
                }
            }
            GhostButton(
                text = "فتح إعداد مفاتيح المصادر",
                onClick = { route = "home" },
                accent = IntelColors.Violet,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    when {
        selectedProvider != null -> {
            val info = apiProviderInfo.first { it.id == selectedProvider }
            ProviderKeyEditor(
                info = info,
                store = store,
                onBack = { route = if (selectedProvider.group == ApiProviderGroup.SEARCH) "search" else "news" },
                onOpenSignup = { runCatching { uriHandler.openUri(info.signupUrl) } }
            )
        }
        route == "home" -> {
            SectionBar("01", "إعداد مفاتيح المصادر الخاصة", "اختر الفئة", IntelColors.Violet)
            GhostButton("رجوع إلى الإعدادات", { route = "closed" }, Icons.Filled.ArrowBack)
            CategoryCard(
                title = "قسم مفاتيح البحث الشامل والتتبع",
                detail = "SerpApi · Tavily · TinyFish · Firecrawl · Exa API",
                enabledCount = apiProviderInfo.filter { it.id.group == ApiProviderGroup.SEARCH && store.isEnabled(it.id) }.size,
                total = 5,
                accent = IntelColors.Violet,
                icon = Icons.Filled.Search,
                onClick = { route = "search" }
            )
            CategoryCard(
                title = "قسم مفاتيح الأخبار والتقارير",
                detail = "Currents · TheNewsAPI · NewsData.io · GNews",
                enabledCount = apiProviderInfo.filter { it.id.group == ApiProviderGroup.NEWS && store.isEnabled(it.id) }.size,
                total = 4,
                accent = IntelColors.Blue,
                icon = Icons.Filled.Newspaper,
                onClick = { route = "news" }
            )
            NoteBlock(
                "أدخل مفاتيحك أنت فقط. تحفظ المفاتيح محلياً مشفرة باستخدام Android Keystore، ولا تُرفع إلى GitHub أو أي خادم تابع للتطبيق.",
                IntelColors.Green,
                "KEY STORAGE"
            )
        }
        route == "search" || route == "news" -> {
            val group = if (route == "search") ApiProviderGroup.SEARCH else ApiProviderGroup.NEWS
            val groupProviders = apiProviderInfo.filter { it.id.group == group }
            val title = if (group == ApiProviderGroup.SEARCH) "قسم مفاتيح البحث الشامل والتتبع" else "قسم مفاتيح الأخبار والتقارير"
            val accent = if (group == ApiProviderGroup.SEARCH) IntelColors.Violet else IntelColors.Blue
            SectionBar("02", title, groupProviders.size.toString() + " مصادر", accent)
            GhostButton("رجوع للفئتين", { route = "home" }, Icons.Filled.ArrowBack)
            groupProviders.forEach { info ->
                ProviderSummaryCard(
                    info = info,
                    store = store,
                    accent = accent,
                    onOpen = { route = "provider:" + info.id.name },
                    onEnabledChange = { store.setEnabled(info.id, it) }
                )
            }
        }
        else -> route = "home"
    }
}

@Composable
private fun CategoryCard(
    title: String,
    detail: String,
    enabledCount: Int,
    total: Int,
    accent: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    IntelPanel(accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = IntelColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = IntelColors.TextFaint, fontSize = 9.sp, lineHeight = 13.sp)
            }
            Pill(enabledCount.toString() + "/" + total + " ON", accent)
        }
        GhostButton("فتح القسم", onClick, accent = accent, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProviderSummaryCard(
    info: ApiProviderInfo,
    store: ApiProviderSettingsStore,
    accent: androidx.compose.ui.graphics.Color,
    onOpen: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    var enabled by remember(info.id) { mutableStateOf(store.isEnabled(info.id)) }
    val configured = (1..ApiProviderSettingsStore.KEY_SLOTS).count { store.hasEncryptedKey(info.id, it) }
    IntelPanel(accent = accent, contentPadding = 11) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(info.name, color = IntelColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(info.summary, color = IntelColors.TextFaint, fontSize = 9.sp)
            }
            Pill(configured.toString() + "/3 KEYS", if (configured > 0) IntelColors.Green else IntelColors.Amber)
        }
        ToggleRow(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                onEnabledChange(it)
            },
            title = "تفعيل هذا المصدر",
            detail = "تشغيله أو إيقافه مستقلاً عن بقية المزودين.",
            accent = accent
        )
        GhostButton("إعداد المفاتيح والقدرات", onOpen, accent = accent, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProviderKeyEditor(
    info: ApiProviderInfo,
    store: ApiProviderSettingsStore,
    onBack: () -> Unit,
    onOpenSignup: () -> Unit
) {
    var enabled by remember(info.id) { mutableStateOf(store.isEnabled(info.id)) }
    var revealKeys by remember(info.id) { mutableStateOf(false) }
    val drafts = remember(info.id) { mutableStateListOf("", "", "") }
    val storedKeys = remember(info.id) {
        mutableStateListOf(
            store.hasEncryptedKey(info.id, 1),
            store.hasEncryptedKey(info.id, 2),
            store.hasEncryptedKey(info.id, 3)
        )
    }

    SectionBar("03", "إعداد " + info.name, "API KEY", if (info.id.group == ApiProviderGroup.SEARCH) IntelColors.Violet else IntelColors.Blue)
    GhostButton("رجوع لقائمة المصادر", onBack, Icons.Filled.ArrowBack)
    IntelPanel(accent = if (info.id.group == ApiProviderGroup.SEARCH) IntelColors.Violet else IntelColors.Blue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(info.name, color = IntelColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(info.summary, color = IntelColors.TextFaint, fontSize = 9.sp)
            }
            Pill(if (enabled) "ENABLED" else "DISABLED", if (enabled) IntelColors.Green else IntelColors.TextFaint)
        }
        ToggleRow(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                store.setEnabled(info.id, it)
            },
            title = "تفعيل المصدر",
            detail = "عند إيقافه لن يستدعيه محرك البحث أو الأخبار حتى لو كانت مفاتيحه محفوظة.",
            accent = if (info.id.group == ApiProviderGroup.SEARCH) IntelColors.Violet else IntelColors.Blue
        )
        SectionBar("KEYS", "مفاتيح API البديلة", "3 SLOTS")
        Text(
            "أدخل حتى ثلاثة مفاتيح مستقلة. عند رفض المفتاح بسبب صلاحية أو رصيد أو حد طلبات، يجرب التطبيق المفتاح التالي تلقائياً.",
            color = IntelColors.TextDim,
            fontSize = 9.sp,
            lineHeight = 14.sp
        )
        repeat(ApiProviderSettingsStore.KEY_SLOTS) { index ->
            val slot = index + 1
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MicroLabel("API KEY SLOT 0" + slot, IntelColors.TextDim)
                    Spacer(Modifier.weight(1f))
                    Pill(if (storedKeys[index]) "SAVED · ENCRYPTED" else "EMPTY", if (storedKeys[index]) IntelColors.Green else IntelColors.TextFaint)
                }
                OutlinedTextField(
                    value = drafts[index],
                    onValueChange = { drafts[index] = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = IntelColors.Text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    label = { Text("الصق المفتاح " + slot, fontSize = 10.sp) },
                    placeholder = { Text(if (storedKeys[index]) "يوجد مفتاح محفوظ؛ اكتب مفتاحاً جديداً للاستبدال" else "API key", fontSize = 10.sp) },
                    visualTransformation = if (revealKeys) VisualTransformation.None else PasswordVisualTransformation(),
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GhostButton(
                        text = "حفظ المفتاح",
                        onClick = {
                            store.saveKey(info.id, slot, drafts[index])
                            drafts[index] = ""
                            storedKeys[index] = true
                        },
                        enabled = drafts[index].isNotBlank(),
                        accent = IntelColors.Green,
                        modifier = Modifier.weight(1f)
                    )
                    GhostButton(
                        text = "حذف",
                        onClick = {
                            store.clearKey(info.id, slot)
                            drafts[index] = ""
                            storedKeys[index] = false
                        },
                        enabled = storedKeys[index],
                        accent = IntelColors.Red,
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }
        }
        GhostButton(
            text = if (revealKeys) "إخفاء المفاتيح أثناء الكتابة" else "إظهار النص أثناء الكتابة",
            onClick = { revealKeys = !revealKeys },
            accent = IntelColors.TextDim
        )
        NoteBlock(
            "المفاتيح المحفوظة لا تظهر مرة أخرى في الحقول بعد مغادرة الصفحة؛ يظهر فقط أنها محفوظة. لن يُرسل أي مفتاح إلى سجل التطبيق.",
            IntelColors.Green,
            "LOCAL SECRET"
        )
    }

    IntelPanel {
        MicroLabel("WHAT THIS SOURCE CAN DO", IntelColors.Signal)
        DataText(info.capabilities, IntelColors.TextDim, size = 10, maxLines = 8)
        SectionBar("FREE PLAN", "الحدود المجانية المعلنة")
        DataText(info.freeQuota, IntelColors.Text, size = 10, maxLines = 8)
        if (info.limitation.isNotBlank()) {
            NoteBlock(info.limitation, IntelColors.Amber, "IMPORTANT")
        }
        SectionBar("SETUP", "طريقة الحصول على المفتاح")
        DataText(info.setup, IntelColors.TextDim, size = 10, maxLines = 6)
        GhostButton(
            text = "فتح الموقع الرسمي",
            onClick = onOpenSignup,
            icon = Icons.Filled.OpenInNew,
            accent = IntelColors.Signal,
            modifier = Modifier.fillMaxWidth()
        )
        Text("قد تتغير الحصص والأسعار وشروط الخطة؛ المرجع النهائي هو لوحة المزود وقت الاستخدام.", color = IntelColors.TextFaint, fontSize = 8.sp, lineHeight = 12.sp)
    }
}
