package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class UdhaarRepository(private val context: Context) {
    val database = AppDatabase.getDatabase(context)
    private val partyDao = database.partyDao()
    private val invoiceDao = database.invoiceDao()
    private val invoiceLineDao = database.invoiceLineDao()

    private val sharedPrefs = context.getSharedPreferences("firebase_ledger_prefs", Context.MODE_PRIVATE)

    interface FirebaseInitCallback {
        fun onSuccess()
        fun onFailure(e: Exception)
    }

    // Attempt to retrieve active Firestore instance, programmatically configuring.
    val firestore: FirebaseFirestore?
        get() {
            return try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    val pId = getFirebaseProjectId()
                    val appId = getFirebaseAppId()
                    val apiKey = getFirebaseApiKey()
                    
                    if (!pId.isNullOrEmpty() && !appId.isNullOrEmpty() && !apiKey.isNullOrEmpty()) {
                        val options = FirebaseOptions.Builder()
                            .setProjectId(pId)
                            .setApplicationId(appId)
                            .setApiKey(apiKey)
                            .build()
                        FirebaseApp.initializeApp(context, options)
                        FirebaseFirestore.getInstance()
                    } else {
                        null
                    }
                } else {
                    FirebaseFirestore.getInstance()
                }
            } catch (e: Exception) {
                Log.e("UdhaarRepository", "Firestore not available: ${e.message}")
                null
            }
        }

    // SharedPreferences Accessors for Dynamic Firebase config in UI settings
    fun getFirebaseProjectId(): String? = sharedPrefs.getString("project_id", "")
    fun getFirebaseAppId(): String? = sharedPrefs.getString("app_id", "")
    fun getFirebaseApiKey(): String? = sharedPrefs.getString("api_key", "")
    fun isFirestoreSyncEnabled(): Boolean = sharedPrefs.getBoolean("sync_enabled", false)

    fun saveFirebaseConfig(projectId: String, appId: String, apiKey: String, syncEnabled: Boolean): Boolean {
        sharedPrefs.edit()
            .putString("project_id", projectId)
            .putString("app_id", appId)
            .putString("api_key", apiKey)
            .putBoolean("sync_enabled", syncEnabled)
            .apply()

        // Reconnect/Reinitialize
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                val app = FirebaseApp.getInstance()
                // Cannot easily delete if live, but we can attempt to clear state or let it refresh
            }
            firestore != null
        } catch (e: Exception) {
            false
        }
    }

    // Party Operations
    fun getAllParties(): Flow<List<Party>> = partyDao.getAllParties()

    suspend fun getPartyById(partyId: String): Party? = withContext(Dispatchers.IO) {
        partyDao.getPartyById(partyId)
    }

    suspend fun insertParty(party: Party) = withContext(Dispatchers.IO) {
        partyDao.insertParty(party)
        if (isFirestoreSyncEnabled()) {
            syncPartyToFirestore(party)
        }
    }

    suspend fun deleteParty(partyId: String) = withContext(Dispatchers.IO) {
        partyDao.deleteParty(partyId)
        invoiceLineDao.deleteLinesByPartyId(partyId)
        if (isFirestoreSyncEnabled()) {
            firestore?.collection("parties")?.document(partyId)?.delete()
        }
    }

    // Invoice Operations
    fun getAllInvoices(): Flow<List<Invoice>> = invoiceDao.getAllInvoices()
    fun getInvoicesForParty(partyId: String): Flow<List<Invoice>> = invoiceDao.getInvoicesForParty(partyId)

    suspend fun insertInvoice(invoice: Invoice, lines: List<InvoiceLine>) = withContext(Dispatchers.IO) {
        invoiceDao.insertInvoice(invoice)
        // Insert lines
        lines.forEach { line ->
            invoiceLineDao.insertInvoiceLine(line)
            if (isFirestoreSyncEnabled()) {
                syncLineToFirestore(line)
            }
        }
        if (isFirestoreSyncEnabled()) {
            syncInvoiceToFirestore(invoice)
        }
    }

    suspend fun deleteInvoice(invoiceId: String) = withContext(Dispatchers.IO) {
        val lines = invoiceLineDao.getLinesForInvoice(invoiceId)
        invoiceDao.deleteInvoice(invoiceId)
        invoiceLineDao.deleteLinesByInvoiceId(invoiceId)
        
        if (isFirestoreSyncEnabled()) {
            val fs = firestore
            if (fs != null) {
                fs.collection("invoices").document(invoiceId).delete()
                lines.forEach { line ->
                    fs.collection("invoiceLines").document(line.lineId).delete()
                }
            }
        }
    }

    suspend fun getNextInvoiceNumber(): String = withContext(Dispatchers.IO) {
        val count = invoiceDao.getInvoiceCount()
        val nextNum = count + 1
        String.format("INV-%04d", nextNum)
    }

    // Invoice Lines
    fun getAllInvoiceLines(): Flow<List<InvoiceLine>> = invoiceLineDao.getAllInvoiceLines()
    fun getLinesForParty(partyId: String): Flow<List<InvoiceLine>> = invoiceLineDao.getLinesForParty(partyId)

    suspend fun getLinesForInvoice(invoiceId: String): List<InvoiceLine> = withContext(Dispatchers.IO) {
        invoiceLineDao.getLinesForInvoice(invoiceId)
    }

    // Firestore Sync Push Helpers
    private fun syncPartyToFirestore(party: Party) {
        firestore?.collection("parties")?.document(party.partyId)?.set(party)
            ?.addOnFailureListener { Log.e("UdhaarRepository", "Party sync failed: ${it.message}") }
    }

    private fun syncInvoiceToFirestore(invoice: Invoice) {
        firestore?.collection("invoices")?.document(invoice.invoiceId)?.set(invoice)
            ?.addOnFailureListener { Log.e("UdhaarRepository", "Invoice sync failed: ${it.message}") }
    }

    private fun syncLineToFirestore(line: InvoiceLine) {
        firestore?.collection("invoiceLines")?.document(line.lineId)?.set(line)
            ?.addOnFailureListener { Log.e("UdhaarRepository", "Line sync failed: ${it.message}") }
    }

    // Push local database entirely to Firestore
    suspend fun pushLocalDataToFirestore(): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firestore is not configured or available."))
        try {
            val parties = partyDao.getAllParties().firstOrNull() ?: emptyList()
            val invoices = invoiceDao.getAllInvoices().firstOrNull() ?: emptyList()
            val lines = invoiceLineDao.getAllInvoiceLines().firstOrNull() ?: emptyList()

            parties.forEach { fs.collection("parties").document(it.partyId).set(it) }
            invoices.forEach { fs.collection("invoices").document(it.invoiceId).set(it) }
            lines.forEach { fs.collection("invoiceLines").document(it.lineId).set(it) }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Pull Firestore data entirely to Local DB
    suspend fun pullDataFromFirestore(): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(Exception("Firestore is not configured or available."))
        try {
            // Synchronously download parties and insert
            val partiesSnapshot = fs.collection("parties").get().await()
            val remoteParties = partiesSnapshot.toObjects(Party::class.java)
            remoteParties.forEach { partyDao.insertParty(it) }

            // Download invoices
            val invoicesSnapshot = fs.collection("invoices").get().await()
            val remoteInvoices = invoicesSnapshot.toObjects(Invoice::class.java)
            remoteInvoices.forEach { invoiceDao.insertInvoice(it) }

            // Download invoice lines
            val linesSnapshot = fs.collection("invoiceLines").get().await()
            val remoteLines = linesSnapshot.toObjects(InvoiceLine::class.java)
            remoteLines.forEach { invoiceLineDao.insertInvoiceLine(it) }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Extension to wrap Firestore Tasks in Coroutines safely
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    if (isComplete) {
        val e = exception
        if (e != null) {
            throw e
        } else {
            return result
        }
    }
    return kotlin.coroutines.suspendCoroutine { continuation ->
        addOnCompleteListener { task ->
            val e = task.exception
            if (e != null) {
                continuation.resumeWith(Result.failure(e))
            } else {
                continuation.resumeWith(Result.success(task.result))
            }
        }
    }
}
