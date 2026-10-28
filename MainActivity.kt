package com.example.akhbartechnologia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class NewsItem(val id: String, val title: String, val body: String, val date: String)
data class LectureItem(val id: String, val day: String, val subject: String, val lecturer: String, val time: String, val room: String, val year: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent { AppRoot() }
    }
}

@Composable
fun AppRoot() {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    var tab by remember { mutableStateOf(0) }
    var news by remember { mutableStateOf(listOf<NewsItem>()) }
    var lectures by remember { mutableStateOf(listOf<LectureItem>()) }
    var isAdmin by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var showNewsEditor by remember { mutableStateOf(false) }
    var showLectureEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("جارٍ تحميل المحتوى…") }

    DisposableEffect(Unit) {
        val newsListener = db.collection("news").orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) { message = "تعذر تحميل الأخبار. تأكد من إعداد Firebase."; return@addSnapshotListener }
                news = snap?.documents?.map {
                    NewsItem(it.id, it.getString("title") ?: "", it.getString("body") ?: "", it.getString("date") ?: "")
                } ?: emptyList()
                message = if (news.isEmpty()) "لا توجد أخبار منشورة حاليًا." else ""
            }
        val lectureListener = db.collection("lectures").orderBy("dayOrder").orderBy("startMinutes")
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                lectures = snap?.documents?.map {
                    LectureItem(it.id, it.getString("day") ?: "", it.getString("subject") ?: "",
                        it.getString("lecturer") ?: "", it.getString("time") ?: "",
                        it.getString("room") ?: "", it.getString("year") ?: "كل الفرق")
                } ?: emptyList()
            }
        val authListener = auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) isAdmin = false
            else db.collection("users").document(user.uid).get()
                .addOnSuccessListener { isAdmin = it.getString("role") == "admin" }
                .addOnFailureListener { isAdmin = false }
        }
        onDispose { newsListener.remove(); lectureListener.remove(); auth.removeAuthStateListener(authListener) }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF123C69),
        secondary = androidx.compose.ui.graphics.Color(0xFF2A9D8F))) {
        Scaffold(
            topBar = {
                TopAppBarCompat(
                    title = "أخبار تكنولوجيا بورسعيد",
                    admin = isAdmin,
                    onLogin = { showLogin = true },
                    onLogout = { auth.signOut(); isAdmin = false }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Campaign, null) }, label = { Text("الأخبار") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("المحاضرات") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.AdminPanelSettings, null) }, label = { Text("الإدارة") })
                }
            },
            floatingActionButton = {
                if (isAdmin && tab == 0) FloatingActionButton(onClick = { showNewsEditor = true }) { Icon(Icons.Default.Add, "إضافة خبر") }
                if (isAdmin && tab == 1) FloatingActionButton(onClick = { showLectureEditor = true }) { Icon(Icons.Default.Add, "إضافة محاضرة") }
            }
        ) { padding ->
            when (tab) {
                0 -> NewsScreen(news, message, Modifier.padding(padding))
                1 -> LectureScreen(lectures, Modifier.padding(padding))
                else -> AdminScreen(isAdmin, onLogin = { showLogin = true },
                    onNews = { tab = 0; showNewsEditor = true },
                    onLecture = { tab = 1; showLectureEditor = true },
                    modifier = Modifier.padding(padding))
            }
        }
    }

    if (showLogin) LoginDialog(
        onDismiss = { showLogin = false },
        onLogin = { email, password ->
            auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener { showLogin = false }
                .addOnFailureListener { message = "تعذر تسجيل الدخول. راجع البريد وكلمة المرور." }
        }
    )
    if (showNewsEditor && isAdmin) NewsEditor(
        onDismiss = { showNewsEditor = false },
        onSave = { title, body ->
            db.collection("news").add(hashMapOf(
                "title" to title, "body" to body, "date" to java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale("ar")).format(java.util.Date()),
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )).addOnFailureListener { message = "فشل النشر. تحقق من صلاحيات قاعدة البيانات." }
            showNewsEditor = false
        }
    )
    if (showLectureEditor && isAdmin) LectureEditor(
        onDismiss = { showLectureEditor = false },
        onSave = { day, subject, lecturer, time, room, year ->
            val dayOrder = listOf("السبت","الأحد","الإثنين","الثلاثاء","الأربعاء","الخميس","الجمعة").indexOf(day).coerceAtLeast(0)
            val startMinutes = time.take(2).toIntOrNull()?.times(60)?.plus(time.drop(3).take(2).toIntOrNull() ?: 0) ?: 0
            db.collection("lectures").add(hashMapOf(
                "day" to day, "dayOrder" to dayOrder, "subject" to subject, "lecturer" to lecturer,
                "time" to time, "startMinutes" to startMinutes, "room" to room, "year" to year
            )).addOnFailureListener { message = "فشل حفظ المحاضرة. تحقق من الصلاحيات." }
            showLectureEditor = false
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarCompat(title: String, admin: Boolean, onLogin: () -> Unit, onLogout: () -> Unit) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) }, actions = {
        if (admin) IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "خروج") }
        else IconButton(onClick = onLogin) { Icon(Icons.Default.Login, "دخول المدير") }
    })
}

