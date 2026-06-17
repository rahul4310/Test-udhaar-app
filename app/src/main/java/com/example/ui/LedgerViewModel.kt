package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Invoice
import com.example.data.InvoiceLine
import com.example.data.Party
import com.example.data.UdhaarRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.round

// Represent a party with its current running balances
data class PartyWithBalance(
    val party: Party,
    val goldBalance: Double,
    val silverBalance: Double,
    val cashBalance: Double
)

data class LedgerEntry(
    val date: Long,
    val invoiceId: String,
    val invoiceNumber: String,
    val entryType: String,
    val description: String,
    val goldChange: Double,
    val silverChange: Double,
    val cashChange: Double,
    val runningGold: Double,
    val runningSilver: Double,
    val runningCash: Double
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    val repository = UdhaarRepository(application.applicationContext)

    // Helper functions for precise decimal rounding
    fun roundTo3Decimals(value: Double): Double {
        return round(value * 1000.0) / 1000.0
    }

    fun roundTo2Decimals(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    // Let's implement active streams
    val _allInvoiceLines = MutableStateFlow<List<InvoiceLine>>(emptyList())
    val allInvoiceLines: StateFlow<List<InvoiceLine>> = _allInvoiceLines

    val _allInvoices = MutableStateFlow<List<Invoice>>(emptyList())
    val allInvoices: StateFlow<List<Invoice>> = _allInvoices

    val partiesFlow = repository.getAllParties()

    // Combining parties with lines and invoices reactively
    val partiesWithBalancesFlow: Flow<List<PartyWithBalance>> = combine(
        repository.getAllParties(),
        _allInvoiceLines
    ) { parties, lines ->
        parties.map { party ->
            val partyLines = lines.filter { it.partyId == party.partyId }
            val goldChange = partyLines.sumOf { it.goldFineGrams }
            val silverChange = partyLines.sumOf { it.silverFineGrams }
            val cashChange = partyLines.sumOf { it.cashChargesRupees }

            PartyWithBalance(
                party = party,
                goldBalance = roundTo3Decimals(party.openingGoldFineGrams + goldChange),
                silverBalance = roundTo3Decimals(party.openingSilverFineGrams + silverChange),
                cashBalance = roundTo2Decimals(party.openingCashRupees + cashChange)
            )
        }
    }

    // List of active parties combined with their computed balances in real-time
    val partiesWithBalances: StateFlow<List<PartyWithBalance>> = partiesWithBalancesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Temporary invoice lines used when creating a hybrid invoice
    private val _tempInvoiceLines = MutableStateFlow<List<InvoiceLine>>(emptyList())
    val tempInvoiceLines: StateFlow<List<InvoiceLine>> = _tempInvoiceLines

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        // Load lines and invoices reactively to update balances
        observeDatabase()
        // Try automatic sync from Firestore on launch if enabled
        if (repository.isFirestoreSyncEnabled()) {
            viewModelScope.launch {
                repository.pullDataFromFirestore()
            }
        }
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            repository.getAllInvoices().collect {
                _allInvoices.value = it
            }
        }
        viewModelScope.launch {
            repository.getAllInvoiceLines().collect { lines ->
                _allInvoiceLines.value = lines
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Party CRUD Operations
    fun saveParty(
        partyId: String?,
        partyName: String,
        phoneNumber: String?,
        address: String?,
        notes: String?,
        openingGold: Double,
        openingSilver: Double,
        openingCash: Double,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val id = partyId ?: UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val existing = if (partyId != null) repository.getPartyById(partyId) else null
            
            val party = Party(
                partyId = id,
                partyName = partyName.trim(),
                phoneNumber = phoneNumber?.trim()?.ifEmpty { null },
                address = address?.trim()?.ifEmpty { null },
                notes = notes?.trim()?.ifEmpty { null },
                openingGoldFineGrams = roundTo3Decimals(openingGold),
                openingSilverFineGrams = roundTo3Decimals(openingSilver),
                openingCashRupees = roundTo2Decimals(openingCash),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            repository.insertParty(party)
            onComplete()
        }
    }

    fun deleteParty(partyId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteParty(partyId)
            onComplete()
        }
    }

    // Temporary Invoice Line Management
    fun clearTempInvoiceLines() {
        _tempInvoiceLines.value = emptyList()
    }

    fun addTempInvoiceLine(line: InvoiceLine) {
        _tempInvoiceLines.value = _tempInvoiceLines.value + line
    }

    fun removeTempInvoiceLine(lineId: String) {
        _tempInvoiceLines.value = _tempInvoiceLines.value.filter { it.lineId != lineId }
    }

    // Invoice Management
    fun saveInvoice(
        partyId: String,
        date: Long,
        notes: String?,
        onComplete: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val lines = _tempInvoiceLines.value
        if (lines.isEmpty()) {
            onFailure("Invoice must have at least one line.")
            return
        }

        viewModelScope.launch {
            try {
                val invoiceId = UUID.randomUUID().toString()
                val invoiceNumber = repository.getNextInvoiceNumber()
                
                val invoice = Invoice(
                    invoiceId = invoiceId,
                    invoiceNumber = invoiceNumber,
                    partyId = partyId,
                    date = date,
                    notes = notes?.trim()?.ifEmpty { null },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // Backport invoiceId & date to invoice lines
                val finalizedLines = lines.map {
                    it.copy(
                        invoiceId = invoiceId,
                        partyId = partyId,
                        date = date,
                        updatedAt = System.currentTimeMillis()
                    )
                }

                repository.insertInvoice(invoice, finalizedLines)
                clearTempInvoiceLines()
                onComplete(invoiceNumber)
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Failed to save invoice.")
            }
        }
    }

    fun deleteInvoice(invoiceId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteInvoice(invoiceId)
            onComplete()
        }
    }

    // Calculation helper for custom lines
    fun calculateLineFines(
        entryType: String,
        metal: String,
        weightGrams: Double,
        tunch: Double,
        cashChargesRupees: Double,
        isSettlement: Boolean,
        settlementMetal: String,
        cashAmountRupees: Double,
        settlementRateRupeesPerGram: Double
    ): Triple<Double, Double, Double> {
        var goldFine = 0.0
        var silverFine = 0.0
        var cash = cashChargesRupees

        if (isSettlement) {
            // Settlement flow
            if (settlementRateRupeesPerGram > 0) {
                val calculatedFine = roundTo3Decimals(cashAmountRupees / settlementRateRupeesPerGram)
                if (settlementMetal == "Gold") {
                    goldFine = -calculatedFine
                } else if (settlementMetal == "Silver") {
                    silverFine = -calculatedFine
                }
            }
            // Cash amount from settlement does not increase udhaar cash charges unless explicitly entered,
            // but we default cash charges to 0 or leave as entered.
        } else {
            // Normal flow
            val calculatedFine = roundTo3Decimals(weightGrams * tunch / 100.0)
            
            // Adjust signs based on EntryType
            val multiplier = if (entryType == "Payment") -1.0 else 1.0
            
            if (metal == "Gold") {
                goldFine = calculatedFine * multiplier
            } else if (metal == "Silver") {
                silverFine = calculatedFine * multiplier
            }
            
            cash = cashChargesRupees * multiplier
        }

        return Triple(goldFine, silverFine, cash)
    }

    // Compute party ledger entries
    fun getLedgerForParty(party: Party, lines: List<InvoiceLine>, invoices: List<Invoice>): List<LedgerEntry> {
        val ledgerList = mutableListOf<LedgerEntry>()

        // 1. Initial Opening Balance entry
        var runningG = party.openingGoldFineGrams
        var runningS = party.openingSilverFineGrams
        var runningC = party.openingCashRupees

        ledgerList.add(
            LedgerEntry(
                date = party.createdAt,
                invoiceId = "",
                invoiceNumber = "-",
                entryType = "Opening Balance",
                description = party.notes ?: "Starting Balance Set",
                goldChange = party.openingGoldFineGrams,
                silverChange = party.openingSilverFineGrams,
                cashChange = party.openingCashRupees,
                runningGold = roundTo3Decimals(runningG),
                runningSilver = roundTo3Decimals(runningS),
                runningCash = roundTo2Decimals(runningC)
            )
        )

        // Lookup map for fast invoice lookup
        val invoiceMap = invoices.associateBy { it.invoiceId }

        // Sort lines chronologically
        val sortedLines = lines.sortedWith(compareBy<InvoiceLine> { it.date }.thenBy { it.createdAt })

        sortedLines.forEach { line ->
            runningG += line.goldFineGrams
            runningS += line.silverFineGrams
            runningC += line.cashChargesRupees

            val inv = invoiceMap[line.invoiceId]
            val invNo = inv?.invoiceNumber ?: "N/A"

            var desc = line.itemDescription
            if (desc.isEmpty()) {
                desc = if (line.isSettlement) "Settlement Payment" else "${line.entryType} of ${line.metal}"
            }
            
            if (line.isSettlement) {
                desc += " (Settle ${line.settlementMetal} @ ₹${line.settlementRateRupeesPerGram}/g)"
            } else if (line.weightGrams > 0) {
                desc += " (${line.weightGrams}g @ ${line.tunch} tunch)"
            }

            if (!line.notes.isNullOrEmpty()) {
                desc += " [${line.notes}]"
            }

            ledgerList.add(
                LedgerEntry(
                    date = line.date,
                    invoiceId = line.invoiceId,
                    invoiceNumber = invNo,
                    entryType = line.entryType,
                    description = desc,
                    goldChange = line.goldFineGrams,
                    silverChange = line.silverFineGrams,
                    cashChange = line.cashChargesRupees,
                    runningGold = roundTo3Decimals(runningG),
                    runningSilver = roundTo3Decimals(runningS),
                    runningCash = roundTo2Decimals(runningC)
                )
            )
        }

        return ledgerList
    }

    // Sync Commands for UI Controls
    fun runPushSync(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.pushLocalDataToFirestore()
            if (result.isSuccess) {
                onComplete(true, "Successfully uploaded matches to Firestore!")
            } else {
                onComplete(false, result.exceptionOrNull()?.message ?: "Upload failed.")
            }
        }
    }

    fun runPullSync(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.pullDataFromFirestore()
            if (result.isSuccess) {
                observeDatabase() // Reload changes
                onComplete(true, "Successfully downloaded ledger records from Firestore!")
            } else {
                onComplete(false, result.exceptionOrNull()?.message ?: "Download failed.")
            }
        }
    }
}
