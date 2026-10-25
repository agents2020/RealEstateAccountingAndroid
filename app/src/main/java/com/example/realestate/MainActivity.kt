package com.example.realestate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.example.realestate.data.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val Ink = Color(0xFF172B4D)
private val Teal = Color(0xFF0F766E)
private val Canvas = Color(0xFFF5F7FA)
private val Muted = Color(0xFF738096)
private val Border = Color(0xFFE6EAF0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = LocalDatabase.get(this)
        val dao = database.accountingDao()
        val repository = AccountingRepository(database)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Teal, secondary = Color(0xFFD69E36), background = Canvas, surface = Color.White)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AccountingApp(dao, repository)
                }
            }
        }
    }
}

private enum class Section(val label: String) { Home("الرئيسية"), Properties("العقارات"), Documents("المستندات"), Entries("القيود"), Reports("التقارير"), More("المزيد") }

@Composable
private fun AccountingApp(dao: AccountingDao, repository: AccountingRepository) {
    var section by remember { mutableStateOf(Section.Home) }
    var showAddProperty by remember { mutableStateOf(false) }
    var showAddDocument by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var propertyName by remember { mutableStateOf("") }
    var propertyLocation by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val properties by dao.observeProperties().collectAsStateWithLifecycle(initialValue = emptyList())
    val revenue by dao.observeLedgerRevenue().collectAsStateWithLifecycle(initialValue = 0L)
    val expenses by dao.observeLedgerExpenses().collectAsStateWithLifecycle(initialValue = 0L)
    val ledgerEntries by dao.observeLedgerEntries().collectAsStateWithLifecycle(initialValue = emptyList())
    val documents by dao.observeDocuments().collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by dao.observeAccounts().collectAsStateWithLifecycle(initialValue = emptyList())
    val trialBalance by dao.observeTrialBalance().collectAsStateWithLifecycle(initialValue = emptyList())
    val auditLog by dao.observeAuditLog().collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(containerColor = Canvas, bottomBar = {
        NavigationBar(containerColor = Color.White, tonalElevation = 6.dp) {
            Section.values().forEach { item ->
                val icon = when(item) {
                    Section.Home -> Icons.Default.Home
                    Section.Properties -> Icons.Default.Apartment
                    Section.Documents -> Icons.Default.Description
                    Section.Entries -> Icons.Default.MenuBook
                    Section.Reports -> Icons.Default.Assessment
                    Section.More -> Icons.Default.MoreHoriz
                }
                NavigationBarItem(selected = section == item, onClick = { section = item }, icon = { Icon(icon, null) }, label = { Text(item.label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Teal, selectedTextColor = Teal, indicatorColor = Color(0xFFE1F3F0)))
            }
        }
    }, floatingActionButton = {
        if (section == Section.Properties) FloatingActionButton(onClick = { showAddProperty = true }, containerColor = Teal, contentColor = Color.White) { Icon(Icons.Default.Add, contentDescription = "إضافة عقار") }
        if (section == Section.Documents) FloatingActionButton(onClick = { operationError = null; showAddDocument = true }, containerColor = Teal, contentColor = Color.White) { Icon(Icons.Default.Add, contentDescription = "إضافة مستند") }
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("المحاسبة العقارية", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("إدارة استثماراتك بثقة", color = Muted, fontSize = 13.sp)
                    }
                    Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                        IconButton(onClick = {}) { Icon(Icons.Default.NotificationsNone, contentDescription = "الإشعارات", tint = Ink) }
                    }
                }
            }
            when(section) {
                Section.Home -> {
                    item { HeroCard(properties.size) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard("الإيرادات المرحلة", money(revenue.toDouble()), Color(0xFFE5F5F0), Teal, Modifier.weight(1f))
                            MetricCard("المصروفات المرحلة", money(expenses.toDouble()), Color(0xFFFFF0E8), Color(0xFFC46A32), Modifier.weight(1f))
                        }
                    }
                    item { MetricCard("صافي النتيجة", money((revenue-expenses).toDouble()), Color.White, Ink, Modifier.fillMaxWidth()) }
                    item { SectionHeader("الوصول السريع", "أهم الأقسام") }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickTile("العقارات", Icons.Default.Apartment, { section = Section.Properties }, Modifier.weight(1f))
                            QuickTile("دفتر اليومية", Icons.Default.MenuBook, { section = Section.Entries }, Modifier.weight(1f))
                            QuickTile("التقارير", Icons.Default.Assessment, { section = Section.Reports }, Modifier.weight(1f))
                        }
                    }
                    item { SectionHeader("آخر القيود", "${ledgerEntries.size} قيد") }
                    if (ledgerEntries.isEmpty()) item { EmptyCard("لا توجد قيود بعد", "ستظهر القيود المحاسبية هنا بعد ترحيل المستندات.") }
                    items(ledgerEntries.take(5)) { entry -> LedgerEntryRow(entry) }
                }
                Section.Properties -> {
                    item { SectionHeader("العقارات والمشروعات", "${properties.size} عقار") }
                    if (properties.isEmpty()) item { EmptyCard("ابدأ بإضافة أول عقار", "اضغط زر + لإضافة عقار إلى قاعدة البيانات المحلية.") }
                    items(properties) { p -> PropertyRow(p) }
                }
                Section.Documents -> {
                    item { SectionHeader("المستندات المالية", "${documents.size} مستند") }
                    item { EmptyCard("المستند هو مصدر القيد", "أنشئ مستند قبض أو صرف أو بيع أو شراء، ثم رحّله لإنشاء قيد يومية مرتبط تلقائيًا. يجب أن يتساوى المدين والدائن.") }
                    if (documents.isEmpty()) item { EmptyCard("لا توجد مستندات", "اضغط + لإنشاء أول مستند محاسبي.") }
                    items(documents) { d ->
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(d.documentNumber, color = Ink, fontWeight = FontWeight.Bold)
                                    Surface(shape = RoundedCornerShape(10.dp), color = if (d.status == "مرحّل") Color(0xFFE5F5F0) else Color(0xFFFFF0D8)) { Text(d.status, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = if (d.status == "مرحّل") Teal else Color(0xFF996515), fontSize = 11.sp) }
                                }
                                Text("${d.type} • ${d.date}", color = Muted, fontSize = 12.sp)
                                Text(d.description, color = Ink, fontSize = 14.sp)
                            }
                        }
                    }
                }
                Section.Entries -> {
                    item { SectionHeader("دفتر اليومية", "${ledgerEntries.size} قيد مرحّل") }
                    item { EmptyCard("دفتر اليومية", "كل قيد في هذه القائمة مرتبط بالمستند المصدر. لا تُرحّل المستندات غير المتوازنة.") }
                    items(ledgerEntries) { LedgerEntryRow(it) }
                }
                Section.Reports -> {
                    val totalDebit = trialBalance.sumOf { it.debit }
                    val totalCredit = trialBalance.sumOf { it.credit }
                    item { SectionHeader("التقارير المالية", "مبنية على القيود المرحلة") }
                    item { MetricCard("إجمالي الإيرادات", money(revenue.toDouble()), Color.White, Teal, Modifier.fillMaxWidth()) }
                    item { MetricCard("إجمالي المصروفات", money(expenses.toDouble()), Color.White, Color(0xFFC46A32), Modifier.fillMaxWidth()) }
                    item { MetricCard("صافي الربح / (الخسارة)", money((revenue-expenses).toDouble()), Color.White, Ink, Modifier.fillMaxWidth()) }
                    item { SectionHeader("ميزان المراجعة", if (totalDebit == totalCredit) "متوازن" else "راجع الفرق") }
                    item {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (totalDebit == totalCredit) Color(0xFFE5F5F0) else Color(0xFFFFE8E8))) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text("مجموع المدين", fontSize = 11.sp, color = Muted); Text(money(totalDebit.toDouble()), fontWeight = FontWeight.Bold, color = Ink) }
                                Column { Text("مجموع الدائن", fontSize = 11.sp, color = Muted); Text(money(totalCredit.toDouble()), fontWeight = FontWeight.Bold, color = Ink) }
                            }
                        }
                    }
                    items(trialBalance.filter { it.debit != 0L || it.credit != 0L }) { row ->
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("${row.code} • ${row.name}", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(row.type, color = Muted, fontSize = 11.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("مدين: ${money(row.debit.toDouble())}", color = Teal, fontSize = 12.sp)
                                    Text("دائن: ${money(row.credit.toDouble())}", color = Color(0xFFC46A32), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    if (trialBalance.none { it.debit != 0L || it.credit != 0L }) item { EmptyCard("لا توجد حركة محاسبية", "رحّل مستندًا متوازنًا لعرض أرصدة الحسابات هنا.") }
                    item { Text("تنبيه: التقرير تجميعي للحركات المرحلة فقط، ولا يعرض حتى الآن قوائم مالية معتمدة أو أرصدة افتتاحية.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp) }
                }
                Section.More -> {
                    item { SectionHeader("الأدوات والإعدادات", "محلي بالكامل") }
                    item { EmptyCard("النسخ الاحتياطي", "النسخ الاحتياطي والاستعادة لم يكتمل تنفيذهما بعد. لا تحذف التطبيق أو بياناته قبل حفظ نسخة مستقلة.") }
                    item { EmptyCard("الأمان", "تُحفظ قاعدة البيانات داخل مساحة التطبيق على هذا الجهاز. حذف بيانات التطبيق قد يحذف الدفاتر.") }
                    item { SectionHeader("سجل التدقيق", "آخر ${auditLog.size} عملية") }
                    if (auditLog.isEmpty()) item { EmptyCard("لا توجد عمليات مسجلة", "ستظهر هنا عمليات إنشاء المستندات وترحيلها.") }
                    items(auditLog) { log ->
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${log.action} • ${log.entityType} #${log.entityId}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(log.details, color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    item { Text("لا يحتاج التطبيق إلى اتصال بالإنترنت للتخزين المحلي.", color = Muted, fontSize = 12.sp) }
                }
            }
        }
    }
    if (operationError != null) {
        AlertDialog(onDismissRequest = { operationError = null }, title = { Text("تعذر تنفيذ العملية") }, text = { Text(operationError ?: "") }, confirmButton = { TextButton(onClick = { operationError = null }) { Text("حسنًا") } })
    }
    if (showAddDocument) {
        DocumentDraftDialog(accounts = accounts, onDismiss = { showAddDocument = false }, onSave = { type, date, description, amount, debitId, creditId, postNow ->
            scope.launch {
                try {
                    val docId = repository.createDraftDocument(type, date, description, listOf(
                        DocumentLineEntity(documentId = 0, accountId = debitId, debit = amount, memo = description),
                        DocumentLineEntity(documentId = 0, accountId = creditId, credit = amount, memo = description)
                    ))
                    if (postNow) repository.post(docId)
                    showAddDocument = false
                } catch (e: Exception) {
                    operationError = e.message ?: "حدث خطأ أثناء حفظ المستند"
                }
            }
        })
    }
    if (showAddProperty) {
        AlertDialog(onDismissRequest = { showAddProperty = false }, title = { Text("إضافة عقار", fontWeight = FontWeight.Bold) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = propertyName, onValueChange = { propertyName = it }, label = { Text("اسم العقار") }, singleLine = true)
                OutlinedTextField(value = propertyLocation, onValueChange = { propertyLocation = it }, label = { Text("الموقع (اختياري)") }, singleLine = true)
            }
        }, confirmButton = { TextButton(onClick = {
            if (propertyName.isNotBlank()) scope.launch { dao.insertProperty(PropertyEntity(name = propertyName.trim(), location = propertyLocation.trim())); propertyName = ""; propertyLocation = ""; showAddProperty = false }
        }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = { showAddProperty = false }) { Text("إلغاء") } })
    }
}