@Composable
fun NewsScreen(news: List<NewsItem>, message: String, modifier: Modifier = Modifier) {
    if (news.isEmpty()) Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message.ifBlank { "لا توجد أخبار منشورة حاليًا." })
    } else LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(news, key = { it.id }) { item ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (item.date.isNotBlank()) Text(item.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(item.body)
                }
            }
        }
    }
}

@Composable
fun LectureScreen(lectures: List<LectureItem>, modifier: Modifier = Modifier) {
    if (lectures.isEmpty()) Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("لم يتم نشر جدول المحاضرات بعد.")
    } else LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lectures, key = { it.id }) { l ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${l.day} • ${l.time}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(l.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("الدكتور: ${l.lecturer.ifBlank { "غير محدد" }}")
                    Text("القاعة: ${l.room.ifBlank { "غير محددة" }}  |  الفرقة: ${l.year}")
                }
            }
        }
    }
}

@Composable
fun AdminScreen(isAdmin: Boolean, onLogin: () -> Unit, onNews: () -> Unit, onLecture: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("لوحة الإدارة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!isAdmin) {
            Text("الدخول مخصص لحسابات المديرين المعتمدة فقط.")
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("تسجيل دخول المدير") }
        } else {
            Text("أنت مسجل كمدير. اختر العملية المطلوبة:")
            Button(onClick = onNews, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PostAdd, null); Spacer(Modifier.width(8.dp)); Text("إضافة خبر") }
            Button(onClick = onLecture, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("إضافة محاضرة") }
            Text("يمكن تعديل أو حذف السجلات من خلال لوحة الإدارة القادمة؛ لا تعتمد على هذا النموذج لإدارة بيانات فعلية قبل إكمال هذه الوظائف.")
        }
    }
}

@Composable
fun LoginDialog(onDismiss: () -> Unit, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("دخول المدير") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(email, { email = it }, label = { Text("البريد الإلكتروني") }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onLogin(email, password) }) { Text("دخول") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
fun NewsEditor(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة خبر") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("عنوان الخبر") })
            OutlinedTextField(body, { body = it }, label = { Text("تفاصيل الخبر") }, minLines = 3)
        } },
        confirmButton = { TextButton(enabled = title.isNotBlank() && body.isNotBlank(), onClick = { onSave(title.trim(), body.trim()) }) { Text("نشر") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
fun LectureEditor(onDismiss: () -> Unit, onSave: (String, String, String, String, String, String) -> Unit) {
    var day by remember { mutableStateOf("الأحد") }
    var subject by remember { mutableStateOf("") }
    var lecturer by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("09:00") }
    var room by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("الفرقة الثانية") }
    val days = listOf("السبت","الأحد","الإثنين","الثلاثاء","الأربعاء","الخميس","الجمعة")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة محاضرة") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(day, { }, readOnly = true, label = { Text("اليوم") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    days.forEach { d -> DropdownMenuItem(text = { Text(d) }, onClick = { day = d; expanded = false }) }
                }
            }
            OutlinedTextField(subject, { subject = it }, label = { Text("المادة") }, singleLine = true)
            OutlinedTextField(lecturer, { lecturer = it }, label = { Text("الدكتور") }, singleLine = true)
            OutlinedTextField(time, { time = it }, label = { Text("الوقت (مثال 09:30)") }, singleLine = true)
            OutlinedTextField(room, { room = it }, label = { Text("القاعة") }, singleLine = true)
            OutlinedTextField(year, { year = it }, label = { Text("الفرقة الدراسية") }, singleLine = true)
        } },
        confirmButton = { TextButton(enabled = subject.isNotBlank(), onClick = { onSave(day, subject.trim(), lecturer.trim(), time.trim(), room.trim(), year.trim()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}
