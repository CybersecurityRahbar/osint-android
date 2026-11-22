package com.cyberphantom.osintworkbench.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.domain.AdvancedPublicSearchEngine
import com.cyberphantom.osintworkbench.domain.IdentifierKind
import com.cyberphantom.osintworkbench.domain.IntelFusion
import com.cyberphantom.osintworkbench.domain.FusionProfile
import com.cyberphantom.osintworkbench.domain.MultiSourceNewsEngine
import com.cyberphantom.osintworkbench.domain.NewsArticle
import com.cyberphantom.osintworkbench.domain.NewsSearchResult
import com.cyberphantom.osintworkbench.domain.PublicProfileResult
import com.cyberphantom.osintworkbench.domain.PublicResearchEngine
import com.cyberphantom.osintworkbench.domain.UsernameEnumerationEngine
import com.cyberphantom.osintworkbench.domain.UsernameHit
import com.cyberphantom.osintworkbench.ui.design.ActionButton
import com.cyberphantom.osintworkbench.ui.design.ConfidenceMeter
import com.cyberphantom.osintworkbench.ui.design.DataText
import com.cyberphantom.osintworkbench.ui.design.EmptyBlock
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.Hairline
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.IntelField
import com.cyberphantom.osintworkbench.ui.design.IntelPanel
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.ModeChip
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.Pill
import com.cyberphantom.osintworkbench.ui.design.SectionBar
import com.cyberphantom.osintworkbench.ui.design.StatTile
import com.cyberphantom.osintworkbench.ui.design.ToggleRow
import com.cyberphantom.osintworkbench.ui.util.safeFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class ResearchMode(val label: String) {
    IDENTIFIERS("المعرّفات"),
    NEWS("الأخبار")
}

