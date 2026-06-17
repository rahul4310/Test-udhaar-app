package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "parties")
data class Party(
    @PrimaryKey val partyId: String = "",
    val partyName: String = "",
    val phoneNumber: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val openingGoldFineGrams: Double = 0.0,
    val openingSilverFineGrams: Double = 0.0,
    val openingCashRupees: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) : Serializable

@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey val invoiceId: String = "",
    val invoiceNumber: String = "",
    val partyId: String = "",
    val date: Long = 0L, // Store as millisecond timestamp
    val notes: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) : Serializable

@Entity(tableName = "invoice_lines")
data class InvoiceLine(
    @PrimaryKey val lineId: String = "",
    val invoiceId: String = "",
    val partyId: String = "",
    val date: Long = 0L,
    val entryType: String = "", // Sale, Payment, Opening Balance, Adjustment
    val metal: String = "", // Gold, Silver, Cash, Other
    val itemDescription: String = "",
    val weightGrams: Double = 0.0,
    val purity: Double = 0.0,
    val tunch: Double = 0.0,
    val goldFineGrams: Double = 0.0,
    val silverFineGrams: Double = 0.0,
    val cashChargesRupees: Double = 0.0,
    val isSettlement: Boolean = false,
    val settlementMetal: String = "None", // Gold, Silver, None
    val cashAmountRupees: Double = 0.0,
    val settlementRateRupeesPerGram: Double = 0.0,
    val calculatedFineGrams: Double = 0.0,
    val notes: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) : Serializable
