package com.example.realestate.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "properties")
data class PropertyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val location: String = "",
    val units: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

// Legacy summary row retained for compatibility with the previous prototype.
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val description: String,
    val debit: Double,
    val credit: Double,
    val status: String = "مسودة",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val date: String,
    val amount: Double,
    val description: String = "",
    val posted: Boolean = false
)

@Dao
abstract class AccountingDao {
    @Query("SELECT * FROM properties ORDER BY id DESC") abstract fun observeProperties(): Flow<List<PropertyEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertProperty(item: PropertyEntity)
    @Query("SELECT COUNT(*) FROM properties") abstract fun observePropertyCount(): Flow<Int>
    @Query("SELECT * FROM journal_entries ORDER BY id DESC") abstract fun observeJournal(): Flow<List<JournalEntryEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertJournal(item: JournalEntryEntity)
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'إيراد' AND posted = 1") abstract fun observeRevenue(): Flow<Double>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'مصروف' AND posted = 1") abstract fun observeExpenses(): Flow<Double>
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertTransaction(item: TransactionEntity)

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY code") abstract fun observeAccounts(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1") abstract suspend fun getAccount(id: Long): AccountEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertAccount(item: AccountEntity): Long
    @Query("SELECT * FROM projects ORDER BY id DESC") abstract fun observeProjects(): Flow<List<ProjectEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertProject(item: ProjectEntity): Long
    @Query("SELECT * FROM parties ORDER BY name") abstract fun observeParties(): Flow<List<PartyEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertParty(item: PartyEntity): Long
    @Query("SELECT * FROM documents ORDER BY id DESC") abstract fun observeDocuments(): Flow<List<AccountingDocumentEntity>>
    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1") abstract suspend fun getDocument(id: Long): AccountingDocumentEntity?
    @Query("SELECT * FROM document_lines WHERE documentId = :documentId ORDER BY id") abstract suspend fun getDocumentLines(documentId: Long): List<DocumentLineEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertDocument(item: AccountingDocumentEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertDocumentLine(item: DocumentLineEntity): Long
    @Query("UPDATE documents SET status = :status, postedAt = :postedAt WHERE id = :id AND status = 'مسودة'") abstract suspend fun updateDocumentStatus(id: Long, status: String, postedAt: Long): Int
    @Query("SELECT * FROM ledger_entries ORDER BY id DESC") abstract fun observeLedgerEntries(): Flow<List<LedgerEntryEntity>>
    @Query("SELECT * FROM ledger_entries WHERE documentId = :documentId LIMIT 1") abstract suspend fun getLedgerEntryByDocumentId(documentId: Long): LedgerEntryEntity?
    @Query("SELECT * FROM ledger_lines WHERE entryId = :entryId ORDER BY id") abstract suspend fun getLedgerLines(entryId: Long): List<LedgerLineEntity>
    @Query("SELECT a.id AS accountId, a.code AS code, a.name AS name, a.type AS type, COALESCE(SUM(l.debit), 0) AS debit, COALESCE(SUM(l.credit), 0) AS credit FROM accounts a LEFT JOIN ledger_lines l ON l.accountId = a.id GROUP BY a.id, a.code, a.name, a.type ORDER BY a.code")
    abstract fun observeTrialBalance(): Flow<List<TrialBalanceRow>>
    @Query("SELECT COUNT(*) FROM ledger_entries") abstract fun observePostedEntryCount(): Flow<Int>
    @Query("SELECT COALESCE(SUM(debit), 0) FROM ledger_lines") abstract fun observeTotalDebits(): Flow<Long>
    @Query("SELECT COALESCE(SUM(credit), 0) FROM ledger_lines") abstract fun observeTotalCredits(): Flow<Long>
    @Query("SELECT COALESCE(SUM(l.credit - l.debit), 0) FROM ledger_lines l INNER JOIN accounts a ON a.id = l.accountId WHERE a.type = 'إيرادات'") abstract fun observeLedgerRevenue(): Flow<Long>
    @Query("SELECT COALESCE(SUM(l.debit - l.credit), 0) FROM ledger_lines l INNER JOIN accounts a ON a.id = l.accountId WHERE a.type = 'مصروفات'") abstract fun observeLedgerExpenses(): Flow<Long>
    @Query("SELECT COALESCE(SUM(debit), 0) - COALESCE(SUM(credit), 0) FROM ledger_lines WHERE accountId = :accountId") abstract suspend fun accountBalance(accountId: Long): Long
    @Query("SELECT * FROM audit_log ORDER BY createdAt DESC LIMIT 200") abstract fun observeAuditLog(): Flow<List<AuditLogEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertAudit(item: AuditLogEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertLedgerEntry(item: LedgerEntryEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertLedgerLine(item: LedgerLineEntity)

    /** Atomic posting: rejects empty, one-sided, or unbalanced documents and creates one linked ledger entry. */
    @Transaction
    open suspend fun postDocument(documentId: Long): Result<Long> {
        val document = getDocument(documentId) ?: return Result.failure(IllegalArgumentException("المستند غير موجود"))
        if (document.status != "مسودة") return Result.failure(IllegalStateException("لا يمكن ترحيل مستند ليس في حالة مسودة"))
        val lines = getDocumentLines(documentId)
        if (lines.size < 2) return Result.failure(IllegalArgumentException("يجب إدخال سطرين محاسبيين على الأقل"))
        if (lines.any { it.debit < 0 || it.credit < 0 || (it.debit > 0L && it.credit > 0L) || (it.debit == 0L && it.credit == 0L) }) {
            return Result.failure(IllegalArgumentException("كل سطر يجب أن يحتوي على مدين أو دائن موجب، وليس كليهما"))
        }
        val debitTotal = lines.sumOf { it.debit }
        val creditTotal = lines.sumOf { it.credit }
        if (debitTotal <= 0L || debitTotal != creditTotal) {
            return Result.failure(IllegalArgumentException("القيد غير متوازن: إجمالي المدين يجب أن يساوي إجمالي الدائن"))
        }
        if (lines.any { getAccount(it.accountId) == null }) return Result.failure(IllegalArgumentException("يوجد حساب غير صالح في تفاصيل المستند"))
        val entryId = insertLedgerEntry(LedgerEntryEntity(
            entryNumber = "JE-${document.documentNumber}", documentId = document.id,
            date = document.date, description = document.description
        ))
        lines.forEach { line ->
            insertLedgerLine(LedgerLineEntity(
                entryId = entryId, accountId = line.accountId, projectId = document.projectId,
                partyId = document.partyId, debit = line.debit, credit = line.credit, memo = line.memo
            ))
        }
        val changed = updateDocumentStatus(documentId, "مرحّل", System.currentTimeMillis())
        if (changed != 1) throw IllegalStateException("تعذر تحديث حالة المستند؛ تم إلغاء الترحيل")
        insertAudit(AuditLogEntity(action = "ترحيل", entityType = "مستند", entityId = documentId, details = "ترحيل إلى القيد ${"JE-${document.documentNumber}"}"))
        return Result.success(entryId)
    }
}

@Database(
    entities = [
        PropertyEntity::class, JournalEntryEntity::class, TransactionEntity::class,
        ProjectEntity::class, PlotEntity::class, PartyEntity::class, InvestorEntity::class,
        ContractEntity::class, InstallmentEntity::class, AccountEntity::class,
        AccountingDocumentEntity::class, DocumentLineEntity::class, LedgerEntryEntity::class,
        LedgerLineEntity::class, AuditLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun accountingDao(): AccountingDao

    companion object {
        @Volatile private var INSTANCE: LocalDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS projects (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, location TEXT NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_name ON projects(name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS parties (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, openingBalance INTEGER NOT NULL, notes TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_parties_name ON parties(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_parties_type ON parties(type)")
                db.execSQL("CREATE TABLE IF NOT EXISTS plots (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, plotNumber TEXT NOT NULL, areaM2 REAL NOT NULL, askingPrice INTEGER NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plots_projectId ON plots(projectId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_plots_projectId_plotNumber ON plots(projectId, plotNumber)")
                db.execSQL("CREATE TABLE IF NOT EXISTS investors (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, partyId INTEGER NOT NULL, agreedCapital INTEGER NOT NULL, sharePercent REAL NOT NULL, notes TEXT NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(partyId) REFERENCES parties(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_investors_projectId ON investors(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_investors_partyId ON investors(partyId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS contracts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, contractNumber TEXT NOT NULL, type TEXT NOT NULL, partyId INTEGER NOT NULL, projectId INTEGER, startDate TEXT NOT NULL, endDate TEXT NOT NULL, value INTEGER NOT NULL, status TEXT NOT NULL, terms TEXT NOT NULL, FOREIGN KEY(partyId) REFERENCES parties(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contracts_partyId ON contracts(partyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contracts_projectId ON contracts(projectId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS installments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, contractId INTEGER NOT NULL, dueDate TEXT NOT NULL, amount INTEGER NOT NULL, paidAmount INTEGER NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL, FOREIGN KEY(contractId) REFERENCES contracts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_installments_contractId ON installments(contractId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_installments_dueDate ON installments(dueDate)")
                db.execSQL("CREATE TABLE IF NOT EXISTS accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, parentId INTEGER, isSystem INTEGER NOT NULL, isActive INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_code ON accounts(code)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_accounts_type ON accounts(type)")
                db.execSQL("CREATE TABLE IF NOT EXISTS documents (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, documentNumber TEXT NOT NULL, type TEXT NOT NULL, date TEXT NOT NULL, description TEXT NOT NULL, partyId INTEGER, projectId INTEGER, contractId INTEGER, status TEXT NOT NULL, createdAt INTEGER NOT NULL, postedAt INTEGER)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_documents_documentNumber ON documents(documentNumber)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_date ON documents(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_type ON documents(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_status ON documents(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_projectId ON documents(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_partyId ON documents(partyId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS document_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, documentId INTEGER NOT NULL, accountId INTEGER NOT NULL, debit INTEGER NOT NULL, credit INTEGER NOT NULL, memo TEXT NOT NULL, FOREIGN KEY(documentId) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_document_lines_documentId ON document_lines(documentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_document_lines_accountId ON document_lines(accountId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS ledger_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entryNumber TEXT NOT NULL, documentId INTEGER NOT NULL, date TEXT NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(documentId) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ledger_entries_documentId ON ledger_entries(documentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_entries_date ON ledger_entries(date)")
                db.execSQL("CREATE TABLE IF NOT EXISTS ledger_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entryId INTEGER NOT NULL, accountId INTEGER NOT NULL, projectId INTEGER, partyId INTEGER, debit INTEGER NOT NULL, credit INTEGER NOT NULL, memo TEXT NOT NULL, FOREIGN KEY(entryId) REFERENCES ledger_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_lines_entryId ON ledger_lines(entryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_lines_accountId ON ledger_lines(accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_lines_projectId ON ledger_lines(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_lines_partyId ON ledger_lines(partyId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, action TEXT NOT NULL, entityType TEXT NOT NULL, entityId INTEGER NOT NULL, details TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_createdAt ON audit_log(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_entityType ON audit_log(entityType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_entityId ON audit_log(entityId)")
                seedAccounts(db)
            }
        }

        private fun seedAccounts(db: SupportSQLiteDatabase) {
            val accounts = listOf(
                Triple("1000", "الصندوق", "أصول"), Triple("1010", "البنك", "أصول"),
                Triple("1100", "الذمم المدينة", "أصول"), Triple("1200", "العقارات والمخزون العقاري", "أصول"),
                Triple("2000", "الذمم الدائنة", "التزامات"), Triple("2100", "قروض مستحقة", "التزامات"),
                Triple("3000", "رأس المال", "حقوق ملكية"), Triple("3100", "جاري الشركاء", "حقوق ملكية"),
                Triple("4000", "إيرادات بيع العقارات", "إيرادات"), Triple("4100", "إيرادات الإيجار", "إيرادات"),
                Triple("5000", "تكلفة العقارات المباعة", "مصروفات"), Triple("5100", "مصروفات الصيانة", "مصروفات"),
                Triple("5200", "مصروفات إدارية وعمومية", "مصروفات"), Triple("5300", "مصروفات تسويق وعمولات", "مصروفات")
            )
            accounts.forEach { (code, name, type) ->
                db.execSQL("INSERT OR IGNORE INTO accounts(code, name, type, parentId, isSystem, isActive) VALUES(?, ?, ?, NULL, 1, 1)", arrayOf(code, name, type))
            }
        }

        fun get(context: Context): LocalDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, LocalDatabase::class.java, "real_estate_accounting.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedAccounts(db)
                    }
                })
                .build().also { INSTANCE = it }
        }
    }
}
