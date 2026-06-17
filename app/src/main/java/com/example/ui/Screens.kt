package com.example.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.round
import kotlin.math.abs
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.InvoiceLine
import com.example.data.Party
import java.text.SimpleDateFormat
import java.util.*

// Sealed class representing screens in manual type-safe routing
sealed class Screen {
    object Home : Screen()
    object PartyList : Screen()
    object UdhaarSummary : Screen()
    data class CreateEditParty(val partyId: String? = null) : Screen()
    data class PartyDetail(val partyId: String) : Screen()
    data class CreateInvoice(val partyId: String) : Screen()
    data class PartyLedger(val partyId: String) : Screen()
}

@Composable
fun AppNavigation(viewModel: LedgerViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val context = LocalContext.current

    val partiesWithBalances by viewModel.partiesWithBalancesFlow.collectAsState(initial = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsState()
    val allInvoices by viewModel.allInvoices.collectAsState()
    val allLines by viewModel.allInvoiceLines.collectAsState()

    val filteredParties = remember(partiesWithBalances, searchQuery) {
        if (searchQuery.isBlank()) {
            partiesWithBalances
        } else {
            partiesWithBalances.filter {
                it.party.partyName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
        when (screen) {
            is Screen.Home -> {
                HomeScreen(
                    viewModel = viewModel,
                    parties = partiesWithBalances,
                    onNavigateToParties = { currentScreen = Screen.PartyList },
                    onNavigateToUdhaar = { currentScreen = Screen.UdhaarSummary },
                    onNavigateToCreateParty = { currentScreen = Screen.CreateEditParty() }
                )
            }
            is Screen.PartyList -> {
                PartyListScreen(
                    parties = filteredParties,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onNavigateBack = { currentScreen = Screen.Home },
                    onPartyClick = { id -> currentScreen = Screen.PartyDetail(id) },
                    onNavigateToCreateParty = { currentScreen = Screen.CreateEditParty() }
                )
            }
            is Screen.UdhaarSummary -> {
                UdhaarScreen(
                    parties = partiesWithBalances,
                    onNavigateBack = { currentScreen = Screen.Home }
                )
            }
            is Screen.CreateEditParty -> {
                CreateEditPartyScreen(
                    partyId = screen.partyId,
                    viewModel = viewModel,
                    onNavigateBack = {
                        currentScreen = if (screen.partyId != null) Screen.PartyDetail(screen.partyId) else Screen.PartyList
                    }
                )
            }
            is Screen.PartyDetail -> {
                val partyWithBal = partiesWithBalances.find { it.party.partyId == screen.partyId }
                if (partyWithBal != null) {
                    PartyDetailScreen(
                        partyWithBalance = partyWithBal,
                        onNavigateBack = { currentScreen = Screen.PartyList },
                        onNavigateToCreateInvoice = { currentScreen = Screen.CreateInvoice(screen.partyId) },
                        onNavigateToLedger = { currentScreen = Screen.PartyLedger(screen.partyId) },
                        onNavigateToEditParty = { currentScreen = Screen.CreateEditParty(screen.partyId) }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Party not found", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            is Screen.CreateInvoice -> {
                val partyWithBal = partiesWithBalances.find { it.party.partyId == screen.partyId }
                if (partyWithBal != null) {
                    CreateInvoiceScreen(
                        party = partyWithBal.party,
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = Screen.PartyDetail(screen.partyId) }
                    )
                }
            }
            is Screen.PartyLedger -> {
                val partyWithBal = partiesWithBalances.find { it.party.partyId == screen.partyId }
                if (partyWithBal != null) {
                    val partyLines = allLines.filter { it.partyId == screen.partyId }
                    val ledgerEntries = remember(partyWithBal, partyLines, allInvoices) {
                        viewModel.getLedgerForParty(partyWithBal.party, partyLines, allInvoices)
                    }
                    PartyLedgerScreen(
                        party = partyWithBal.party,
                        ledgerEntries = ledgerEntries,
                        onNavigateBack = { currentScreen = Screen.PartyDetail(screen.partyId) }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// A. HOME SCREEN
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: LedgerViewModel,
    parties: List<PartyWithBalance>,
    onNavigateToParties: () -> Unit,
    onNavigateToUdhaar: () -> Unit,
    onNavigateToCreateParty: () -> Unit
) {
    var showConfigDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Calculations of grand totals
    val grandTotalGold = remember(parties) { parties.sumOf { it.goldBalance } }
    val grandTotalSilver = remember(parties) { parties.sumOf { it.silverBalance } }
    val grandTotalCash = remember(parties) { parties.sumOf { it.cashBalance } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Gold & Silver Udhaar",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier.testTag("configs_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Database Config",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Title Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "Jewellery Udhaar Ledger",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Track party-wise Gold Fine, Silver Fine, and Cash balances securely.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Quick Status of Firestore Connectivity
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (viewModel.repository.isFirestoreSyncEnabled())
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (viewModel.repository.isFirestoreSyncEnabled()) Icons.Default.Refresh else Icons.Default.Warning,
                            contentDescription = "Cloud status",
                            tint = if (viewModel.repository.isFirestoreSyncEnabled()) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (viewModel.repository.isFirestoreSyncEnabled()) "Cloud Sync Active" else "Local / Offline mode",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (viewModel.repository.isFirestoreSyncEnabled()) "Writing collections to Firestore" else "Tap config icon atop to connect Cloud Firestore.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Quick Dashboard Numbers
            item {
                Text(
                    text = "Shop Udhaar Totals",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardMetricRow(
                        label = "Grand Total Gold Fine",
                        value = "${viewModel.roundTo3Decimals(grandTotalGold)} g",
                        color = Color(0xFFE5A93C) // Gold Accent
                    )
                    DashboardMetricRow(
                        label = "Grand Total Silver Fine",
                        value = "${viewModel.roundTo3Decimals(grandTotalSilver)} g",
                        color = Color(0xFF90A4AE) // Silver Accent
                    )
                    DashboardMetricRow(
                        label = "Grand Total Cash",
                        value = "₹${formatAmount(grandTotalCash)}",
                        color = Color(0xFF2E7D32) // Cash Accent Green
                    )
                }
            }

            // Main Actions Cards Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ActionCard(
                            title = "Parties",
                            subtitle = "${parties.size} Registered",
                            icon = Icons.Default.Person,
                            onClick = onNavigateToParties,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("parties_nav_card")
                        )
                        ActionCard(
                            title = "Pending Udhaar",
                            subtitle = "Summary List",
                            icon = Icons.AutoMirrored.Default.List,
                            onClick = onNavigateToUdhaar,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("udhaar_nav_card")
                        )
                    }
                    Button(
                        onClick = onNavigateToCreateParty,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("create_party_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add New Party Master", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        FirebaseConfigDialog(
            viewModel = viewModel,
            onDismiss = { showConfigDialog = false }
        )
    }
}

@Composable
fun DashboardMetricRow(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = color.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// B. PARTY LIST SCREEN
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyListScreen(
    parties: List<PartyWithBalance>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onPartyClick: (String) -> Unit,
    onNavigateToCreateParty: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Party Master Directory", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreateParty,
                modifier = Modifier.testTag("fab_add_party"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Party")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("party_search_bar"),
                placeholder = { Text("Search party by name...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors()
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (parties.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.5f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No parties registered yet.",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                        Text(
                            text = "Tap plus floating button to register.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(parties) { partyAndBal ->
                        PartyRowItem(
                            partyAndBal = partyAndBal,
                            onClick = { onPartyClick(partyAndBal.party.partyId) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PartyRowItem(partyAndBal: PartyWithBalance, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("party_item_${partyAndBal.party.partyId}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = partyAndBal.party.partyName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!partyAndBal.party.phoneNumber.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = partyAndBal.party.phoneNumber,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))
            
            // Grid-like display of balances
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BalanceMinifiedChip("Gold Fine", "${partyAndBal.goldBalance} g", Color(0xFFE5A93C))
                BalanceMinifiedChip("Silver Fine", "${partyAndBal.silverBalance} g", Color(0xFF90A4AE))
                BalanceMinifiedChip("Cash / Charges", "₹${formatAmount(partyAndBal.cashBalance)}", Color(0xFF2E7D32))
            }
        }
    }
}

@Composable
fun BalanceMinifiedChip(label: String, value: String, accentColor: Color) {
    Column {
        Text(text = label, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = Color.Gray)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor)
    }
}

// -------------------------------------------------------------------------------------------------
// C. CREATE/EDIT PARTY SCREEN
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditPartyScreen(
    partyId: String?,
    viewModel: LedgerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var isEditing = partyId != null

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var openingGold by remember { mutableStateOf("") }
    var openingSilver by remember { mutableStateOf("") }
    var openingCash by remember { mutableStateOf("") }

    LaunchedEffect(partyId) {
        if (partyId != null) {
            val party = viewModel.repository.getPartyById(partyId)
            if (party != null) {
                name = party.partyName
                phone = party.phoneNumber ?: ""
                address = party.address ?: ""
                notes = party.notes ?: ""
                openingGold = party.openingGoldFineGrams.toString()
                openingSilver = party.openingSilverFineGrams.toString()
                openingCash = party.openingCashRupees.toString()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Party Master" else "New Party Registration", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Personal Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Party Name (Required)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_party_name"),
                    placeholder = { Text("Enter party's full name") },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_party_phone"),
                    placeholder = { Text("WhatsApp or Call number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Business or home address") },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Business Details") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    placeholder = { Text("Notes about this customer/party") },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Opening Balances",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Enter positive if they owe us, negative if we owe them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            item {
                OutlinedTextField(
                    value = openingGold,
                    onValueChange = { openingGold = it },
                    label = { Text("Opening Gold Fine (Grams)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_opening_gold"),
                    placeholder = { Text("0.000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = openingSilver,
                    onValueChange = { openingSilver = it },
                    label = { Text("Opening Silver Fine (Grams)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_opening_silver"),
                    placeholder = { Text("0.000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = openingCash,
                    onValueChange = { openingCash = it },
                    label = { Text("Opening Cash / Charges (Rupees)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_opening_cash"),
                    placeholder = { Text("₹0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (name.trim().isEmpty()) {
                            Toast.makeText(context, "Party Name is required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val og = openingGold.toDoubleOrNull() ?: 0.0
                        val os = openingSilver.toDoubleOrNull() ?: 0.0
                        val oc = openingCash.toDoubleOrNull() ?: 0.0

                        viewModel.saveParty(
                            partyId = partyId,
                            partyName = name,
                            phoneNumber = phone,
                            address = address,
                            notes = notes,
                            openingGold = og,
                            openingSilver = os,
                            openingCash = oc
                        ) {
                            Toast.makeText(
                                context,
                                if (isEditing) "Party master updated!" else "New party added!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("save_party_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Save")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Party Master", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// D. PARTY DETAIL SCREEN
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyDetailScreen(
    partyWithBalance: PartyWithBalance,
    onNavigateBack: () -> Unit,
    onNavigateToCreateInvoice: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToEditParty: () -> Unit
) {
    val party = partyWithBalance.party
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(party.partyName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToEditParty,
                        modifier = Modifier.testTag("edit_party_icon_button")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Party Details")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Party Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Contact Details",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!party.phoneNumber.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = party.phoneNumber, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (!party.address.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = party.address, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (!party.notes.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = party.notes, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (party.phoneNumber.isNullOrEmpty() && party.address.isNullOrEmpty() && party.notes.isNullOrEmpty()) {
                        Text("No contact notes saved for this customer.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Centralized large balanced displays
            Text(
                text = "Udhaar Balance Buckets",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleBalanceDisplayCard(
                    metalLabel = "Gold Fine",
                    amount = "${partyWithBalance.goldBalance} g",
                    valueMetric = partyWithBalance.goldBalance,
                    colorAccent = Color(0xFFE5A93C)
                )
                SingleBalanceDisplayCard(
                    metalLabel = "Silver Fine",
                    amount = "${partyWithBalance.silverBalance} g",
                    valueMetric = partyWithBalance.silverBalance,
                    colorAccent = Color(0xFF90A4AE)
                )
                SingleBalanceDisplayCard(
                    metalLabel = "Cash / Charges",
                    amount = "₹${formatAmount(partyWithBalance.cashBalance)}",
                    valueMetric = partyWithBalance.cashBalance,
                    colorAccent = Color(0xFF2E7D32)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Transaction buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToLedger,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("view_ledger_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Default.List, contentDescription = "Ledger")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Ledger", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onNavigateToCreateInvoice,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("create_invoice_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Invoice", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SingleBalanceDisplayCard(
    metalLabel: String,
    amount: String,
    valueMetric: Double,
    colorAccent: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = metalLabel, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(
                    text = amount,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorAccent
                )
            }

            // Owed metrics display badges (Green for party owes, red for we owe)
            if (valueMetric > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "Party Owes Us", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else if (valueMetric < 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFFEBEE))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "We Owe Party", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.LightGray.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "Settled", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// E. CREATE HYBRID INVOICE SCREEN
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceScreen(
    party: Party,
    viewModel: LedgerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var invoiceNotes by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }

    val tempLines by viewModel.tempInvoiceLines.collectAsState()

    // Line totals
    val goldChange = remember(tempLines) { tempLines.sumOf { it.goldFineGrams } }
    val silverChange = remember(tempLines) { tempLines.sumOf { it.silverFineGrams } }
    val cashChange = remember(tempLines) { tempLines.sumOf { it.cashChargesRupees } }

    var showAddLineDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.clearTempInvoiceLines()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Create Hybrid Invoice", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(text = "To: ${party.partyName}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Date and notes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = selectedDate
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val resCal = Calendar.getInstance()
                                resCal.set(year, month, dayOfMonth)
                                selectedDate = resCal.timeInMillis
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = formatDate(selectedDate))
                }

                OutlinedTextField(
                    value = invoiceNotes,
                    onValueChange = { invoiceNotes = it },
                    label = { Text("Invoice Notes (Optional)") },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Invoice Entries", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = { showAddLineDialog = true },
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("add_item_line_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Row")
                }
            }

            // Table list of added items
            if (tempLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Invoice is empty.", color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text("Add sale, payment, or adjustment rows.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(10.dp)
                ) {
                    items(tempLines) { line ->
                        TempInvoiceLineItemCard(line = line) {
                            viewModel.removeTempInvoiceLine(line.lineId)
                        }
                    }
                }
            }

            // Live Totals Segment
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Net Invoice Balances", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net Gold:", fontSize = 13.sp)
                        Text("${viewModel.roundTo3Decimals(goldChange)} g", fontWeight = FontWeight.Bold, color = Color(0xFFE5A93C))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net Silver:", fontSize = 13.sp)
                        Text("${viewModel.roundTo3Decimals(silverChange)} g", fontWeight = FontWeight.Bold, color = Color(0xFF90A4AE))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net Cash / Charges:", fontSize = 13.sp)
                        Text("₹${formatAmount(cashChange)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }
            }

            // Save Invoice Button
            Button(
                onClick = {
                    viewModel.saveInvoice(
                        partyId = party.partyId,
                        date = selectedDate,
                        notes = invoiceNotes,
                        onComplete = { invNo ->
                            Toast.makeText(context, "Saved invoice $invNo successfully!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                        onFailure = { errorString ->
                            Toast.makeText(context, errorString, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("submit_invoice_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Save")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Finalize & Save Invoice", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    if (showAddLineDialog) {
        AddInvoiceLineDialog(
            viewModel = viewModel,
            onDismiss = { showAddLineDialog = false },
            onAddLine = { line ->
                viewModel.addTempInvoiceLine(line)
                showAddLineDialog = false
            }
        )
    }
}

@Composable
fun TempInvoiceLineItemCard(line: InvoiceLine, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, Color.LightGray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (line.entryType == "Sale") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = line.entryType, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = line.metal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                var details = line.itemDescription
                if (line.isSettlement) {
                    details += " (Settle ${line.settlementMetal} @ ₹${line.settlementRateRupeesPerGram})"
                } else if (line.weightGrams > 0) {
                    details += " (${line.weightGrams}g @ ${line.tunch} tunch)"
                }
                Text(
                    text = details,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Display numeric change impact
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (line.goldFineGrams != 0.0) {
                        Text(text = "Gold: ${line.goldFineGrams} g", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE5A93C))
                    }
                    if (line.silverFineGrams != 0.0) {
                        Text(text = "Silver: ${line.silverFineGrams} g", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF90A4AE))
                    }
                    if (line.cashChargesRupees != 0.0) {
                        Text(text = "Cash: ₹${line.cashChargesRupees}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Line", tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// F. ADD/EDIT INVOICE LINE DIALOG / SCREEN
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInvoiceLineDialog(
    viewModel: LedgerViewModel,
    onDismiss: () -> Unit,
    onAddLine: (InvoiceLine) -> Unit
) {
    val context = LocalContext.current

    var entryType by remember { mutableStateOf("Sale") }
    val entryTypes = listOf("Sale", "Payment", "Opening Balance", "Adjustment")
    var entryTypeExpanded by remember { mutableStateOf(false) }

    var metal by remember { mutableStateOf("Gold") }
    val metals = listOf("Gold", "Silver", "Cash", "Other")
    var metalExpanded by remember { mutableStateOf(false) }

    var itemDescr by remember { mutableStateOf("") }
    var weightStr by remember { mutableStateOf("") }
    var purityStr by remember { mutableStateOf("") }
    var tunchStr by remember { mutableStateOf("") }
    var cashChargesStr by remember { mutableStateOf("") }

    // Settlement variables
    var isSettlement by remember { mutableStateOf(false) }
    var settlementMetal by remember { mutableStateOf("Gold") }
    val settlementMetals = listOf("Gold", "Silver")
    var settlementExpanded by remember { mutableStateOf(false) }
    var settleCashStr by remember { mutableStateOf("") }
    var settleRateStr by remember { mutableStateOf("") }

    var notes by remember { mutableStateOf("") }

    // Auto-computed lines values displays
    val computedFine = remember(weightStr, tunchStr) {
        val w = weightStr.toDoubleOrNull() ?: 0.0
        val t = tunchStr.toDoubleOrNull() ?: 0.0
        viewModel.roundTo3Decimals(w * t / 100.0)
    }

    val computedSettleFine = remember(settleCashStr, settleRateStr) {
        val cash = settleCashStr.toDoubleOrNull() ?: 0.0
        val rate = settleRateStr.toDoubleOrNull() ?: 0.0
        if (rate > 0) viewModel.roundTo3Decimals(cash / rate) else 0.0
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .testTag("add_item_dialog_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Add Ledger Input line",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Entry Type Dropdown
                    item {
                        ExposedDropdownField(
                            label = "Entry Type",
                            selected = entryType,
                            options = entryTypes,
                            expanded = entryTypeExpanded,
                            onExpandedChange = { entryTypeExpanded = it },
                            onSelect = {
                                entryType = it
                                entryTypeExpanded = false
                                // Auto check settlement if payment with gold/silver
                                if (entryType != "Payment") {
                                    isSettlement = false
                                }
                            }
                        )
                    }

                    // Metal Dropdown
                    item {
                        ExposedDropdownField(
                            label = "Metal / Asset Class",
                            selected = metal,
                            options = metals,
                            expanded = metalExpanded,
                            onExpandedChange = { metalExpanded = it },
                            onSelect = {
                                metal = it
                                metalExpanded = false
                            }
                        )
                    }

                    // Set Description
                    item {
                        OutlinedTextField(
                            value = itemDescr,
                            onValueChange = { itemDescr = it },
                            label = { Text("Item Description") },
                            placeholder = { Text("e.g. gold chain, cash payment, tops") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dial_descr"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // Render entry details if not Cash direct metal
                    if (metal != "Cash") {
                        // Standard physical sale details
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = weightStr,
                                    onValueChange = { weightStr = it },
                                    label = { Text("Gross Weight (g)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dial_weight"),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = tunchStr,
                                    onValueChange = { tunchStr = it },
                                    label = { Text("Tunch (0-100)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dial_tunch"),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = purityStr,
                                onValueChange = { purityStr = it },
                                label = { Text("Purity % (Optional)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        // Display Auto Calculated Fine
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "Auto-Calculated Fine: $computedFine grams",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Cash / charges input
                    item {
                        OutlinedTextField(
                            value = cashChargesStr,
                            onValueChange = { cashChargesStr = it },
                            label = { Text(if (metal == "Cash") "Cash Amount (Rupees)" else "Cash / Charges (Rupees)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dial_cash_charges"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // Cash-to-Metal Settlement payments
                    if (entryType == "Payment" && (metal == "Gold" || metal == "Silver")) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isSettlement,
                                    onCheckedChange = { isSettlement = it },
                                    modifier = Modifier.testTag("dial_settle_checkbox")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Convert Cash to Metal Settlement", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        if (isSettlement) {
                            item {
                                ExposedDropdownField(
                                    label = "Settlement Metal",
                                    selected = settlementMetal,
                                    options = settlementMetals,
                                    expanded = settlementExpanded,
                                    onExpandedChange = { settlementExpanded = it },
                                    onSelect = {
                                        settlementMetal = it
                                        settlementExpanded = false
                                    }
                                )
                            }

                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = settleCashStr,
                                        onValueChange = { settleCashStr = it },
                                        label = { Text("Cash Paid (₹)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("dial_settle_cash"),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = settleRateStr,
                                        onValueChange = { settleRateStr = it },
                                        label = { Text("Rate per g (₹)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("dial_settle_rate"),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "Subtracted $settlementMetal Fine: $computedSettleFine g",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // Adjustment constraint notes
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes / Adjustment Reason") },
                            placeholder = { Text("Reason (Mandatory if Adjustment)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dial_notes"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            // Validations
                            val tunchVal = tunchStr.toDoubleOrNull() ?: 0.0
                            val weightVal = weightStr.toDoubleOrNull() ?: 0.0
                            val cashChargesVal = cashChargesStr.toDoubleOrNull() ?: 0.0

                            // 1. Tunch bounds
                            if (metal != "Cash" && (tunchVal < 0.0 || tunchVal > 100.0)) {
                                Toast.makeText(context, "Tunch must be between 0 and 100", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            // 2. Positive weight
                            if (metal != "Cash" && weightVal < 0.0) {
                                Toast.makeText(context, "Weight cannot be negative", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            // 3. Settlement rate validate
                            val rateVal = settleRateStr.toDoubleOrNull() ?: 0.0
                            if (isSettlement && rateVal <= 0.0) {
                                Toast.makeText(context, "Settlement rate must be greater than zero", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            // 4. Adjustment notes mandatory
                            if (entryType == "Adjustment" && notes.trim().isEmpty()) {
                                Toast.makeText(context, "Adjustment notes/reason are required", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // Perform accurate calculations using helper in ViewModel
                            val triple = viewModel.calculateLineFines(
                                entryType = entryType,
                                metal = metal,
                                weightGrams = weightVal,
                                tunch = tunchVal,
                                cashChargesRupees = cashChargesVal,
                                isSettlement = isSettlement,
                                settlementMetal = settlementMetal,
                                cashAmountRupees = settleCashStr.toDoubleOrNull() ?: 0.0,
                                settlementRateRupeesPerGram = rateVal
                            )

                            // Formulate InvoiceLine object
                            val line = InvoiceLine(
                                lineId = UUID.randomUUID().toString(),
                                invoiceId = "", // set by creation parent
                                partyId = "", // set by parent
                                date = 0L, // set by parent
                                entryType = entryType,
                                metal = metal,
                                itemDescription = itemDescr.trim(),
                                weightGrams = weightVal,
                                purity = purityStr.toDoubleOrNull() ?: 0.0,
                                tunch = tunchVal,
                                goldFineGrams = triple.first,
                                silverFineGrams = triple.second,
                                cashChargesRupees = triple.third,
                                isSettlement = isSettlement,
                                settlementMetal = if (isSettlement) settlementMetal else "None",
                                cashAmountRupees = if (isSettlement) (settleCashStr.toDoubleOrNull() ?: 0.0) else 0.0,
                                settlementRateRupeesPerGram = if (isSettlement) rateVal else 0.0,
                                calculatedFineGrams = if (isSettlement) computedSettleFine else 0.0,
                                notes = notes.trim().ifEmpty { null },
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )

                            onAddLine(line)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("confirm_add_line_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Row")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownField(
    label: String,
    selected: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit
) {
    Column {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(2.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selected,
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(),
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option) },
                        onClick = { onSelect(option) }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// G. PARTY LEDGER SCREEN
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyLedgerScreen(
    party: Party,
    ledgerEntries: List<LedgerEntry>,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Party Running Ledger", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(text = party.partyName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // WhatsApp share button
                    IconButton(
                        onClick = {
                            val lastEntry = ledgerEntries.lastOrNull()
                            val g = lastEntry?.runningGold ?: party.openingGoldFineGrams
                            val s = lastEntry?.runningSilver ?: party.openingSilverFineGrams
                            val c = lastEntry?.runningCash ?: party.openingCashRupees

                            val text = """
                                Ledger Summary for ${party.partyName}
                                
                                Gold fine balance: ${g}g
                                Silver fine balance: ${s}g
                                Cash balance: ₹${formatAmount(c)}
                                
                                Positive balance means amount/metal due from you.
                            """.trimIndent()

                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Ledger Summary via"))
                        },
                        modifier = Modifier.testTag("share_summary_icon_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share Summary")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary banner cards
            val last = ledgerEntries.lastOrNull()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current Ledger Closings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Gold: ${last?.runningGold ?: 0.000} g | Silver: ${last?.runningSilver ?: 0.000} g", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        Text("Cash: ₹${formatAmount(last?.runningCash ?: 0.0)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                }
            }

            Text(text = "Chronological Book entries", fontWeight = FontWeight.Bold, fontSize = 15.sp)

            // Ledger Events List Representation (Accessible on portrait/mobile, beautiful!)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ledgerEntries) { entry ->
                    LedgerEntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
fun LedgerEntryCard(entry: LedgerEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = formatDate(entry.date), fontSize = 11.sp, color = Color.Gray)
                    Text(
                        text = if (entry.invoiceNumber == "-") "Opening Balance" else "Invoice: ${entry.invoiceNumber}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when (entry.entryType) {
                                "Sale" -> Color(0xFFE8F5E9)
                                "Payment" -> Color(0xFFFFEBEE)
                                "Adjustment" -> Color(0xFFFFF3E0)
                                else -> Color(0xFFECEFF1)
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = entry.entryType, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = entry.description, fontSize = 12.sp, color = Color.DarkGray)
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha = 0.3f))

            // Values row (Modifications & running subtotals)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LedgerFieldColumn("Gold", formatSigned(entry.goldChange, "g"), "${entry.runningGold} g", Color(0xFFE5A93C))
                LedgerFieldColumn("Silver", formatSigned(entry.silverChange, "g"), "${entry.runningSilver} g", Color(0xFF90A4AE))
                LedgerFieldColumn("Cash/Charges", formatSignedRupees(entry.cashChange), "₹${formatAmount(entry.runningCash)}", Color(0xFF2E7D32))
            }
        }
    }
}

@Composable
fun LedgerFieldColumn(metal: String, change: String, running: String, color: Color) {
    Column {
        Text(text = metal, fontSize = 10.sp, color = Color.Gray)
        Text(text = change, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (change.startsWith("-")) Color(0xFFC62828) else if (change.startsWith("+")) Color(0xFF2E7D32) else Color.DarkGray)
        Text(text = "Closing: $running", fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// -------------------------------------------------------------------------------------------------
// H. UDHAAR / PENDING SCREEN
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UdhaarScreen(
    parties: List<PartyWithBalance>,
    onNavigateBack: () -> Unit
) {
    // Filter out parties that have no active pending ledger balances
    val pendingParties = remember(parties) {
        parties.filter {
            it.goldBalance != 0.0 || it.silverBalance != 0.0 || it.cashBalance != 0.0
        }
    }

    // Grand calculations
    val grandGold = remember(parties) { parties.sumOf { it.goldBalance } }
    val grandSilver = remember(parties) { parties.sumOf { it.silverBalance } }
    val grandCash = remember(parties) { parties.sumOf { it.cashBalance } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending Udhaar Summary", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Grand summary card with double-column labels
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Shop Net Due Accumulations",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Gold Fine Due:", fontSize = 13.sp)
                        Text("${round(grandGold * 1000.0) / 1000.0} g", fontWeight = FontWeight.Bold, color = Color(0xFFE5A93C))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Silver Fine Due:", fontSize = 13.sp)
                        Text("${round(grandSilver * 1000.0) / 1000.0} g", fontWeight = FontWeight.Bold, color = Color(0xFF90A4AE))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Cash Outstandings:", fontSize = 13.sp)
                        Text("₹${formatAmount(grandCash)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }
            }

            Text(
                text = "${pendingParties.size} Parties with Pending Outstandings",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            if (pendingParties.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Face, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Amazing! No ledger dues pending.", color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text("All customer books are completely clear.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pendingParties) { partyAndBal ->
                        PendingUdhaarRowItem(partyAndBal = partyAndBal)
                    }
                }
            }
        }
    }
}

@Composable
fun PendingUdhaarRowItem(partyAndBal: PartyWithBalance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = partyAndBal.party.partyName,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniUdhaarMetricCol("Gold Fine", "${partyAndBal.goldBalance} g", partyAndBal.goldBalance, Color(0xFFE5A93C))
                MiniUdhaarMetricCol("Silver Fine", "${partyAndBal.silverBalance} g", partyAndBal.silverBalance, Color(0xFF90A4AE))
                MiniUdhaarMetricCol("Cash Balance", "₹${formatAmount(partyAndBal.cashBalance)}", partyAndBal.cashBalance, Color(0xFF2E7D32))
            }
        }
    }
}

@Composable
fun MiniUdhaarMetricCol(label: String, valStr: String, value: Double, accentColor: Color) {
    Column {
        Text(text = label, fontSize = 10.sp, color = Color.Gray)
        Text(
            text = valStr,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (value > 0) accentColor else if (value < 0) Color.Red else Color.LightGray
        )
        Text(
            text = if (value > 0) "Owes Us" else if (value < 0) "We Owe" else "Balanced",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (value > 0) Color(0xFF2E7D32) else if (value < 0) Color.Red else Color.Gray
        )
    }
}

// -------------------------------------------------------------------------------------------------
// DYNAMIC FLOT CONFIG FIREBASE DIALOG
// -------------------------------------------------------------------------------------------------
@Composable
fun FirebaseConfigDialog(
    viewModel: LedgerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var projectId by remember { mutableStateOf(viewModel.repository.getFirebaseProjectId() ?: "") }
    var appId by remember { mutableStateOf(viewModel.repository.getFirebaseAppId() ?: "") }
    var apiKey by remember { mutableStateOf(viewModel.repository.getFirebaseApiKey() ?: "") }
    var syncEnabled by remember { mutableStateOf(viewModel.repository.isFirestoreSyncEnabled()) }

    var isSyncing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("configs_dialog_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Cloud Firestore Setup",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Connecting Cloud Firestore enables full remote cross-device database synchronizations. Setup your Firebase and paste credentials below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = projectId,
                    onValueChange = { projectId = it },
                    label = { Text("Firebase Project ID") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("config_proj_id"),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = appId,
                    onValueChange = { appId = it },
                    label = { Text("Application ID (App ID)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = syncEnabled,
                        onCheckedChange = { syncEnabled = it },
                        modifier = Modifier.testTag("config_sync_switch")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto-Write to Cloud", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                // Operations buttons
                Button(
                    onClick = {
                        val ok = viewModel.repository.saveFirebaseConfig(
                            projectId = projectId.trim(),
                            appId = appId.trim(),
                            apiKey = apiKey.trim(),
                            syncEnabled = syncEnabled
                        )
                        if (ok) {
                            Toast.makeText(context, "Firebase configuration saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Saved fallback offline successfully.", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Connection Details")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isSyncing = true
                            viewModel.runPushSync { success, message ->
                                isSyncing = false
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            isSyncing = true
                            viewModel.runPullSync { success, message ->
                                isSyncing = false
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// STRING STYLING FORMATS UTILS
// -------------------------------------------------------------------------------------------------
fun formatAmount(amount: Double): String {
    val df = java.text.DecimalFormat("#,##,###.##")
    return df.format(amount)
}

fun formatSigned(value: Double, suffix: String): String {
    if (value > 0) return "+${value}$suffix"
    if (value < 0) return "${value}$suffix"
    return "0.000$suffix"
}

fun formatSignedRupees(value: Double): String {
    if (value > 0) return "+₹${formatAmount(value)}"
    if (value < 0) return "-₹${formatAmount(kotlin.math.abs(value))}"
    return "₹0"
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