@Composable
fun ResearchScreen(
    store: LocalStore,
    initialQuery: String,
    startInNews: Boolean,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(if (startInNews) ResearchMode.NEWS else ResearchMode.IDENTIFIERS) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var kind by rememberSaveable { mutableStateOf(IdentifierKind.NAME) }
    var includeImages by rememberSaveable { mutableStateOf(true) }
    var runUsernameScan by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var progressMessage by rememberSaveable { mutableStateOf("جاهز لبدء البحث") }
    var progressFraction by remember { mutableStateOf(0.02f) }
    var operationStartedAt by remember { mutableStateOf(0L) }
    var liveElapsedMs by remember { mutableStateOf(0L) }
    var completedElapsedMs by remember { mutableStateOf(0L) }
    var newsRangeEnabled by rememberSaveable { mutableStateOf(false) }
    var newsFromMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L) }
    var newsToMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var report by remember { mutableStateOf<com.cyberphantom.osintworkbench.domain.PublicWebSearchResponse?>(null) }
    var fusion by remember { mutableStateOf<FusionProfile?>(null) }
    var usernameHits by remember { mutableStateOf<List<UsernameHit>>(emptyList()) }
    var phoneMeta by remember { mutableStateOf<String?>(null) }
    var news by remember { mutableStateOf<NewsSearchResult?>(null) }
    var pendingJson by remember { mutableStateOf("") }

    val state = remember { PublicResearchEngine() }
    val links = remember(query, kind) {
        if (query.isBlank()) emptyList() else runCatching { state.buildSearchLinks(query, kind) }.getOrDefault(emptyList())
    }

    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val body = pendingJson
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(body) }
                        ?: error("no stream")
                }.isSuccess
            }
            Toast.makeText(context, if (saved) "تم حفظ نتائج العملية" else "تعذر الحفظ", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(initialQuery, startInNews) {
        if (initialQuery.isNotBlank()) query = initialQuery
        mode = if (startInNews) ResearchMode.NEWS else ResearchMode.IDENTIFIERS
    }

    LaunchedEffect(busy, operationStartedAt) {
        if (!busy) return@LaunchedEffect
        while (busy) {
            liveElapsedMs = (SystemClock.elapsedRealtime() - operationStartedAt).coerceAtLeast(0L)
            delay(160L)
        }
    }

    ScreenColumn {
        ScreenLead(
            code = "MODULE 03 / INTEL",
            title = "البحث الاستخباراتي",
            subtitle = "فهارس عامة متعددة، تحليل تجميعي، أخبار مؤرشفة، ومصادر خاصة",
            icon = Icons.Filled.TravelExplore,
            accent = IntelColors.Violet
        )

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ResearchMode.entries.forEach { item ->
                ModeChip(
                    selected = mode == item,
                    label = item.label,
                    onClick = { mode = item; error = null },
                    modifier = Modifier.weight(1f),
                    accent = IntelColors.Violet
                )
            }
        }

        if (busy || completedElapsedMs > 0L) {
            SearchProgressPanel(
                message = progressMessage,
                progress = progressFraction,
                elapsedMs = if (busy) liveElapsedMs else completedElapsedMs,
                accent = if (mode == ResearchMode.NEWS) IntelColors.Blue else IntelColors.Violet,
                running = busy
            )
        }

        when (mode) {
            ResearchMode.NEWS -> {
                IntelPanel(accent = IntelColors.Blue) {
                    MicroLabel("NEWS / MULTI-SOURCE", IntelColors.Blue)
                    IntelField(
                        value = query,
                        onValueChange = { query = it; error = null },
                        label = "كلمات البحث",
                        placeholder = "اسم، حدث، منطقة…"
                    )
                    ToggleRow(
                        checked = newsRangeEnabled,
                        onCheckedChange = { enabled ->
                            newsRangeEnabled = enabled
                            if (enabled && newsToMillis < newsFromMillis) {
                                newsToMillis = System.currentTimeMillis()
                                newsFromMillis = newsToMillis - 7L * 24L * 60L * 60L * 1000L
                            }
                        },
                        title = "تحديد فترة النشر",
                        detail = "تصفية المقالات من تاريخ ووقت البداية إلى تاريخ ووقت النهاية.",
                        accent = IntelColors.Blue
                    )
                    if (newsRangeEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GhostButton(
                                text = "من: ${formatNewsDateTime(newsFromMillis)}",
                                onClick = {
                                    showDateTimePicker(context, newsFromMillis) { selected -> newsFromMillis = selected }
                                },
                                modifier = Modifier.weight(1f),
                                accent = IntelColors.Blue
                            )
                            GhostButton(
                                text = "إلى: ${formatNewsDateTime(newsToMillis)}",
                                onClick = {
                                    showDateTimePicker(context, newsToMillis) { selected -> newsToMillis = selected }
                                },
                                modifier = Modifier.weight(1f),
                                accent = IntelColors.Blue
                            )
                        }
                        if (newsFromMillis > newsToMillis) {
                            Text("وقت البداية يجب أن يسبق وقت النهاية.", color = IntelColors.Amber, fontSize = 9.sp)
                        }
                        MicroLabel("FILTER · ${formatNewsDateTime(newsFromMillis)}  →  ${formatNewsDateTime(newsToMillis)}", IntelColors.TextFaint)
                    }
                    ActionButton(
                        text = "جمع الأخبار",
                        onClick = {
                            scope.launch {
                                busy = true
                                operationStartedAt = SystemClock.elapsedRealtime()
                                liveElapsedMs = 0L
                                completedElapsedMs = 0L
                                progressMessage = "تهيئة مصادر الأخبار…"
                                progressFraction = 0.04f
                                error = null
                                news = null
                                try {
                                    val result = MultiSourceNewsEngine().search(
                                        query = query.trim(),
                                        maxRecords = 60,
                                        includeImages = includeImages,
                                        startTimeMillis = if (newsRangeEnabled) newsFromMillis else null,
                                        endTimeMillis = if (newsRangeEnabled) newsToMillis else null,
                                        onProgress = { message -> scope.launch {
                                            progressMessage = message
                                            progressFraction = progressFor(message)
                                        } }
                                    )
                                    news = result
                                    progressMessage = "اكتمل جمع الأخبار وفرزها."
                                    progressFraction = 1f
                                    runCatching { persistNews(store, result) }
                                        .onFailure { error = "تعذر حفظ النتائج: ${it.message}" }
                                    onSaved()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (exception: Exception) {
                                    error = exception.message ?: "تعذر جلب الأخبار."
                                    progressMessage = "توقف البحث بسبب خطأ."
                                } finally {
                                    completedElapsedMs = (SystemClock.elapsedRealtime() - operationStartedAt).coerceAtLeast(0L)
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Newspaper,
                        loading = busy,
                        enabled = query.isNotBlank() && (!newsRangeEnabled || newsFromMillis <= newsToMillis),
                        accent = IntelColors.Blue
                    )
                    error?.let { Text(it, color = IntelColors.Red, fontSize = 10.sp, lineHeight = 15.sp) }
                }

                news?.let { result ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StatTile("ARTICLES", result.articles.size.toString(), IntelColors.Blue, Modifier.weight(1f))
                        StatTile("PUBLISHERS", result.articles.map { it.publisherName.ifBlank { it.domain } }.distinct().size.toString(), IntelColors.Signal, Modifier.weight(1f))
                        StatTile("SOURCES", result.sourcesAttempted.toString(), IntelColors.Violet, Modifier.weight(1f))
                    }
                    result.warning?.let { NoteBlock(it, IntelColors.Amber, "SOURCE NOTE") }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        GhostButton(
                            text = "حفظ النتائج JSON",
                            onClick = {
                                pendingJson = newsJson(result)
                                jsonLauncher.launch("news-${safeFileName(result.query)}.json")
                            },
                            icon = Icons.Filled.Link
                        )
                    }
                    if (result.articles.isEmpty()) {
                        EmptyBlock(
                            icon = Icons.Filled.Newspaper,
                            title = "لا توجد مقالات",
                            message = "جرّب كلمات أوسع أو بالإنجليزية؛ بعض المصادر تحدّ الطلبات المتكررة."
                        )
                    } else {
                        SectionBar("01", "التغطية", "${result.articles.size} مقالة")
                        result.articles.forEach { article -> NewsRow(article) { runCatching { uriHandler.openUri(article.url) } } }
                    }
                }
            }
            ResearchMode.IDENTIFIERS -> {
                IntelPanel(accent = IntelColors.Violet) {
                    MicroLabel("IDENTIFIER / DISCOVERY", IntelColors.Violet)
                    IntelField(
                        value = query,
                        onValueChange = { query = it; error = null; report = null; fusion = null },
                        label = "القيمة",
                        placeholder = kind.placeholder()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IdentifierKind.entries.forEach { item ->
                            ModeChip(
                                selected = kind == item,
                                label = item.label,
                                onClick = { kind = item; report = null; fusion = null; usernameHits = emptyList() },
                                modifier = Modifier.weight(1f),
                                accent = IntelColors.Violet
                            )
                        }
                    }
                    ToggleRow(
                        checked = includeImages,
                        onCheckedChange = { includeImages = it },
                        title = "تضمين صور المعاينة",
                        detail = "يبطئ الجمع قليلاً لكنه يفيد التحقق البصري من الحسابات والمواقع.",
                        accent = IntelColors.Violet
                    )
                    if (kind == IdentifierKind.USERNAME) {
                        ToggleRow(
                            checked = runUsernameScan,
                            onCheckedChange = { runUsernameScan = it },
                            title = "فحص وجود الحساب على المنصات",
                            detail = "تحقق مباشر من 30 منصة عبر واجهاتها العامة؛ المحجوب والمشكوك يُوسم صراحة.",
                            accent = IntelColors.Violet
                        )
                    }
                    ActionButton(
                        text = "تشغيل البحث",
                        onClick = {
                            scope.launch {
                                busy = true
                                operationStartedAt = SystemClock.elapsedRealtime()
                                liveElapsedMs = 0L
                                completedElapsedMs = 0L
                                progressMessage = "تهيئة فهارس المعرفات…"
                                progressFraction = 0.04f
                                error = null
                                report = null
                                fusion = null
                                usernameHits = emptyList()
                                phoneMeta = null
                                try {
                                    val engine = AdvancedPublicSearchEngine()
                                    val result = engine.search(
                                        rawQuery = query.trim(),
                                        kind = kind,
                                        maxResults = 60,
                                        includeImages = includeImages,
                                        onProgress = { message -> scope.launch {
                                            progressMessage = message
                                            progressFraction = progressFor(message)
                                        } }
                                    )
                                    report = result
                                    val fused = withContext(Dispatchers.Default) { IntelFusion.analyze(query.trim(), kind, result.results) }
                                    fusion = fused
                                    if (kind == IdentifierKind.PHONE) {
                                        phoneMeta = runCatching { engine.phoneMetadata(query.trim()) }.getOrNull()
                                    }
                                    if (kind == IdentifierKind.USERNAME && runUsernameScan) {
                                        progressMessage = "فحص وجود المعرّف على المنصات العامة…"
                                        usernameHits = UsernameEnumerationEngine().scan(query.trim())
                                    }
                                    runCatching { persistIdentifierSearch(store, query.trim(), kind, result, fused, usernameHits) }
                                        .onFailure { error = "تعذر حفظ النتائج: ${it.message}" }
                                    progressMessage = "اكتمل التحليل ودمج النتائج."
                                    progressFraction = 1f
                                    onSaved()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (exception: Exception) {
                                    error = exception.message ?: "تعذر إكمال البحث."
                                    progressMessage = "توقف البحث بسبب خطأ."
                                } finally {
                                    completedElapsedMs = (SystemClock.elapsedRealtime() - operationStartedAt).coerceAtLeast(0L)
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.PersonSearch,
                        loading = busy,
                        enabled = query.isNotBlank(),
                        accent = IntelColors.Violet
                    )
                    error?.let { Text(it, color = IntelColors.Red, fontSize = 10.sp, lineHeight = 15.sp) }
                }

                if (links.isNotEmpty()) {
                    SectionBar("01", "قنوات البحث المباشرة", "${links.size}")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        links.forEach { link ->
                            ModeChip(
                                selected = false,
                                label = link.provider,
                                onClick = { runCatching { uriHandler.openUri(link.url) } },
                                accent = IntelColors.Blue
                            )
                        }
                    }
                }

                fusion?.let { profile ->
                    SectionBar("02", "تحليل التجميع", "FUSION")
                    IntelPanel(accent = if (profile.confidence >= 55) IntelColors.Signal else IntelColors.Amber) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                MicroLabel("ASSESSMENT", IntelColors.TextFaint)
                                Text(profile.verdict, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IntelColors.Text)
                            }
                            ConfidenceMeter(profile.confidence)
                        }
                        Hairline()
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            StatTile("RESULTS", profile.resultCount.toString(), IntelColors.Blue, Modifier.weight(1f))
                            StatTile("PROVIDERS", profile.providerCount.toString(), IntelColors.Signal, Modifier.weight(1f))
                            StatTile("HOSTS", profile.hostCount.toString(), IntelColors.Violet, Modifier.weight(1f))
                        }
                        if (profile.identifierTotal > 0) {
                            MicroLabel("المعرّفات المستخرجة", IntelColors.TextFaint)
                            IdentifierGroup("بريد", profile.emails)
                            IdentifierGroup("هاتف", profile.phones)
                            IdentifierGroup("مستخدم", profile.handles)
                            IdentifierGroup("نطاق", profile.domains)
                            IdentifierGroup("IP", profile.ips)
                            IdentifierGroup("محفظة", profile.wallets)
                        }
                        if (profile.topCountries.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                profile.topCountries.take(4).forEach { (country, count) ->
                                    Pill("$country $count", IntelColors.Blue)
                                }
                            }
                        }
                        profile.alerts.take(5).forEach { alert ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { runCatching { uriHandler.openUri(alert.url) } },
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = IntelColors.Amber, modifier = Modifier.size(13.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(alert.title, fontSize = 9.sp, color = IntelColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    MicroLabel("${alert.keyword} · ${alert.provider}", IntelColors.TextFaint)
                                }
                            }
                        }
                        profile.notes.take(3).forEach { note ->
                            Text(note, fontSize = 8.sp, color = IntelColors.TextFaint, lineHeight = 12.sp)
                        }
                    }
                }

                phoneMeta?.let { meta ->
                    SectionBar("03", "بيانات الرقم", "OFFLINE")
                    IntelPanel {
                        DataText(meta, IntelColors.TextDim, size = 10, maxLines = 8)
                        Text(
                            "مشتقة من خطط الترقيم فقط: لا تثبت الملكية ولا الحالة الحالية للرقم.",
                            fontSize = 8.sp,
                            color = IntelColors.TextFaint,
                            lineHeight = 12.sp
                        )
                    }
                }

                if (usernameHits.isNotEmpty()) {
                    val found = usernameHits.count { it.found }
                    SectionBar("04", "انتشار اسم المستخدم", "$found / ${usernameHits.size}")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StatTile("FOUND", found.toString(), IntelColors.Green, Modifier.weight(1f))
                        StatTile("BLOCKED", usernameHits.count { it.status.label == "محجوب/محدود" }.toString(), IntelColors.Amber, Modifier.weight(1f))
                        StatTile("UNKNOWN", usernameHits.count { it.status.label == "غير مؤكد" }.toString(), IntelColors.TextFaint, Modifier.weight(1f))
                    }
                    usernameHits.sortedByDescending { it.found }.forEach { hit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(IntelColors.Panel, RoundedCornerShape(2.dp))
                                .clickable(enabled = hit.found) { runCatching { uriHandler.openUri(hit.url) } }
                                .padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Pill(
                                hit.status.label,
                                when (hit.status) {
                                    com.cyberphantom.osintworkbench.domain.ProbeStatus.FOUND -> IntelColors.Green
                                    com.cyberphantom.osintworkbench.domain.ProbeStatus.BLOCKED -> IntelColors.Amber
                                    com.cyberphantom.osintworkbench.domain.ProbeStatus.NOT_FOUND -> IntelColors.TextFaint
                                    else -> IntelColors.Blue
                                }
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(hit.platform, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IntelColors.Text)
                                DataText(hit.detail, IntelColors.TextFaint, size = 8, maxLines = 1)
                            }
                            ConfidenceMeter(hit.confidence)
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = IntelColors.TextFaint, modifier = Modifier.size(12.dp))
                        }
                    }
                }

                report?.let { result ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StatTile("RESULTS", result.results.size.toString(), IntelColors.Violet, Modifier.weight(1f))
                        StatTile("SOURCES", result.sourceCount.toString(), IntelColors.Blue, Modifier.weight(1f))
                        StatTile("WARN", result.warnings.size.toString(), IntelColors.Amber, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        GhostButton(
                            text = "حفظ JSON",
                            onClick = {
                                pendingJson = identifierJson(query, kind, result, fusion, usernameHits)
                                jsonLauncher.launch("search-${safeFileName(query)}.json")
                            },
                            icon = Icons.Filled.Link
                        )
                        MicroLabel("${result.providersAttempted} مصدر تمت محاولته", IntelColors.TextFaint)
                    }
                    result.warnings.forEach { warning ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = IntelColors.Amber, modifier = Modifier.size(12.dp))
                            Text(warning, fontSize = 8.sp, color = IntelColors.TextFaint, lineHeight = 12.sp)
                        }
                    }
                    if (result.results.isEmpty()) {
                        EmptyBlock(
                            icon = Icons.Filled.Bolt,
                            title = "لم تُعد الفهارس نتائج",
                            message = "قد تكون القيمة نادرة أو أن المصادر تحدّ الطلبات. جرّب صياغة أخرى أو نوع معرّف مختلف."
                        )
                    } else {
                        SectionBar("05", "النتائج", "${result.results.size}")
                        result.results.forEach { item ->
                            ResultRow(item) { runCatching { uriHandler.openUri(item.url) } }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(2.dp))
    }
}

