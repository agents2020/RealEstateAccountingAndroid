package com.example.realestate.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountingRepositoryTest {
    private lateinit var database: LocalDatabase
    private lateinit var dao: AccountingDao
    private lateinit var repository: AccountingRepository
    private var cashId = 0L
    private var incomeId = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.accountingDao()
        repository = AccountingRepository(database)
        cashId = dao.insertAccount(AccountEntity(code = "T100", name = "حساب اختبار نقدي", type = "أصول"))
        incomeId = dao.insertAccount(AccountEntity(code = "T400", name = "حساب اختبار إيراد", type = "إيرادات"))
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun balancedDocumentPostsExactlyOneLinkedEntry() = runBlocking {
        val documentId = repository.createDraftDocument(
            type = "قبض", date = "2026-10-25", description = "اختبار قبض",
            lines = listOf(
                DocumentLineEntity(documentId = 0, accountId = cashId, debit = 125000),
                DocumentLineEntity(documentId = 0, accountId = incomeId, credit = 125000)
            )
        )

        val entryId = repository.post(documentId)
        assertNotEquals(0L, entryId)
        val document = dao.getDocument(documentId)
        assertNotNull(document)
        assertEquals("مرحّل", document!!.status)
        val lines = dao.getLedgerLines(entryId)
        assertEquals(2, lines.size)
        assertEquals(125000L, lines.sumOf { it.debit })
        assertEquals(125000L, lines.sumOf { it.credit })
        assertEquals(documentId, dao.getLedgerEntryByDocumentId(documentId)!!.documentId)
    }

    @Test
    fun postingSameDocumentTwiceIsRejected() = runBlocking {
        val documentId = repository.createDraftDocument(
            type = "قيد يومية", date = "2026-10-25", description = "اختبار منع التكرار",
            lines = listOf(
                DocumentLineEntity(documentId = 0, accountId = cashId, debit = 500),
                DocumentLineEntity(documentId = 0, accountId = incomeId, credit = 500)
            )
        )
        repository.post(documentId)
        val secondAttempt = runCatching { repository.post(documentId) }
        assertTrue("يجب رفض ترحيل المستند مرتين", secondAttempt.isFailure)
    }

    @Test
    fun invalidCalendarDateIsRejected() = runBlocking {
        val result = runCatching {
            repository.createDraftDocument(
                type = "قبض", date = "2026-02-30", description = "تاريخ غير صحيح",
                lines = listOf(
                    DocumentLineEntity(documentId = 0, accountId = cashId, debit = 1),
                    DocumentLineEntity(documentId = 0, accountId = incomeId, credit = 1)
                )
            )
        }
        assertTrue("يجب رفض تاريخ غير موجود", result.isFailure)
    }

    @Test
    fun unbalancedDocumentIsRejectedBeforeSaving() = runBlocking {
        val result = runCatching {
            repository.createDraftDocument(
                type = "قيد يومية", date = "2026-10-25", description = "قيد غير متوازن",
                lines = listOf(
                    DocumentLineEntity(documentId = 0, accountId = cashId, debit = 100),
                    DocumentLineEntity(documentId = 0, accountId = incomeId, credit = 90)
                )
            )
        }
        assertTrue("يجب رفض القيد غير المتوازن", result.isFailure)
    }
}
