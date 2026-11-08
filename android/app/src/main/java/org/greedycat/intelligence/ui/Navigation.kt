package org.greedycat.intelligence.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import org.greedycat.intelligence.ResearchApplication
import org.greedycat.intelligence.data.StoredRound

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchNavigation(container: ResearchApplication) {
    val model: ResearchViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ResearchViewModel(container.realRepository, container.demoRepository) as T
    })
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "overview"
    val demoRoute = route.startsWith("demo")
    val compact by model.compactSpacing.collectAsStateWithLifecycle()
    val inset = if (compact) 14.dp else 20.dp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(if (demoRoute) "Demo workspace" else "Greedy Cat", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Text(if (demoRoute) "DEMO · SYNTHETIC" else "INTELLIGENCE / LOCAL WORKSPACE", fontSize = 9.sp,
                        color = WorkspaceAmber, letterSpacing = 1.3.sp)
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        bottomBar = {
            if (!demoRoute) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf("overview" to "Overview", "history" to "History", "settings" to "Settings").forEach { (destination, label) ->
                    NavigationBarItem(selected = route == destination,
                        onClick = { nav.navigate(destination) { popUpTo("overview") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(when (destination) { "overview" -> Icons.Default.Home; "history" -> Icons.AutoMirrored.Filled.List; else -> Icons.Default.Settings }, contentDescription = null) },
                        label = { Text(label) })
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "overview", modifier = Modifier.padding(padding)) {
            composable("overview") {
                val rounds by model.realRounds.collectAsStateWithLifecycle()
                ScreenColumn(inset) {
                    Eyebrow("RESEARCH FOUNDATION")
                    Text("Evidence comes first.", style = MaterialTheme.typography.headlineMedium)
                    Text("An offline home for careful game research.", color = WorkspaceMuted)
                    ResearchCard {
                        Text("REAL WORKSPACE", color = WorkspaceAmber, fontSize = 10.sp, letterSpacing = 1.sp)
                        Text("Your evidence, on this device", style = MaterialTheme.typography.titleLarge)
                        Text("Import a reference and review its result. Drafts and human-confirmed results are kept separately.", color = WorkspaceMuted)
                        FilledTonalButton(onClick = { nav.navigate("history") }) { Text("View history") }
                        OutlinedButton(onClick = { nav.navigate("research") }) { Text("Open research") }
                    }
                    ResearchCard {
                        StatusLine("Local storage", when (rounds) { RoundLoad.Loading -> "Opening…"; RoundLoad.Failed -> "Unavailable"; else -> "Ready" })
                        StatusLine("Recent real drafts", (rounds as? RoundLoad.Ready)?.rows?.size?.toString() ?: "Unknown")
                        StatusLine("Game observation", "Not connected")
                        StatusLine("Active model", "Unavailable")
                        StatusLine("Desktop sync", "Not available yet")
                    }
                    ResearchCard {
                        Text("No proven predictive edge", color = WorkspaceAmber, fontWeight = FontWeight.SemiBold)
                        Text("More computation does not guarantee better predictions. Performance must be tested on verified, unseen rounds.", color = WorkspaceMuted)
                    }
                }
            }
            composable("history") {
                val library = referenceModel(container.realReviews, "realReferences")
                ReferenceLibrary(library) { evidence, round -> nav.navigate("review/$evidence/$round") }
            }
            composable("research") {
                AnalysisScreen(analysisModel(container.realPredictions, "realAnalysis")) { nav.popBackStack() }
            }
            composable("demoResearch") {
                AnalysisScreen(analysisModel(container.demoPredictions, "demoAnalysis")) { nav.popBackStack() }
            }
            composable("review/{evidenceId}/{roundId}") { backStack ->
                ManualReviewScreen(container.realReviews, requireNotNull(backStack.arguments?.getString("evidenceId")),
                    requireNotNull(backStack.arguments?.getString("roundId"))) { nav.popBackStack() }
            }
            composable("demoReferences") {
                Column {
                    TextButton(onClick = { nav.popBackStack() }) { Text("Back to demo workspace") }
                    val library = referenceModel(container.demoReviews, "demoReferences")
                    ReferenceLibrary(library) { evidence, round -> nav.navigate("demoReview/$evidence/$round") }
                }
            }
            composable("demoReview/{evidenceId}/{roundId}") { backStack ->
                ManualReviewScreen(container.demoReviews, requireNotNull(backStack.arguments?.getString("evidenceId")),
                    requireNotNull(backStack.arguments?.getString("roundId"))) { nav.popBackStack() }
            }
            composable("settings") {
                val preferenceError by model.preferenceError.collectAsStateWithLifecycle()
                ScreenColumn(inset) {
                    Eyebrow("ON THIS DEVICE")
                    Text("Workspace settings", style = MaterialTheme.typography.headlineMedium)
                    ResearchCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Compact spacing", fontWeight = FontWeight.Medium)
                                Text("Use less space between cards.", fontSize = 12.sp, color = WorkspaceMuted)
                            }
                            Switch(checked = compact, onCheckedChange = model::setCompactSpacing,
                                modifier = Modifier.testTag("compactSwitch").semantics { contentDescription = "Compact spacing" })
                        }
                        preferenceError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                    ResearchCard {
                        Text("Explore with sample data", style = MaterialTheme.typography.titleMedium)
                        Text("The demo uses a separate local database. Its synthetic drafts never appear in real history.", color = WorkspaceMuted)
                        OutlinedButton(onClick = { nav.navigate("demo") }) { Text("Open demo workspace") }
                    }
                    ResearchCard {
                        Text("Your controls", style = MaterialTheme.typography.titleMedium)
                        Text("This foundation requests no network, screen capture or storage-sharing permissions. It does not navigate another app or place wagers.", color = WorkspaceMuted)
                        StatusLine("Screen capture", "Not available yet")
                        StatusLine("Phone pairing", "Not available yet")
                    }
                }
            }
            composable("demo") {
                val rounds by model.demoRounds.collectAsStateWithLifecycle()
                val busy by model.busy.collectAsStateWithLifecycle()
                val notice by model.notice.collectAsStateWithLifecycle()
                ScreenColumn(inset) {
                    TextButton(onClick = { nav.popBackStack() }) { Text("Back to real workspace") }
                    ResearchCard {
                        Text("Synthetic data only", color = WorkspaceAmber, style = MaterialTheme.typography.titleLarge)
                        Text("Save a fixed sample draft to test local persistence. It contains no result and does not represent a real game round.", color = WorkspaceMuted)
                        Button(onClick = model::addSample, enabled = !busy) { Text("Add sample draft") }
                        OutlinedButton(onClick = model::clearSamples, enabled = !busy && (rounds as? RoundLoad.Ready)?.rows?.isNotEmpty() == true) { Text("Clear demo records") }
                        notice?.let { Text(it, fontSize = 12.sp, color = WorkspaceAmber) }
                    }
                    OutlinedButton(onClick = { nav.navigate("demoReferences") }) { Text("Review demo references") }
                    OutlinedButton(onClick = { nav.navigate("demoResearch") }) { Text("Open demo research") }
                    Text("Clearing demo rounds retains immutable checkpoint snapshots.", color = WorkspaceMuted)
                    RoundRows(rounds, demo = true)
                }
            }
        }
    }
}