@Composable
private fun ResultRow(result: PublicProfileResult, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IntelColors.Panel, RoundedCornerShape(2.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(result.provider, IntelColors.Signal)
            Spacer(Modifier.weight(1f))
            ConfidenceMeter(result.confidence)
        }
        Text(result.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = IntelColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (result.description.isNotBlank()) {
            Text(result.description, fontSize = 9.sp, color = IntelColors.TextDim, lineHeight = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (!result.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = result.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).background(IntelColors.PanelInset, RoundedCornerShape(2.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        Hairline()
        Row(verticalAlignment = Alignment.CenterVertically) {
            DataText(result.url, IntelColors.TextFaint, size = 8, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.OpenInNew, contentDescription = "فتح", tint = IntelColors.Signal, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun NewsRow(article: NewsArticle, onOpen: () -> Unit) {
    IntelPanel(
        modifier = Modifier.clickable(onClick = onOpen),
        accent = IntelColors.Blue,
        contentPadding = 10
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(article.publisherName.ifBlank { article.domain }, IntelColors.Blue)
            Spacer(Modifier.weight(1f))
            MicroLabel(formatPublishedDate(article.publishedAt), IntelColors.TextFaint)
        }
        Text(article.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = IntelColors.Text, lineHeight = 17.sp)
        if (article.description.isNotBlank()) {
            Text(
                article.description,
                color = IntelColors.TextDim,
                fontSize = 9.sp,
                lineHeight = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!article.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).background(IntelColors.PanelInset, RoundedCornerShape(2.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("\${article.provider} · \${article.language.ifBlank { "—" }}", IntelColors.TextFaint, Modifier.weight(1f))
            DataText(article.domain, IntelColors.TextFaint, size = 8, maxLines = 1)
        }
    }
}

@Composable
private fun IdentifierGroup(label: String, hits: List<com.cyberphantom.osintworkbench.domain.IdentifierHit>) {
    if (hits.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = IntelColors.TextFaint, fontSize = 8.sp, modifier = Modifier.padding(end = 2.dp))
        hits.take(8).forEach { hit ->
            Pill("${hit.value} ×${hit.count}", IntelColors.Signal)
        }
    }
}

private fun IdentifierKind.placeholder(): String = when (this) {
    IdentifierKind.NAME -> "الاسم الكامل"
    IdentifierKind.USERNAME -> "@username"
    IdentifierKind.PHONE -> "+967…"
    IdentifierKind.EMAIL -> "name@example.com"
}

private fun newsJson(result: NewsSearchResult): String {
    val root = JSONObject()
        .put("schemaVersion", 1)
        .put("kind", "news")
        .put("query", result.query)
        .put("exportedAt", System.currentTimeMillis())
        .put("warning", result.warning)
    val items = JSONArray()
    result.articles.forEach { article ->
        items.put(
            JSONObject()
                .put("title", article.title)
                .put("url", article.url)
                .put("domain", article.domain)
                .put("publishedAt", article.publishedAt)
                .put("language", article.language)
                .put("publisher", article.publisherName)
                .put("provider", article.provider)
                .put("imageUrl", article.imageUrl)
        )
    }
    return root.put("articles", items).toString(2)
}

private fun identifierJson(
    query: String,
    kind: IdentifierKind,
    report: com.cyberphantom.osintworkbench.domain.PublicWebSearchResponse,
    fusion: FusionProfile?,
    hits: List<UsernameHit>
): String {
    val root = JSONObject()
        .put("schemaVersion", 1)
        .put("kind", kind.name)
        .put("query", query)
        .put("exportedAt", System.currentTimeMillis())
        .put("warnings", JSONArray(report.warnings))
    val results = JSONArray()
    report.results.forEach { item ->
        results.put(
            JSONObject()
                .put("provider", item.provider)
                .put("title", item.title)
                .put("description", item.description)
                .put("url", item.url)
                .put("confidence", item.confidence)
                .put("imageUrl", item.imageUrl)
        )
    }
    root.put("results", results)
    if (hits.isNotEmpty()) {
        root.put(
            "usernameHits",
            JSONArray().apply {
                hits.forEach { hit ->
                    put(
                        JSONObject()
                            .put("platform", hit.platform)
                            .put("status", hit.status.name)
                            .put("url", hit.url)
                            .put("confidence", hit.confidence)
                    )
                }
            }
        )
    }
    fusion?.let { profile ->
        root.put(
            "fusion",
            JSONObject()
                .put("confidence", profile.confidence)
                .put("verdict", profile.verdict)
                .put("resultCount", profile.resultCount)
                .put("providerCount", profile.providerCount)
                .put("notes", JSONArray(profile.notes))
        )
    }
    return root.toString(2)
}

private suspend fun persistIdentifierSearch(
    store: LocalStore,
    query: String,
    kind: IdentifierKind,
    report: com.cyberphantom.osintworkbench.domain.PublicWebSearchResponse,
    fusion: FusionProfile?,
    hits: List<UsernameHit>
) = withContext(Dispatchers.IO) {
    val started = System.currentTimeMillis()
    val root = store.upsertEntity(query, "IDENTIFIER_${kind.name}", "User supplied", 90)
    report.results.take(60).forEach { item ->
        val node = store.upsertEntity(item.url, "PUBLIC_WEB_RESULT@$query", item.provider, item.confidence)
        store.addEdge(root, node, "LISTED_IN_PUBLIC_WEB_INDEX", item.provider, item.confidence)
    }
    report.results.take(8).forEach { item ->
        store.addFinding(root, item.title.ifBlank { "نتيجة عامة" }, "${item.description}\n${item.url}", item.provider, "info")
    }
    fusion?.alerts?.take(20)?.forEach { alert ->
        store.addFinding(
            root,
            "مؤشر: ${alert.title}",
            "${alert.keyword}\n${alert.url}",
            alert.provider,
            if (alert.severity == "high") "critical" else "warning"
        )
    }
    hits.filter { it.found }.forEach { hit ->
        val node = store.upsertEntity(hit.url, "ACCOUNT_CANDIDATE@$query", hit.platform, hit.confidence)
        store.addEdge(root, node, "USERNAME_PRESENT_ON", hit.platform, hit.confidence)
    }
    store.addScan(
        target = query,
        status = if (report.results.isEmpty()) "no_results" else "completed",
        startedAt = started,
        summary = "${report.results.size} نتيجة · ${report.sourceCount} مصدر · ${hits.count { it.found }} حساب مرجح",
        records = report.results.size,
        payloadJson = identifierJson(query, kind, report, fusion, hits)
    )
}

private suspend fun persistNews(store: LocalStore, result: NewsSearchResult) = withContext(Dispatchers.IO) {
    val root = store.upsertEntity(result.query, "NEWS_QUERY", "Multi-source news", 70)
    result.articles.take(40).forEach { article ->
        val node = store.upsertEntity(article.url, "NEWS_ARTICLE@${result.query}", article.provider, 60)
        store.addEdge(root, node, "REPORTED_BY", article.provider, 60)
    }
    result.articles.take(10).forEach { article ->
        store.addFinding(
            root,
            article.title,
            "${article.publisherName.ifBlank { article.domain }} · ${article.publishedAt}\n${article.url}",
            article.provider,
            "info"
        )
    }
    store.addScan(
        target = result.query,
        status = if (result.articles.isEmpty()) "no_results" else "completed",
        startedAt = System.currentTimeMillis(),
        summary = "${result.articles.size} مقالة · ${result.articles.map { it.provider }.distinct().size} مزود",
        records = result.articles.size,
        payloadJson = newsJson(result)
    )
}

@Composable
private fun SearchProgressPanel(
    message: String,
    progress: Float,
    elapsedMs: Long,
    accent: androidx.compose.ui.graphics.Color,
    running: Boolean
) {
    IntelPanel(accent = accent, contentPadding = 11) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MicroLabel(if (running) "LIVE SEARCH / RUNNING" else "SEARCH / COMPLETE", accent)
            Spacer(Modifier.weight(1f))
            MicroLabel(formatElapsed(elapsedMs), IntelColors.Text)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(20) { index ->
                val filled = (index + 1) / 20f <= progress.coerceIn(0f, 1f)
                Spacer(
                    Modifier.weight(1f).height(6.dp)
                        .background(
                            if (filled) accent else IntelColors.Hairline,
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("STAGE TRACKER", IntelColors.TextFaint)
            Spacer(Modifier.weight(1f))
            MicroLabel("\${(progress.coerceIn(0f, 1f) * 100).toInt()}%", accent)
        }
        Text(message, color = IntelColors.TextDim, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

private fun progressFor(message: String): Float = when {
    message.contains("اكتمل") -> 1f
    message.contains("فلترة") || message.contains("ترتيب") || message.contains("تحليل") -> 0.92f
    message.contains("الناشرين") || message.contains("الخلاصات") -> 0.72f
    message.contains("المعرفة") || message.contains("المجتمعات") -> 0.52f
    message.contains("توسيع") || message.contains("مسارات") -> 0.26f
    message.contains("تهيئة") -> 0.06f
    else -> 0.38f
}

private fun formatPublishedDate(raw: String): String {
    val value = raw.trim()
    val parsed = runCatching { java.time.Instant.parse(value) }
        .recoverCatching { java.time.OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
        .recoverCatching {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX", Locale.US)
            formatter.parse(value, java.time.Instant::from)
        }
        .getOrNull()
    return parsed?.let {
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
            .withZone(java.time.ZoneId.systemDefault()).format(it)
    } ?: value.ifBlank { "تاريخ النشر غير متاح" }
}

private fun formatElapsed(milliseconds: Long): String {
    val value = milliseconds.coerceAtLeast(0L)
    val seconds = value / 1000L
    val minutes = seconds / 60L
    val remainder = seconds % 60L
    val millis = (value % 1000L) / 100L
    return "%02d:%02d.%d".format(Locale.US, minutes, remainder, millis)
}

private fun formatNewsDateTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

private fun showDateTimePicker(
    context: android.content.Context,
    initialMillis: Long,
    onSelected: (Long) -> Unit
) {
    val current = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val chosen = Calendar.getInstance().apply {
                timeInMillis = initialMillis
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    chosen.set(Calendar.HOUR_OF_DAY, hour)
                    chosen.set(Calendar.MINUTE, minute)
                    chosen.set(Calendar.SECOND, 0)
                    chosen.set(Calendar.MILLISECOND, 0)
                    onSelected(chosen.timeInMillis)
                },
                current.get(Calendar.HOUR_OF_DAY),
                current.get(Calendar.MINUTE),
                true
            ).show()
        },
        current.get(Calendar.YEAR),
        current.get(Calendar.MONTH),
        current.get(Calendar.DAY_OF_MONTH)
    ).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}
