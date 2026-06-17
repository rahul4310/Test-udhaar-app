package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY partyName ASC")
    fun getAllParties(): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE partyId = :partyId")
    suspend fun getPartyById(partyId: String): Party?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: Party)

    @Update
    suspend fun updateParty(party: Party)

    @Query("DELETE FROM parties WHERE partyId = :partyId")
    suspend fun deleteParty(partyId: String)
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY date DESC, createdAt DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE partyId = :partyId ORDER BY date DESC, createdAt DESC")
    fun getInvoicesForParty(partyId: String): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE invoiceId = :invoiceId")
    suspend fun getInvoiceById(invoiceId: String): Invoice?

    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun getInvoiceCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice)

    @Query("DELETE FROM invoices WHERE invoiceId = :invoiceId")
    suspend fun deleteInvoice(invoiceId: String)
}

@Dao
interface InvoiceLineDao {
    @Query("SELECT * FROM invoice_lines ORDER BY date ASC, createdAt ASC")
    fun getAllInvoiceLines(): Flow<List<InvoiceLine>>

    @Query("SELECT * FROM invoice_lines WHERE partyId = :partyId ORDER BY date ASC, createdAt ASC")
    fun getLinesForParty(partyId: String): Flow<List<InvoiceLine>>

    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId ORDER BY createdAt ASC")
    suspend fun getLinesForInvoice(invoiceId: String): List<InvoiceLine>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceLine(line: InvoiceLine)

    @Query("DELETE FROM invoice_lines WHERE invoiceId = :invoiceId")
    suspend fun deleteLinesByInvoiceId(invoiceId: String)

    @Query("DELETE FROM invoice_lines WHERE partyId = :partyId")
    suspend fun deleteLinesByPartyId(partyId: String)
}
