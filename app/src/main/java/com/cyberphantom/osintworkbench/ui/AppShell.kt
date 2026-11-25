package com.cyberphantom.osintworkbench.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.CaseRow
import com.cyberphantom.osintworkbench.data.EntityRow
import com.cyberphantom.osintworkbench.data.FindingRow
import com.cyberphantom.osintworkbench.data.LocalStore
import com.cyberphantom.osintworkbench.data.RelationRow
import com.cyberphantom.osintworkbench.data.StoreStats
import com.cyberphantom.osintworkbench.ui.design.Hairline
import com.cyberphantom.osintworkbench.ui.design.GhostButton
import com.cyberphantom.osintworkbench.ui.design.IntelColors
import com.cyberphantom.osintworkbench.ui.design.MicroLabel
import com.cyberphantom.osintworkbench.ui.design.NoteBlock
import com.cyberphantom.osintworkbench.ui.design.NeonAtmosphereBackground
import com.cyberphantom.osintworkbench.ui.design.WorkbenchThemeState
import com.cyberphantom.osintworkbench.ui.design.WorkbenchVisualTheme
import com.cyberphantom.osintworkbench.ui.screens.CasesScreen
import com.cyberphantom.osintworkbench.ui.screens.HomeScreen
import com.cyberphantom.osintworkbench.ui.screens.NetworkScreen
import com.cyberphantom.osintworkbench.ui.screens.ReconScreen
import com.cyberphantom.osintworkbench.ui.screens.ResearchScreen
import com.cyberphantom.osintworkbench.ui.screens.SettingsScreen
import com.cyberphantom.osintworkbench.ui.telegram.PrivateSessionHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class WorkbenchTab(val label: String, val code: String, val icon: ImageVector) {
    HOME("الرئيسية", "OPS", Icons.Filled.Dashboard),
    RECON("الاستطلاع", "RECON", Icons.Filled.Dns),
    NETWORK("الشبكة", "GRAPH", Icons.Filled.AccountTree),
    CASES("الحالات", "CASE", Icons.Filled.FolderOpen),
    RESEARCH("البحث", "INTEL", Icons.Filled.TravelExplore),
    SETTINGS("الإعدادات", "SYS", Icons.Filled.Settings)
}

@Composable
fun WorkbenchApp() {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(WorkbenchTab.HOME) }
    var tabBackStack by remember { mutableStateOf(emptyList<WorkbenchTab>()) }
    var researchScreenVisited by rememberSaveable { mutableStateOf(false) }
    var reconSeed by rememberSaveable { mutableStateOf("") }
    var researchSeed by rememberSaveable { mutableStateOf("") }
    var researchNews by rememberSaveable { mutableStateOf(false) }

    var stats by remember { mutableStateOf(StoreStats()) }
    var findings by remember { mutableStateOf(emptyList<FindingRow>()) }
    var entities by remember { mutableStateOf(emptyList<EntityRow>()) }
    var relations by remember { mutableStateOf(emptyList<RelationRow>()) }
    var cases by remember { mutableStateOf(emptyList<CaseRow>()) }
    var dataVersion by remember { mutableStateOf(0) }

    fun refresh() {
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) { loadSnapshot(store) }
            stats = snapshot.stats
            findings = snapshot.findings
            entities = snapshot.entities
            relations = snapshot.relations
            cases = snapshot.cases
            dataVersion += 1
        }
    }

    LaunchedEffect(Unit) { refresh() }
    val sessionVisible by PrivateSessionHost.sessionVisible

    fun navigateTo(destination: WorkbenchTab) {
        if (destination != tab) {
            tabBackStack = (tabBackStack + tab).takeLast(24)
            tab = destination
        }
        if (destination == WorkbenchTab.RESEARCH) researchScreenVisited = true
    }

    fun navigateBack() {
        if (sessionVisible) {
            PrivateSessionHost.hideSession()
            return
        }
        if (tabBackStack.isNotEmpty()) {
            tab = tabBackStack.last()
            tabBackStack = tabBackStack.dropLast(1)
        } else if (tab != WorkbenchTab.HOME) {
            tab = WorkbenchTab.HOME
        }
        // At HOME, consume Back rather than finishing the Activity.
    }

    BackHandler(enabled = true) { navigateBack() }

    Box(Modifier.fillMaxSize().background(IntelColors.Base)) {
        // One permanent WebView host. The exact SessionWebView instance remains attached
        // while the opaque workbench surface is shown above it.
        AndroidView(
            modifier = Modifier.fillMaxSize().safeDrawingPadding()
                    .padding(horizontal = if (sessionVisible) 4.dp else 0.dp)
                    .padding(top = if (sessionVisible) 84.dp else 0.dp),
            factory = { context ->
                FrameLayout(context).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    PrivateSessionHost.mountRoot(this)
                }
            },
            update = { container -> PrivateSessionHost.mountRoot(container) }
        )

        if (!sessionVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(IntelColors.Base)
                    .clickable { }
            )
            if (WorkbenchThemeState.theme == WorkbenchVisualTheme.NEON && WorkbenchThemeState.motionEnabled) {
                NeonAtmosphereBackground(Modifier.fillMaxSize())
            }
        }

        // Keep the existing app screens composed so their form/search state survives
        // while Telegram is temporarily shown above the same persistent WebView.
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .offset(x = if (sessionVisible) 1200.dp else 0.dp)
                .zIndex(2f)
        ) {
            OpsHeader(activeTab = tab, onOpenSettings = { navigateTo(WorkbenchTab.SETTINGS) })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    WorkbenchTab.HOME -> HomeScreen(
                        stats = stats,
                        findings = findings,
                        cases = cases,
                        onRecon = { target ->
                            reconSeed = target
                            navigateTo(WorkbenchTab.RECON)
                        },
                        onResearch = { query, news ->
                            researchSeed = query
                            researchNews = news
                            navigateTo(WorkbenchTab.RESEARCH)
                        },
                        onOpenTab = { navigateTo(it) }
                    )
                    WorkbenchTab.RECON -> ReconScreen(
                        store = store,
                        initialTarget = reconSeed,
                        onSaved = { refresh() }
                    )
                    WorkbenchTab.NETWORK -> NetworkScreen(entities = entities, relations = relations)
                    WorkbenchTab.CASES -> CasesScreen(
                        cases = cases,
                        store = store,
                        onSaved = { refresh() }
                    )
                    WorkbenchTab.RESEARCH -> Unit
                    WorkbenchTab.SETTINGS -> SettingsScreen(
                        stats = stats,
                        store = store,
                        dataVersion = dataVersion,
                        onDataChanged = { refresh() }
                    )
                }

                // Retain the research subtree after the first visit so navigation
                // does not dispose active searches or their progress state.
                if (tab == WorkbenchTab.RESEARCH || researchScreenVisited) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .offset(x = if (tab == WorkbenchTab.RESEARCH) 0.dp else 1200.dp)
                            .zIndex(if (tab == WorkbenchTab.RESEARCH) 3f else 0.5f)
                    ) {
                        ResearchScreen(
                            store = store,
                            initialQuery = researchSeed,
                            startInNews = researchNews,
                            onSaved = { refresh() },
                            onOpenSettings = { navigateTo(WorkbenchTab.SETTINGS) },
                            isCurrentTab = tab == WorkbenchTab.RESEARCH && !sessionVisible
                        )
                    }
                }
            }
            BottomRail(selected = tab, onSelect = {
                WorkbenchThemeState.tap(null)
                navigateTo(it)
            })
        }

        if (sessionVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .border(1.dp, IntelColors.HairlineStrong, RoundedCornerShape(4.dp))
                    .zIndex(4.5f)
            )
            TelegramSessionFrame(Modifier.align(Alignment.TopCenter).zIndex(5f))
        }
    }
}