@Composable
private fun DocumentDraftDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long, Long, Long, Boolean) -> Unit
) {
    var type by remember { mutableStateOf("قبض") }
    var date by remember { mutableStateOf(AccountingRepository.todayIso()) }
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var debitAccount by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var creditAccount by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: accounts.firstOrNull()?.id ?: 0L) }
    var typeExpanded by remember { mutableStateOf(false) }
    var debitExpanded by remember { mutableStateOf(false) }
    var creditExpanded by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val types = listOf("قبض", "صرف", "شراء", "بيع", "مساهمة", "توزيع أرباح", "قيد يومية", "تسوية")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مستند محاسبي جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Box {
                    OutlinedButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("نوع المستند: $type") }
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) { types.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { type = value; typeExpanded = false }) } }
                }
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("التاريخ YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("البيان") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() } }, label = { Text("المبلغ بالريال اليمني") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { debitExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("حساب المدين: ${accounts.firstOrNull { it.id == debitAccount }?.let { it.code + " - " + it.name } ?: "اختر حسابًا"}") }
                    DropdownMenu(expanded = debitExpanded, onDismissRequest = { debitExpanded = false }) { accounts.forEach { a -> DropdownMenuItem(text = { Text("${a.code} - ${a.name}") }, onClick = { debitAccount = a.id; debitExpanded = false }) } }
                }
                Box {
                    OutlinedButton(onClick = { creditExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("حساب الدائن: ${accounts.firstOrNull { it.id == creditAccount }?.let { it.code + " - " + it.name } ?: "اختر حسابًا"}") }
                    DropdownMenu(expanded = creditExpanded, onDismissRequest = { creditExpanded = false }) { accounts.forEach { a -> DropdownMenuItem(text = { Text("${a.code} - ${a.name}") }, onClick = { creditAccount = a.id; creditExpanded = false }) } }
                }
                if (localError != null) Text(localError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Text("سيُنشأ قيد مزدوج من سطر مدين وسطر دائن متساويين.", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = {
                    val amount = amountText.toLongOrNull()
                    when {
                        accounts.isEmpty() -> localError = "لا توجد حسابات في دليل الحسابات."
                        date.isBlank() || description.isBlank() -> localError = "أدخل التاريخ والبيان."
                        amount == null || amount <= 0L -> localError = "أدخل مبلغًا صحيحًا أكبر من صفر."
                        debitAccount == creditAccount -> localError = "اختر حسابين مختلفين للمدين والدائن."
                        else -> onSave(type, date, description, amount, debitAccount, creditAccount, true)
                    }
                }) { Text("حفظ وترحيل") }
                TextButton(onClick = {
                    val amount = amountText.toLongOrNull()
                    when {
                        accounts.isEmpty() -> localError = "لا توجد حسابات في دليل الحسابات."
                        date.isBlank() || description.isBlank() -> localError = "أدخل التاريخ والبيان."
                        amount == null || amount <= 0L -> localError = "أدخل مبلغًا صحيحًا أكبر من صفر."
                        debitAccount == creditAccount -> localError = "اختر حسابين مختلفين للمدين والدائن."
                        else -> onSave(type, date, description, amount, debitAccount, creditAccount, false)
                    }
                }) { Text("حفظ كمسودة") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable private fun HeroCard(count: Int) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Ink), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ملخص الاستثمار", color = Color(0xFFBFD8E7), fontSize = 13.sp)
                Text("مساحة واحدة لإدارة\nأصولك العقارية", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
                Surface(color = Color(0xFF24455B), shape = RoundedCornerShape(12.dp)) { Text("$count عقار مسجل", color = Color(0xFFD5F3EC), modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 12.sp) }
            }
            Box(Modifier.size(72.dp).background(Color(0xFF234D5A), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Apartment, null, tint = Color(0xFF72D3C1), modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable private fun MetricCard(title: String, value: String, tint: Color, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = tint), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("ريال يمني", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable private fun SectionHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable private fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier, onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(42.dp).background(Color(0xFFE7F5F2), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Teal) }
            Text(label, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable private fun EmptyCard(title: String, message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold)
            Text(message, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable private fun PropertyRow(item: PropertyEntity) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(Color(0xFFE7F5F2), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Apartment, null, tint = Teal) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Ink, fontWeight = FontWeight.Bold)
                Text(item.location.ifBlank { "لم يحدد الموقع" }, color = Muted, fontSize = 12.sp)
            }
            Text("#${item.id}", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable private fun LedgerEntryRow(item: LedgerEntryEntity) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color(0xFFEAF0FA), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MenuBook, null, tint = Color(0xFF456AA0)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.entryNumber, color = Ink, fontWeight = FontWeight.SemiBold)
                Text(item.description, color = Muted, fontSize = 12.sp)
                Text(item.date, color = Muted, fontSize = 11.sp)
            }
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFE5F5F0)) { Text(item.status, color = Teal, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), fontSize = 10.sp) }
        }
    }
}

@Composable private fun JournalRow(item: JournalEntryEntity) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color(0xFFEAF0FA), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF456AA0)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(item.description, color = Ink, fontWeight = FontWeight.SemiBold); Text(item.date, color = Muted, fontSize = 11.sp) }
            Text(money(item.debit), color = Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

private fun money(value: Double): String = NumberFormat.getNumberInstance(Locale("ar", "YE")).apply { maximumFractionDigits = 2 }.format(value)