@Composable
private fun analysisModel(repository: org.greedycat.intelligence.data.PredictionRepository, key: String): AnalysisViewModel =
    viewModel(key = key, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AnalysisViewModel(repository) as T
    })

@Composable
private fun referenceModel(repository: org.greedycat.intelligence.data.ReviewRepository, key: String): ReferenceViewModel =
    viewModel(key = key, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReferenceViewModel(repository) as T
    })

@Composable
private fun ScreenColumn(spacing: androidx.compose.ui.unit.Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

@Composable
private fun Eyebrow(text: String) { Text(text, color = WorkspaceMuted, fontSize = 10.sp, letterSpacing = 1.5.sp) }

@Composable
private fun ResearchCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = WorkspaceMuted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 12.sp)
    }
}

@Composable
private fun RoundRows(state: RoundLoad, demo: Boolean) {
    when (state) {
        RoundLoad.Loading -> ResearchCard { CircularProgressIndicator(Modifier.size(24.dp)); Text("Opening local history…") }
        RoundLoad.Failed -> ResearchCard { Text("Local history could not be opened.", color = MaterialTheme.colorScheme.error); Text("Records were not replaced. Restart the app to retry.", color = WorkspaceMuted) }
        is RoundLoad.Ready -> if (state.rows.isEmpty()) ResearchCard {
            Text(if (demo) "No demo drafts yet" else "No real rounds yet", style = MaterialTheme.typography.titleMedium)
            Text(if (demo) "Add the sample above to test persistence." else "Verified reference import is not available in this foundation. Your history starts empty.", color = WorkspaceMuted)
        } else state.rows.forEach { stored -> RoundCard(stored, demo) }
    }
}

@Composable
private fun RoundCard(stored: StoredRound, demo: Boolean) {
    ResearchCard {
        Text(if (demo) "SYNTHETIC DRAFT" else "UNVERIFIED DRAFT", color = WorkspaceAmber, fontSize = 10.sp)
        Text("Round ${stored.round.roundNumber ?: "unknown"}", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(if (demo) "demoRow" else "realRow"))
        StatusLine("Mode", stored.round.mode.name.lowercase().replaceFirstChar { it.uppercase() })
        StatusLine("Outcome", "Unknown")
        Text("Missing: " + stored.missing.joinToString { it.field.name.lowercase().replace('_', ' ') }, fontSize = 11.sp, color = WorkspaceMuted)
        Text(stored.round.startedAt ?: "Start time unknown", fontSize = 10.sp, color = WorkspaceMuted)
    }
}