@Composable
private fun TelegramSessionFrame(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        color = IntelColors.Base,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, IntelColors.HairlineStrong)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("نافذة جلسة المصدر", color = IntelColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    MicroLabel("SESSION VIEW · FULL HEIGHT", IntelColors.Signal)
                }
                GhostButton(
                    text = "إغلاق",
                    onClick = { PrivateSessionHost.hideSession() },
                    accent = IntelColors.TextDim
                )
            }
            NoteBlock(
                "استخدم التمرير داخل Telegram نفسه. تبقى الجلسة الحالية مفتوحة كما هي ويستخدم البحث الآلي WebView نفسها.",
                IntelColors.Signal,
                "TELEGRAM SESSION"
            )
        }
    }
}

private data class WorkbenchSnapshot(
    val stats: StoreStats,
    val findings: List<FindingRow>,
    val entities: List<EntityRow>,
    val relations: List<RelationRow>,
    val cases: List<CaseRow>
)

private fun loadSnapshot(store: LocalStore): WorkbenchSnapshot = WorkbenchSnapshot(
    stats = store.stats(),
    findings = store.recentFindings(6),
    entities = store.entities(600),
    relations = store.relations(600),
    cases = store.cases(200)
)

@Composable
private fun OpsHeader(activeTab: WorkbenchTab, onOpenSettings: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val utcFormatter = remember {
        SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    val utc = runCatching { utcFormatter.format(Date(now)) }.getOrDefault("--:--:--")

    Column(
        Modifier.fillMaxWidth().background(
            if (WorkbenchThemeState.theme == WorkbenchVisualTheme.NEON) IntelColors.Void.copy(alpha = 0.88f)
            else IntelColors.Void
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(IntelColors.PanelHigh, RoundedCornerShape(2.dp))
                    .border(1.dp, IntelColors.HairlineStrong, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.width(12.dp).height(1.dp).background(IntelColors.Signal))
                    Box(Modifier.width(8.dp).height(1.dp).background(IntelColors.Signal.copy(alpha = 0.6f)))
                    Box(Modifier.width(12.dp).height(1.dp).background(IntelColors.Signal.copy(alpha = 0.3f)))
                }
            }
            Spacer(Modifier.width(9.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "OSINT WORKBENCH",
                    color = IntelColors.Text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    fontFamily = FontFamily.Monospace
                )
                MicroLabel("NODE-01 · LOCAL STORAGE · ${activeTab.code}")
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    utc,
                    color = IntelColors.Signal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    modifier = Modifier.clickable(onClick = onOpenSettings),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MicroLabel("UTC", IntelColors.TextFaint)
                }
            }
        }
        Hairline()
    }
}

@Composable
private fun BottomRail(selected: WorkbenchTab, onSelect: (WorkbenchTab) -> Unit) {
    Surface(
        color = if (WorkbenchThemeState.theme == WorkbenchVisualTheme.NEON) IntelColors.Void.copy(alpha = 0.90f)
        else IntelColors.Void
    ) {
        Column {
            Hairline()
            Row(Modifier.fillMaxWidth().height(56.dp)) {
                WorkbenchTab.entries.forEach { tab ->
                    val active = tab == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { onSelect(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (active) IntelColors.Signal else Color.Transparent)
                        )
                        Spacer(Modifier.height(8.dp))
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (active) IntelColors.Signal else IntelColors.TextFaint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tab.label,
                            color = if (active) IntelColors.Text else IntelColors.TextFaint,
                            fontSize = 9.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
