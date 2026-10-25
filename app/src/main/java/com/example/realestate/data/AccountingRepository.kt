package com.example.realestate.data

import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.time.LocalDate

/** Business rules shared by document entry and posting screens. All amounts are whole Yemeni rials (YER). */
class AccountingRepository(private val database: LocalDatabase) {
    private val dao = database.accountingDao()

    suspend fun createDraftDocument(
        type: String,
        date: String,
        description: String,
        lines: List<DocumentLineEntity>,
        partyId: Long? = null,
        projectId: Long? = null,
        contractId: Long? = null
    ): Long = database.withTransaction {
        require(type in setOf("قبض", "صرف", "شراء", "بيع", "مساهمة", "توزيع أرباح", "قيد يومية", "تسوية")) { "نوع المستند غير معروف" }
        require(runCatching { LocalDate.parse(date) }.isSuccess) { "أدخل تاريخًا صحيحًا بصيغة YYYY-MM-DD" }
        require(description.isNotBlank()) { "وصف المستند مطلوب" }
        require(lines.size >= 2) { "أدخل سطرين محاسبيين على الأقل" }
        require(lines.all { it.debit >= 0 && it.credit >= 0 && ((it.debit > 0L) xor (it.credit > 0L)) }) {
            "كل سطر يجب أن يحتوي على مبلغ مدين أو دائن فقط"
        }
        val debit = lines.sumOf { it.debit }
        val credit = lines.sumOf { it.credit }
        require(debit > 0L && debit == credit) { "المستند غير متوازن: المدين $debit والدائن $credit" }
        val serial = UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)
        val documentId = dao.insertDocument(
            AccountingDocumentEntity(
                documentNumber = "DOC-$serial", type = type, date = date,
                description = description.trim(), partyId = partyId, projectId = projectId, contractId = contractId
            )
        )
        lines.forEach { line ->
            require(dao.getAccount(line.accountId) != null) { "أحد الحسابات المحددة غير موجود" }
            dao.insertDocumentLine(line.copy(id = 0, documentId = documentId))
        }
        dao.insertAudit(AuditLogEntity(action = "إنشاء مسودة", entityType = "مستند", entityId = documentId, details = type))
        documentId
    }

    suspend fun post(documentId: Long): Long = dao.postDocument(documentId).getOrThrow()

    companion object {
        fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
