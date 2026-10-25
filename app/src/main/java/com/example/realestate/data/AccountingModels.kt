package com.example.realestate.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects", indices = [Index("name")])
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val location: String = "",
    val description: String = "",
    val status: String = "نشط",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "plots", foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)], indices = [Index("projectId"), Index(value = ["projectId", "plotNumber"], unique = true)])
data class PlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val plotNumber: String,
    val areaM2: Double = 0.0,
    val askingPrice: Long = 0,
    val status: String = "متاحة",
    val notes: String = ""
)

@Entity(tableName = "parties", indices = [Index("name"), Index("type")])
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val phone: String = "",
    val address: String = "",
    val openingBalance: Long = 0,
    val notes: String = ""
)

@Entity(tableName = "investors", foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = PartyEntity::class, parentColumns = ["id"], childColumns = ["partyId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("projectId"), Index("partyId")])
data class InvestorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val partyId: Long,
    val agreedCapital: Long = 0,
    val sharePercent: Double = 0.0,
    val notes: String = ""
)

@Entity(tableName = "contracts", foreignKeys = [ForeignKey(entity = PartyEntity::class, parentColumns = ["id"], childColumns = ["partyId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("partyId"), Index("projectId")])
data class ContractEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contractNumber: String,
    val type: String,
    val partyId: Long,
    val projectId: Long? = null,
    val startDate: String,
    val endDate: String = "",
    val value: Long = 0,
    val status: String = "ساري",
    val terms: String = ""
)

@Entity(tableName = "installments", foreignKeys = [ForeignKey(entity = ContractEntity::class, parentColumns = ["id"], childColumns = ["contractId"], onDelete = ForeignKey.CASCADE)], indices = [Index("contractId"), Index("dueDate")])
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contractId: Long,
    val dueDate: String,
    val amount: Long,
    val paidAmount: Long = 0,
    val status: String = "مستحق",
    val notes: String = ""
)

@Entity(tableName = "accounts", indices = [Index(value = ["code"], unique = true), Index("type")])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val type: String,
    val parentId: Long? = null,
    val isSystem: Boolean = false,
    val isActive: Boolean = true
)

@Entity(tableName = "documents", indices = [Index(value = ["documentNumber"], unique = true), Index("date"), Index("type"), Index("status"), Index("projectId"), Index("partyId")])
data class AccountingDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentNumber: String,
    val type: String,
    val date: String,
    val description: String,
    val partyId: Long? = null,
    val projectId: Long? = null,
    val contractId: Long? = null,
    val status: String = "مسودة",
    val createdAt: Long = System.currentTimeMillis(),
    val postedAt: Long? = null
)

@Entity(tableName = "document_lines", foreignKeys = [ForeignKey(entity = AccountingDocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("documentId"), Index("accountId")])
data class DocumentLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val accountId: Long,
    val debit: Long = 0,
    val credit: Long = 0,
    val memo: String = ""
)

@Entity(tableName = "ledger_entries", foreignKeys = [ForeignKey(entity = AccountingDocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["documentId"], unique = true), Index("date")])
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryNumber: String,
    val documentId: Long,
    val date: String,
    val description: String,
    val status: String = "مرحّل",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ledger_lines", foreignKeys = [ForeignKey(entity = LedgerEntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("entryId"), Index("accountId"), Index("projectId"), Index("partyId")])
data class LedgerLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val accountId: Long,
    val projectId: Long? = null,
    val partyId: Long? = null,
    val debit: Long = 0,
    val credit: Long = 0,
    val memo: String = ""
)

@Entity(tableName = "audit_log", indices = [Index("createdAt"), Index("entityType"), Index("entityId")])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val entityType: String,
    val entityId: Long,
    val details: String = "",
    val createdAt: Long = System.currentTimeMillis()
)


/** Aggregate row used by the trial balance and general-ledger summary. */
data class TrialBalanceRow(
    val accountId: Long,
    val code: String,
    val name: String,
    val type: String,
    val debit: Long,
    val credit: Long
)
