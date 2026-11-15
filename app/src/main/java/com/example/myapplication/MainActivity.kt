package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

// ================= МОДЕЛИ ДАННЫХ =================

data class ModelInfo(
    val article: String,
    val brand: String,
    val color: String,
    val upper: String,
    val lining: String,
    val insole: String,
    val outsole: String,
    val pairsPerBox: String,
    val sizeBoxes: String,
    val sizeBoxesCount: Int,
    val mixedBoxes: String,
    val mixedBoxesCount: String,
    val comments: String
)

data class Order(
    val id: String,
    val internalFolderName: String = "",
    val factory: String,
    val format: String,
    val deliveryNum: String,
    val date: String,
    val models: List<ModelInfo>
)

// ================= ВСПОМОГАТЕЛЬНЫЕ МОДИФИКАТОРЫ =================

fun Modifier.safeClickable(onClick: () -> Unit) = composed {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("QC_PREFS", Context.MODE_PRIVATE) }
    val currentOnClick by rememberUpdatedState(onClick)
    var lastAcceptedAt by remember { mutableLongStateOf(0L) }
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val up = waitForUpOrCancellation()
            if (up != null) {
                val now = android.os.SystemClock.uptimeMillis()
                if (shouldAcceptProtectedClick(getTouchProtectionMode(sharedPrefs), lastAcceptedAt, now)) {
                    lastAcceptedAt = now
                    currentOnClick()
                }
            }
        }
    }
}

fun Modifier.safeCombinedClickable(onTap: () -> Unit, onLongPress: () -> Unit) = composed {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("QC_PREFS", Context.MODE_PRIVATE) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    var lastAcceptedAt by remember { mutableLongStateOf(0L) }
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
            var isLongPress = false
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                kotlinx.coroutines.delay(longPressTimeout)
                isLongPress = true
                currentOnLongPress()
            }
            val up = waitForUpOrCancellation()
            job.cancel()
            if (up != null && !isLongPress) {
                val now = android.os.SystemClock.uptimeMillis()
                if (shouldAcceptProtectedClick(getTouchProtectionMode(sharedPrefs), lastAcceptedAt, now)) {
                    lastAcceptedAt = now
                    currentOnTap()
                }
            }
        }
    }
}

// ================= ЛОГИКА ПРОВЕРКИ ТЕСТА =================

fun shouldSuggestTest(model: ModelInfo): Boolean {
    val lowerLining = model.lining.lowercase()
    val lowerInsole = model.insole.lowercase()

    val positiveKeywords = listOf("текстиль", "войлок", "шерсть", "шерстяной", "мех", "натуральн")
    val negativeKeywords = listOf("синтетическ", "искусственн")

    // Проверяем подкладку
    val liningHasPositive = positiveKeywords.any { lowerLining.contains(it) }
    val liningHasNegative = negativeKeywords.any { lowerLining.contains(it) }
    val isLiningTestable = liningHasPositive && !liningHasNegative

    // Проверяем стельку
    val insoleHasPositive = positiveKeywords.any { lowerInsole.contains(it) }
    val insoleHasNegative = negativeKeywords.any { lowerInsole.contains(it) }
    val isInsoleTestable = insoleHasPositive && !insoleHasNegative

    // Достаточно, чтобы тестовый материал был ХОТЯ БЫ В ОДНОМ из них
    val hasTestableMaterial = isLiningTestable || isInsoleTestable

    // Проверка размеров
    val sizes = model.sizeBoxes.split(";").mapNotNull { it.substringBefore("-").trim().toIntOrNull() }
    val hasSmallSizes = sizes.any { it < 27 }

    return hasTestableMaterial && hasSmallSizes
}

// ================= TELEGRAM-STYLE CHECKBOX =================

@Composable
fun TelegramCheckbox(
    checked: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.6f,
        animationSpec = tween(durationMillis = 150),
        label = "checkboxScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "checkboxAlpha"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Checkbox) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFF3D8DFF) else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (checked) Color(0xFF3D8DFF) else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            )
        }
    }
}

// ================= BOTTOM SHEET =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                if (title != null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                content()
            }
        }
    }
}

@Composable
fun SheetMenuItem(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .safeClickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

// ================= ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ =================

data class SavedOrdersResult(
    val orders: List<Order> = emptyList(),
    val corruptedJson: String? = null
)

internal fun decodeSavedOrders(json: String): List<Order> {
    val type = object : com.google.gson.reflect.TypeToken<List<Order>>() {}.type
    val loaded: List<Order> = Gson().fromJson(json, type) ?: emptyList()
    return loaded.map { order ->
        if (order.internalFolderName.isBlank()) {
            order.copy(internalFolderName = "${order.factory}_${order.format}_${order.deliveryNum}_${order.date.replace(".", "_")}")
        } else order
    }
}

fun loadSavedOrders(sharedPrefs: android.content.SharedPreferences): SavedOrdersResult {
    val json = try {
        sharedPrefs.getString("saved_orders", null)
    } catch (e: Exception) {
        return SavedOrdersResult(
            corruptedJson = sharedPrefs.all["saved_orders"]?.toString()
                ?: "saved_orders имеет неподдерживаемый тип"
        )
    } ?: return SavedOrdersResult()
    return try {
        SavedOrdersResult(orders = decodeSavedOrders(json))
    } catch (e: Exception) {
        e.printStackTrace()
        SavedOrdersResult(corruptedJson = json)
    }
}

fun getOrderStats(order: Order, sharedPrefs: android.content.SharedPreferences): Triple<Int, Int, Int> {
    var accepted = 0
    var rejected = 0
    var rework = 0
    order.models.forEach { model ->
        val key = ModelProgressStore.key(order.id, model.article, ModelProgressStore.DECISION)
        when (sharedPrefs.getString(key, "")) {
            "ПРИНЯТО" -> accepted++
            "НЕ ПРИНЯТО", "НЕ ГОТОВО" -> rejected++
            "ДОРАБОТКА" -> rework++
        }
    }
    return Triple(accepted, rejected, rework)
}

fun getOverdueDays(dateStr: String): Int {
    return try {
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        fmt.isLenient = false
        val readyDate = fmt.parse(dateStr) ?: return 0
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val diff = today.time - readyDate.time
        if (diff <= 0) 0 else (diff / (1000 * 60 * 60 * 24)).toInt()
    } catch (e: Exception) { 0 }
}

internal fun mondayFirstDayOffset(year: Int, monthZeroBased: Int): Int =
    LocalDate.of(year, monthZeroBased + 1, 1).dayOfWeek.value - 1

internal fun searchModelsByArticle(
    orders: List<Order>,
    query: String,
    selectedFactories: Set<String>
): List<Pair<Order, ModelInfo>> = orders
    .filter { selectedFactories.isEmpty() || it.factory in selectedFactories }
    .flatMap { order ->
        order.models.filter { it.article.contains(query, ignoreCase = true) }.map { order to it }
    }

fun normalizeArticle(article: String): String =
    article.trim()
        .replace("/", "-")
        .replace(" ", "-")
        .replace(Regex("-+"), "-")
        .lowercase()

fun macroFileBaseName(fileName: String): String? {
    if (fileName.isEmpty()) return null
    val noExt = fileName.substringBeforeLast(".")
    if (noExt.startsWith("extra_", true)) return null
    var base = noExt.trim().lowercase()
    val parts = base.split("_")
    if (parts.size > 1 && parts.last().all { it.isDigit() }) {
        base = parts.dropLast(1).joinToString("_")
    }
    return base
        .replace("/", "-")
        .replace(" ", "-")
        .replace(Regex("-+"), "-")
}

fun countModelsWithoutMacros(context: Context, folderUri: String, order: Order): Int {
    return try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        val orderFolder = tree?.findFile(order.internalFolderName) ?: return order.models.size
        val macroNames = mutableSetOf<String>()
        orderFolder.listFiles().forEach { file ->
            val name = file.name ?: return@forEach
            if (name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".jpeg", true)) {
                macroFileBaseName(name)?.let { macroNames.add(it) }
            }
        }
        order.models.count { normalizeArticle(it.article) !in macroNames }
    } catch (e: Exception) { order.models.size }
}

fun extractDeliveryNum(cleanName: String): String {
    val regex = Regex("(\\d+)[\\s_]*ООО", RegexOption.IGNORE_CASE)
    val match = regex.find(cleanName)
    if (match != null) return match.groupValues[1]
    val parts = cleanName.split("_")
    for (i in 1 until parts.size) {
        if (parts[i].all { it.isDigit() } && parts[i].isNotEmpty()) return parts[i]
    }
    return if (parts.size > 1) parts[1] else "?"
}

fun extractFormat(cleanName: String): String {
    val lower = cleanName.lowercase()
    return when {
        lower.contains("джаззз") -> "MEGA"
        lower.contains("киберселл") -> "BEST"
        lower.contains("лебартово") -> "BEST"
        lower.contains("фэшн") || lower.contains("ритейл") || lower.contains("retail") -> "STAR"
        else -> "UNKNOWN"
    }
}

fun findCommentsColumnIndex(sheet: org.apache.poi.ss.usermodel.Sheet): Int {
    val maxScan = minOf(sheet.lastRowNum, 10)
    for (rowIdx in 0..maxScan) {
        val row = sheet.getRow(rowIdx) ?: continue
        for (cellIdx in 0 until row.lastCellNum) {
            val cell = row.getCell(cellIdx) ?: continue
            val text = cell.toString().lowercase()
            if (text.contains("комментари") || text.contains("备注")) {
                return cellIdx
            }
        }
    }
    return -1
}

fun getDocumentDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
} catch (e: Exception) {
    null
}

// ================= ГЛАВНЫЙ КЛАСС =================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.25)
                        .build()
                }
                .build()
        }

        setContent {
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("QC_PREFS", Context.MODE_PRIVATE) }
            val themeMode = remember { mutableStateOf(sharedPrefs.getString("theme_mode", "system") ?: "system") }

            val darkTheme = when (themeMode.value) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            AppTheme(darkTheme = darkTheme) {
                val navigationBarColor = MaterialTheme.colorScheme.surface.toArgb()
                SideEffect {
                    window.navigationBarColor = navigationBarColor
                    WindowInsetsControllerCompat(window, window.decorView)
                        .isAppearanceLightNavigationBars = !darkTheme
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        themeMode = themeMode.value,
                        onThemeModeChange = {
                            themeMode.value = it
                            sharedPrefs.edit().putString("theme_mode", it).apply()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    themeMode: String,
    onThemeModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("QC_PREFS", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()

    var orders by remember { mutableStateOf(emptyList<Order>()) }
    var ordersLoaded by remember { mutableStateOf(false) }
    var ordersLoadFailed by remember { mutableStateOf(false) }
    var ordersLoadAttempt by remember { mutableIntStateOf(0) }
    val saveOrdersMutex = remember { Mutex() }
    val savedFolderUri = remember { sharedPrefs.getString("images_folder_uri", null) }
    var imagesFolderUri by remember {
        mutableStateOf(savedFolderUri?.takeIf { hasPersistedFolderPermission(context, it) })
    }
    var selectedTab by remember { mutableStateOf(0) }

    val imagesCache = remember { mutableStateMapOf<String, List<Uri>>() }
    var isAutoScanning by remember { mutableStateOf(false) }

    var selectedOrderId by remember { mutableStateOf<String?>(null) }
    var selectedModelArticle by remember { mutableStateOf<String?>(null) }

    var calendarSelectedOrderId by remember { mutableStateOf<String?>(null) }
    var calendarSelectedDate by remember { mutableStateOf("") }

    var modelSortOption by remember { mutableStateOf("") }
    var modelSelectedDate by remember { mutableStateOf("") }
    var orderSearchQuery by remember { mutableStateOf("") }
    var orderSearchType by remember { mutableStateOf("По заказу") }

    var modelSearchQuery by remember { mutableStateOf("") }
    var cameFromArticleSearch by remember { mutableStateOf(false) }

    val orderListState = rememberLazyListState()
    val modelListState = rememberLazyListState()

    LaunchedEffect(ordersLoadAttempt) {
        ordersLoaded = false
        ordersLoadFailed = false
        val result = withContext(Dispatchers.IO) { loadSavedOrders(sharedPrefs) }
        if (result.corruptedJson != null) {
            withContext(Dispatchers.IO) {
                if (!sharedPrefs.contains("saved_orders_corrupt_backup")) {
                    sharedPrefs.edit().putString("saved_orders_corrupt_backup", result.corruptedJson).commit()
                }
            }
            ordersLoadFailed = true
        } else {
            withContext(Dispatchers.IO) { ModelProgressStore.migrateLegacyData(sharedPrefs, result.orders) }
            orders = result.orders
            ordersLoaded = true
        }
    }

    LaunchedEffect(savedFolderUri) {
        if (savedFolderUri != null && imagesFolderUri == null) {
            Toast.makeText(context, "Доступ к сохранённой папке нужно выдать повторно", Toast.LENGTH_LONG).show()
        }
    }

    val selectedOrder: Order? = remember(orders, selectedOrderId) {
        selectedOrderId?.let { id -> orders.find { it.id == id } }
    }
    val selectedModel: ModelInfo? = remember(selectedOrder, selectedModelArticle) {
        val article = selectedModelArticle ?: return@remember null
        selectedOrder?.models?.find { it.article == article }
    }

    val calendarOrder: Order? = remember(orders, calendarSelectedOrderId) {
        calendarSelectedOrderId?.let { id -> orders.find { it.id == id } }
    }

    fun saveOrders(newOrders: List<Order>) {
        scope.launch {
            saveOrdersMutex.withLock {
                val saved = withContext(Dispatchers.IO) {
                    try {
                        val json = gson.toJson(newOrders)
                        ModelProgressStore.migrateLegacyData(sharedPrefs, newOrders)
                        sharedPrefs.edit().putString("saved_orders", json).commit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                if (saved) {
                    orders = newOrders
                } else {
                    Toast.makeText(context, "Не удалось сохранить изменения заказов", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun autoScan() {
        val currentUri = imagesFolderUri
        if (currentUri == null || isAutoScanning || !ordersLoaded) return

        isAutoScanning = true
        scope.launch(Dispatchers.IO) {
            try {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(currentUri))
                    ?: throw SecurityException("Папка недоступна")
                val existingOrderIds = orders.mapTo(mutableSetOf()) { it.id }
                val excelFiles = tree.listFiles().filter { file ->
                    val name = file.name
                    name != null && name !in existingOrderIds &&
                        (name.endsWith(".xlsx", true) || name.endsWith(".xls", true))
                }

                val newOrdersList = orders.toMutableList()
                var addedCount = 0
                var folderErrors = 0

                for (file in excelFiles) {
                    val order = parseExcel(context.contentResolver.openInputStream(file.uri), file.name ?: "")
                    if (order != null && newOrdersList.none { it.id == order.id }) {
                        newOrdersList.add(order)
                        if (!createOrderFolderSAF(context, currentUri, order.internalFolderName)) folderErrors++
                        addedCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    if (addedCount > 0) {
                        saveOrders(newOrdersList)
                        Toast.makeText(context, "Авто-обновление: добавлено $addedCount заказов", Toast.LENGTH_SHORT).show()
                    }
                    if (folderErrors > 0) {
                        Toast.makeText(context, "Не удалось создать папки для $folderErrors заказов", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Не удалось обновить заказы. Проверьте доступ к папке", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { isAutoScanning = false }
            }
        }
    }

    LaunchedEffect(selectedTab, imagesFolderUri, ordersLoaded) {
        if (selectedTab == 0 && imagesFolderUri != null && ordersLoaded) {
            autoScan()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                imagesFolderUri = uri.toString()
                sharedPrefs.edit().putString("images_folder_uri", uri.toString()).apply()
                imagesCache.clear()
                Toast.makeText(context, "Папка выбрана!", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(context, "Не удалось получить постоянный доступ к папке", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (imagesFolderUri == null && ordersLoaded) {
        AlertDialog(
            onDismissRequest = { },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Выбор папки", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Выберите папку, куда вы будете складывать Excel-файлы и макеты.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { Button(onClick = { folderPickerLauncher.launch(null) }, shape = RoundedCornerShape(16.dp)) { Text("Выбрать", style = MaterialTheme.typography.labelLarge) } }
        )
    }

    if (!ordersLoaded && !ordersLoadFailed) {
        Dialog(onDismissRequest = { }) {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Загрузка сохранённых заказов…")
                }
            }
        }
    }

    if (ordersLoadFailed) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Ошибка сохранённых данных") },
            text = { Text("Список заказов повреждён. Его исходная копия сохранена для диагностики; данные не были перезаписаны. Повторите загрузку после восстановления данных.") },
            confirmButton = {
                TextButton(onClick = { ordersLoadAttempt++ }) { Text("Повторить") }
            }
        )
    }

    BackHandler(enabled = selectedTab == 2 && calendarSelectedOrderId != null) {
        calendarSelectedOrderId = null
        selectedModelArticle = null
    }

    BackHandler(enabled = selectedTab == 0 && selectedModel != null) {
        selectedModelArticle = null
        if (cameFromArticleSearch) {
            selectedOrderId = null
            cameFromArticleSearch = false
        }
    }
    BackHandler(enabled = selectedTab == 0 && selectedModel == null && selectedOrder != null) {
        selectedOrderId = null
        modelSortOption = ""
        modelSelectedDate = ""
        modelSearchQuery = ""
        imagesCache.clear()
    }

    var selectionModeActive by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!selectionModeActive) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    modifier = Modifier.heightIn(min = 56.dp)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            selectedOrderId = null
                            selectedModelArticle = null
                            calendarSelectedOrderId = null
                            imagesCache.clear()
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Заказы", style = MaterialTheme.typography.labelSmall) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Макеты", style = MaterialTheme.typography.labelSmall) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Календарь", style = MaterialTheme.typography.labelSmall) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Настройки", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> {
                    when {
                        selectedOrder == null -> OrderListScreen(
                            orders, { selectedOrderId = it.id; cameFromArticleSearch = false }, { saveOrders(it) }, { saveOrders(emptyList()) },
                            imagesFolderUri, isAutoScanning,
                            listState = orderListState,
                            sharedPrefs = sharedPrefs,
                            refreshAction = { autoScan() },
                            searchQuery = orderSearchQuery,
                            onSearchQueryChange = { orderSearchQuery = it },
                            searchType = orderSearchType,
                            onSearchTypeChange = { orderSearchType = it },
                            onModelClick = { orderId, modelArt ->
                                selectedOrderId = orderId
                                selectedModelArticle = modelArt
                                cameFromArticleSearch = true
                            }
                        )
                        selectedModel == null -> ModelListScreen(
                            selectedOrder!!,
                            { article ->
                                sharedPrefs.getString(ModelProgressStore.key(selectedOrder!!.id, article, ModelProgressStore.DECISION), "") ?: ""
                            },
                            { article ->
                                sharedPrefs.getString(ModelProgressStore.key(selectedOrder!!.id, article, ModelProgressStore.DATE), "") ?: ""
                            },
                            { article ->
                                sharedPrefs.getString(ModelProgressStore.key(selectedOrder!!.id, article, ModelProgressStore.TEST_DATE), "") ?: ""
                            },
                            { article ->
                                sharedPrefs.getString(ModelProgressStore.key(selectedOrder!!.id, article, ModelProgressStore.TEST_RECEIVED_DATE), "") ?: ""
                            },
                            onBack = {
                                selectedOrderId = null
                                modelSortOption = ""
                                modelSelectedDate = ""
                                modelSearchQuery = ""
                                imagesCache.clear()
                            },
                            onModelClick = { selectedModelArticle = it.article; cameFromArticleSearch = false },
                            onOrdersUpdated = { saveOrders(it) },
                            allOrders = orders,
                            imagesFolderUri = imagesFolderUri,
                            imagesCache = imagesCache,
                            listState = modelListState,
                            sharedPrefs = sharedPrefs,
                            sortOption = modelSortOption,
                            onSortOptionChange = { modelSortOption = it },
                            selectedDate = modelSelectedDate,
                            onSelectedDateChange = { modelSelectedDate = it },
                            searchQuery = modelSearchQuery,
                            onSearchQueryChange = { modelSearchQuery = it },
                            onSelectionModeChange = { selectionModeActive = it }
                        )
                        else -> {
                            InspectionScreen(
                                selectedModel!!,
                                onBack = {
                                    selectedModelArticle = null
                                    if (cameFromArticleSearch) {
                                        selectedOrderId = null
                                        cameFromArticleSearch = false
                                    }
                                },
                                sharedPrefs,
                                selectedOrder!!,
                                imagesFolderUri,
                                imagesCache
                            )
                        }
                    }
                }
                1 -> GalleryTab(orders, imagesFolderUri, imagesCache)
                2 -> {
                    if (calendarOrder == null) {
                        CalendarScreen(
                            orders = orders,
                            sharedPrefs = sharedPrefs,
                            selectedDate = calendarSelectedDate,
                            onDateSelected = { calendarSelectedDate = it },
                            onModelClick = { orderId, article ->
                                calendarSelectedOrderId = orderId
                                selectedModelArticle = article
                            }
                        )
                    } else {
                        val calModel = calendarOrder.models.find { it.article == selectedModelArticle }
                        if (calModel != null) {
                            InspectionScreen(
                                calModel,
                                onBack = {
                                    calendarSelectedOrderId = null
                                    selectedModelArticle = null
                                },
                                sharedPrefs,
                                calendarOrder,
                                imagesFolderUri,
                                imagesCache
                            )
                        }
                    }
                }
                3 -> SettingsTab(
                    imagesFolderUri,
                    { folderPickerLauncher.launch(null) },
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
        }
    }
}

// ================= ВКЛАДКА 1: МАКЕТЫ =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryTab(
    orders: List<Order>,
    imagesFolderUri: String?,
    imagesCache: MutableMap<String, List<Uri>>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedOrderForGallery by remember { mutableStateOf<Order?>(null) }
    var showAllOrders by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val unlinkedCounts = remember { mutableStateMapOf<String, Int>() }
    var isInitialLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    fun refreshCounts() {
        val uri = imagesFolderUri ?: run {
            isInitialLoading = false
            return
        }
        scope.launch(Dispatchers.IO) {
            val result = mutableMapOf<String, Int>()
            orders.forEach { order ->
                val count = countModelsWithoutMacros(context, uri, order)
                result[order.id] = count
            }
            withContext(Dispatchers.Main) {
                unlinkedCounts.clear()
                unlinkedCounts.putAll(result)
                isInitialLoading = false
                refreshTrigger++
            }
        }
    }

    LaunchedEffect(orders, imagesFolderUri) {
        refreshCounts()
    }

    BackHandler(enabled = selectedOrderForGallery != null) { selectedOrderForGallery = null }

    if (selectedOrderForGallery != null) {
        GalleryDetailScreen(
            order = selectedOrderForGallery!!,
            imagesFolderUri = imagesFolderUri,
            onBack = {
                selectedOrderForGallery = null
                refreshCounts()
            }
        )
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Макеты", style = MaterialTheme.typography.titleLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    actions = {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    }
                )
            }
        ) { paddingValues ->
            AppBottomSheet(
                visible = showMenu,
                onDismiss = { showMenu = false },
                title = "Настройки списка"
            ) {
                SheetMenuItem(
                    text = if (showAllOrders) "Только без макетов" else "Показать все заказы",
                    icon = Icons.Default.Visibility,
                    onClick = { showAllOrders = !showAllOrders; showMenu = false }
                )
                SheetMenuItem(
                    text = "Отмена",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = Icons.Default.Close,
                    onClick = { showMenu = false }
                )
            }

            val displayOrders = when {
                showAllOrders -> orders
                isInitialLoading -> orders
                else -> {
                    refreshTrigger
                    orders.filter { (unlinkedCounts[it.id] ?: 0) > 0 }
                }
            }

            if (orders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Нет заказов. Загрузите Excel.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (isInitialLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (displayOrders.isEmpty() && !showAllOrders) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Все заказы имеют макеты 🎉", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showAllOrders = true }) {
                            Text("Показать все заказы", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayOrders, key = { it.id }) { order ->
                        val unlinked = unlinkedCounts[order.id] ?: 0
                        Card(
                            modifier = Modifier.fillMaxWidth().safeClickable { selectedOrderForGallery = order },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(order.factory, style = MaterialTheme.typography.titleSmall)
                                        Text("${order.format} ${order.deliveryNum}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("Дата: ${order.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Моделей: ${order.models.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (unlinked > 0) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFFFF3E0), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "Без макетов: $unlinked",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFE8F5E9), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "Все с макетами",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class FolderImage(
    val uri: Uri,
    val name: String,
    val normalizedBase: String?
)

data class GalleryImageItem(
    val uri: Uri,
    val isOrphan: Boolean,
    val normalizedBase: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryDetailScreen(
    order: Order,
    imagesFolderUri: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var sortedImages by remember { mutableStateOf<List<GalleryImageItem>>(emptyList()) }
    var fullScreenImage by remember { mutableStateOf<Uri?>(null) }
    var imageToDelete by remember { mutableStateOf<Uri?>(null) }

    var orphanActionImage by remember { mutableStateOf<Uri?>(null) }
    var showModelListForLink by remember { mutableStateOf(false) }
    var modelToManualAttach by remember { mutableStateOf<ModelInfo?>(null) }

    var isLoading by remember { mutableStateOf(true) }
    var modelsWithoutImages by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }

    val manualPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val targetModel = modelToManualAttach
        val folderUri = imagesFolderUri
        if (uri != null && targetModel != null && folderUri != null) {
            modelToManualAttach = null
            scope.launch(Dispatchers.IO) {
                val articleName = targetModel.article.replace("/", "-").replace(" ", "-")
                val orderFolder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                    ?.findFile(order.internalFolderName)
                val newFile = orderFolder?.createFile("image/jpeg", "$articleName.jpg")
                val copied = newFile != null && copyDocumentContent(context, uri, newFile)
                if (copied) {
                    orderFolder?.listFiles()?.forEach { file ->
                        val base = file.name?.let(::macroFileBaseName)
                        if (file.uri != newFile?.uri && base == normalizeArticle(articleName)) file.delete()
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (copied) "Фото привязано" else "Не удалось сохранить фото",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (copied) isLoading = true
                }
            }
        }
    }

    fun attachExistingOrphanToModel(targetModel: ModelInfo) {
        val srcUri = orphanActionImage ?: return
        val uri = imagesFolderUri ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val orderFolder = DocumentFile.fromTreeUri(context, Uri.parse(uri))
                    ?.findFile(order.internalFolderName)
                val articleName = targetModel.article.replace("/", "-").replace(" ", "-")
                val newFile = orderFolder?.createFile("image/jpeg", "$articleName.jpg")
                val copied = newFile != null && copyDocumentContent(context, srcUri, newFile)
                if (copied) {
                    orderFolder?.listFiles()?.forEach { file ->
                        val base = file.name?.let(::macroFileBaseName)
                        if (file.uri != newFile?.uri && file.uri != srcUri && base == normalizeArticle(articleName)) {
                            file.delete()
                        }
                    }
                    val sourceDeleted = deleteDocument(context, srcUri)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (sourceDeleted) "Привязано к ${targetModel.article}" else "Фото привязано, исходный файл не удалён",
                            Toast.LENGTH_SHORT
                        ).show()
                        orphanActionImage = null
                        isLoading = true
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Ошибка привязки", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка привязки", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val folderUri = imagesFolderUri
        if (uris.isNotEmpty() && folderUri != null) {
            scope.launch(Dispatchers.IO) {
                val orderFolder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                    ?.findFile(order.internalFolderName)
                var addedCount = 0
                uris.forEach { uri ->
                    val fileName = "extra_gallery_${System.currentTimeMillis()}_${(0..999).random()}.jpg"
                    val newFile = orderFolder?.createFile("image/jpeg", fileName)
                    if (newFile != null && copyDocumentContent(context, uri, newFile)) addedCount++
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (addedCount == uris.size) "Добавлено фото: $addedCount" else "Добавлено $addedCount из ${uris.size} фото",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (addedCount > 0) isLoading = true
                }
            }
        }
    }

    LaunchedEffect(imagesFolderUri, order.internalFolderName, isLoading) {
        if (imagesFolderUri != null && isLoading) {
            val (loadedImages, loadedSortedImages, loadedModelsWithoutImages) = withContext(Dispatchers.IO) {
                val images = loadAllImagesFromFolder(context, imagesFolderUri, order)
                val normalizedArticles = order.models.mapTo(mutableSetOf()) { normalizeArticle(it.article) }
                val sorted = images.sortedByDescending { image ->
                    image.normalizedBase == null || image.normalizedBase !in normalizedArticles
                }.map { image ->
                    val base = image.normalizedBase
                    val isLinked = base != null && base in normalizedArticles
                    GalleryImageItem(image.uri, isOrphan = !isLinked && base != null, normalizedBase = base)
                }
                val missing = getModelsWithoutImages(order, images.mapNotNullTo(mutableSetOf()) { it.normalizedBase })
                Triple(images.map { it.uri }, sorted, missing)
            }
            allImages = loadedImages
            sortedImages = loadedSortedImages
            modelsWithoutImages = loadedModelsWithoutImages
            isLoading = false
        }
    }

    AppBottomSheet(
        visible = imageToDelete != null,
        onDismiss = { imageToDelete = null },
        title = "Удалить макет?"
    ) {
        Text(
            "Вы уверены, что хотите удалить эту фотографию?",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HoldToConfirmButton(
            actionText = "Удалить",
            onConfirm = {
                val target = imageToDelete ?: return@HoldToConfirmButton
                val remainingImages = allImages.filter { it != target }
                val remainingItems = sortedImages.filter { it.uri != target }
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) { deleteDocument(context, target) }
                    if (deleted) {
                        allImages = remainingImages
                        sortedImages = remainingItems
                        modelsWithoutImages = getModelsWithoutImages(
                            order,
                            remainingItems.mapNotNullTo(mutableSetOf()) { it.normalizedBase }
                        )
                        imageToDelete = null
                        Toast.makeText(context, "Фото удалено", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Не удалось удалить фото", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        )
        SheetMenuItem(
            text = "Отмена",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Default.Close,
            onClick = { imageToDelete = null }
        )
    }

    AppBottomSheet(
        visible = orphanActionImage != null && !showModelListForLink,
        onDismiss = { orphanActionImage = null },
        title = "Непривязанный макет"
    ) {
        Text(
            "Это фото не привязано ни к одной модели.",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SheetMenuItem(
            text = "Посмотреть",
            icon = Icons.Default.Visibility,
            onClick = {
                fullScreenImage = orphanActionImage
                orphanActionImage = null
            }
        )
        SheetMenuItem(
            text = "Привязать к модели",
            icon = Icons.Default.Link,
            onClick = { showModelListForLink = true }
        )
        SheetMenuItem(
            text = "Отмена",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Default.Close,
            onClick = { orphanActionImage = null }
        )
    }

    AppBottomSheet(
        visible = showModelListForLink,
        onDismiss = { showModelListForLink = false; orphanActionImage = null },
        title = "Выберите модель"
    ) {
        if (modelsWithoutImages.isEmpty()) {
            Text(
                "Все модели уже имеют макеты.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth()
            ) {
                items(modelsWithoutImages, key = { it.article }) { model ->
                    SheetMenuItem(
                        text = model.article,
                        icon = Icons.Default.ShoppingBag,
                        onClick = {
                            showModelListForLink = false
                            if (orphanActionImage != null) {
                                attachExistingOrphanToModel(model)
                            } else {
                                modelToManualAttach = model
                                manualPhotoPicker.launch("image/*")
                            }
                        }
                    )
                }
            }
        }
    }

    if (fullScreenImage != null) {
        FullScreenImageDialog(uri = fullScreenImage!!, onDismiss = { fullScreenImage = null })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${order.factory} ${order.format} ${order.deliveryNum}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 28.sp, modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = "Добавить фото", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            if (!isLoading) {
                if (modelsWithoutImages.isNotEmpty()) {
                    Button(
                        onClick = { showModelListForLink = true; orphanActionImage = null },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800), contentColor = Color.White)
                    ) {
                        Text("Модели без макетов (${modelsWithoutImages.size})", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        enabled = false
                    ) {
                        Text("Все макеты привязаны", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (allImages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("В папке этого заказа нет макетов.\nНажмите на иконку вверху чтобы добавить.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedImages, key = { it.uri.toString() }) { image ->
                        val uri = image.uri
                        val isOrphan = image.isOrphan
                        val imageRequest = remember(uri) {
                            ImageRequest.Builder(context)
                                .data(uri)
                                .memoryCacheKey(uri.toString())
                                .build()
                        }

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isOrphan) 3.dp else 0.dp,
                                    color = if (isOrphan) Color.Red else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .pointerInput(uri) {
                                    detectTapGestures(
                                        onTap = {
                                            if (isOrphan) {
                                                orphanActionImage = uri
                                            } else {
                                                fullScreenImage = uri
                                            }
                                        },
                                        onLongPress = { imageToDelete = uri }
                                    )
                                }
                        ) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = "Макет",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            if (isOrphan) {
                                Text(
                                    "!",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.TopStart).background(Color.Red, RoundedCornerShape(4.dp)).padding(2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun getModelsWithoutImages(order: Order, macroNames: Set<String>): List<ModelInfo> =
    order.models.filter { normalizeArticle(it.article) !in macroNames }

fun loadAllImagesFromFolder(context: Context, folderUri: String?, order: Order): List<FolderImage> {
    val result = mutableListOf<FolderImage>()
    if (folderUri == null) return result
    try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        val orderFolder = tree?.findFile(order.internalFolderName)
        if (orderFolder != null && orderFolder.isDirectory) {
            orderFolder.listFiles().forEach { file ->
                val name = file.name
                if (file.isFile && name != null &&
                    (name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".jpeg", true))) {
                    result.add(FolderImage(file.uri, name, macroFileBaseName(name)))
                }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return result
}

// ================= НОВАЯ ВКЛАДКА: КАЛЕНДАРЬ =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    orders: List<Order>,
    sharedPrefs: android.content.SharedPreferences,
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    onModelClick: (String, String) -> Unit
) {
    val calendar = Calendar.getInstance()
    var currentYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }

    val workByDate = remember(orders, sharedPrefs) {
        val map = mutableMapOf<String, MutableList<Pair<Order, ModelInfo>>>()
        orders.forEach { order ->
            order.models.forEach { model ->
                val decisionKey = ModelProgressStore.key(order.id, model.article, ModelProgressStore.DECISION)
                val dateKey = ModelProgressStore.key(order.id, model.article, ModelProgressStore.DATE)
                val decision = sharedPrefs.getString(decisionKey, "") ?: ""
                val date = sharedPrefs.getString(dateKey, "") ?: ""
                if (decision.isNotEmpty() && date.isNotEmpty()) {
                    if (!map.containsKey(date)) map[date] = mutableListOf()
                    map[date]!!.add(order to model)
                }
            }
        }
        map
    }

    val dayCounts = remember(workByDate) {
        workByDate.mapValues { it.value.size }
    }

    val daysInMonth = remember(currentYear, currentMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val firstDayOfWeek = remember(currentYear, currentMonth) {
        mondayFirstDayOffset(currentYear, currentMonth)
    }

    val monthNames = arrayOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")

    val selectedDayModels = remember(selectedDate, workByDate) {
        workByDate[selectedDate] ?: emptyList()
    }

    val groupedByOrder = remember(selectedDayModels) {
        selectedDayModels.groupBy { it.first }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Календарь приёмок", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (currentMonth == 0) { currentMonth = 11; currentYear-- }
                else currentMonth--
            }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Назад", tint = MaterialTheme.colorScheme.primary)
            }
            Text("${monthNames[currentMonth]} $currentYear", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = {
                if (currentMonth == 11) { currentMonth = 0; currentYear++ }
                else currentMonth++
            }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Вперед", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { day ->
                Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(4.dp))

        var day = 1
        for (week in 0..5) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (dayOfWeek in 0..6) {
                    if ((week == 0 && dayOfWeek < firstDayOfWeek) || day > daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f).height(48.dp))
                    } else {
                        val dateStr = String.format("%02d.%02d.%d", day, currentMonth + 1, currentYear)
                        val hasWork = dayCounts.containsKey(dateStr)
                        val isSelected = dateStr == selectedDate
                        val count = dayCounts[dateStr] ?: 0

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        hasWork -> acceptedBg()
                                        else -> Color.Transparent
                                    }
                                )
                                .safeClickable { onDateSelected(if (isSelected) "" else dateStr) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    day.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) Color.White else if (hasWork) successText() else MaterialTheme.colorScheme.onSurface
                                )
                                if (hasWork && !isSelected) {
                                    Text(
                                        count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = successText()
                                    )
                                }
                            }
                        }
                        day++
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(24.dp))

        if (selectedDate.isNotEmpty()) {
            Text("Работа за $selectedDate", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (groupedByOrder.isEmpty()) {
                Text("Нет работы в этот день.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                groupedByOrder.forEach { (order, models) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "${order.factory} ${order.format} ${order.deliveryNum} ${order.date}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))

                            models.forEach { (_, model) ->
                                val decision = sharedPrefs.getString(ModelProgressStore.key(order.id, model.article, ModelProgressStore.DECISION), "") ?: ""
                                val note = sharedPrefs.getString(ModelProgressStore.key(order.id, model.article, ModelProgressStore.NOTE), "") ?: ""
                                val testDate = sharedPrefs.getString(ModelProgressStore.key(order.id, model.article, ModelProgressStore.TEST_DATE), "") ?: ""
                                val testReceived = sharedPrefs.getString(ModelProgressStore.key(order.id, model.article, ModelProgressStore.TEST_RECEIVED_DATE), "") ?: ""

                                val cardColor = when (decision) {
                                    "ПРИНЯТО" -> acceptedBg()
                                    "ДОРАБОТКА" -> reworkBg()
                                    "НЕ ГОТОВО", "НЕ ПРИНЯТО" -> rejectedBg()
                                    else -> MaterialTheme.colorScheme.surface
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).safeClickable { onModelClick(order.id, model.article) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(model.article, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                            Text(decision, style = MaterialTheme.typography.labelSmall, color = statusLabelColor())
                                        }
                                        if (note.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(note, style = MaterialTheme.typography.bodySmall, color = subtleText(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (testDate.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            if (testReceived.isNotEmpty()) {
                                                Text("Тест: ✓ $testReceived", style = MaterialTheme.typography.labelSmall, color = successText())
                                            } else {
                                                Text("На тесте с: $testDate", style = MaterialTheme.typography.labelSmall, color = warnText())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= ВКЛАДКА НАСТРОЙКИ =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    folderUri: String?,
    onSelectFolder: () -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("QC_PREFS", Context.MODE_PRIVATE) }
    var touchProtectionMode by remember { mutableStateOf(getTouchProtectionMode(sharedPrefs)) }
    var statusVibrationEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean(STATUS_VIBRATION_ENABLED_KEY, true))
    }
    var showTouchProtectionMenu by remember { mutableStateOf(false) }
    val versionName = remember(context) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "?"
        } catch (e: Exception) { "?" }
    }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var downloadTotal by remember { mutableLongStateOf(-1L) }

    var showThemeMenu by remember { mutableStateOf(false) }
    val themeLabel = when (themeMode) {
        "light" -> "Светлая"
        "dark" -> "Темная"
        else -> "Системная"
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Настройки", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            onClick = onSelectFolder
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFF03A9F4),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Папка для файлов", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (folderUri == null) "Не выбрана — нажмите, чтобы выбрать"
                        else "Нажмите, чтобы сменить папку",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Сменить", color = Color(0xFF03A9F4), style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            onClick = { showThemeMenu = true }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DarkMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Тема оформления", style = MaterialTheme.typography.titleSmall)
                    Text(themeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        DropdownMenu(
            expanded = showThemeMenu,
            onDismissRequest = { showThemeMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            DropdownMenuItem(
                text = { Text("Системная", style = MaterialTheme.typography.bodyMedium) },
                onClick = { onThemeModeChange("system"); showThemeMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Светлая", style = MaterialTheme.typography.bodyMedium) },
                onClick = { onThemeModeChange("light"); showThemeMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Темная", style = MaterialTheme.typography.bodyMedium) },
                onClick = { onThemeModeChange("dark"); showThemeMenu = false }
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            onClick = { showTouchProtectionMenu = true }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Защита от случайных касаний", style = MaterialTheme.typography.titleSmall)
                    Text(touchProtectionMode.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        DropdownMenu(
            expanded = showTouchProtectionMenu,
            onDismissRequest = { showTouchProtectionMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            TouchProtectionMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        touchProtectionMode = mode
                        sharedPrefs.edit().putString(TOUCH_PROTECTION_MODE_KEY, mode.preferenceValue).apply()
                        showTouchProtectionMenu = false
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Vibration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Вибрация при смене статуса",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = statusVibrationEnabled,
                    onCheckedChange = { enabled ->
                        statusVibrationEnabled = enabled
                        sharedPrefs.edit().putBoolean(STATUS_VIBRATION_ENABLED_KEY, enabled).apply()
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    isCheckingUpdate = true
                    try {
                        val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
                        when (val result = UpdateManager.checkForUpdate(current)) {
                            is UpdateManager.CheckResult.Available -> {
                                updateInfo = result.info
                                showUpdateDialog = true
                            }
                            UpdateManager.CheckResult.UpToDate ->
                                Toast.makeText(context, "У вас последняя версия", Toast.LENGTH_SHORT).show()
                            is UpdateManager.CheckResult.Error ->
                                Toast.makeText(context, "Не удалось проверить обновления. Проверьте подключение к сети", Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        isCheckingUpdate = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            enabled = !isCheckingUpdate && !isDownloading
        ) {
            if (isCheckingUpdate) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Проверить обновления", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Версия приложения: $versionName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "They coded them as gods Qeedy and k1mle",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
    }

    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showUpdateDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Доступно обновление", style = MaterialTheme.typography.titleLarge) },
            text = {
                if (isDownloading) {
                    Column {
                        Text("Скачивание обновления...", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        if (downloadTotal > 0L) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text("Загружено: ${downloadedBytes / 1024 / 1024} МБ", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Text("Новая версия ${updateInfo!!.version}. Скачать и установить?", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                if (!isDownloading) {
                    TextButton(onClick = {
                        val downloadUrl = updateInfo?.downloadUrl ?: return@TextButton
                        scope.launch {
                            isDownloading = true
                            downloadProgress = 0f
                            downloadedBytes = 0L
                            downloadTotal = -1L
                            val apk = UpdateManager.downloadAndExtractApk(
                                context = context,
                                url = downloadUrl,
                                onProgress = { downloaded, total ->
                                    downloadedBytes = downloaded
                                    downloadTotal = total
                                    if (total > 0) {
                                        downloadProgress = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                                    }
                                }
                            )
                            isDownloading = false
                            if (apk != null) {
                                showUpdateDialog = false
                                try {
                                    UpdateManager.installApk(context, apk)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Не удалось открыть установщик", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Ошибка скачивания или распаковки", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) { Text("Обновить") }
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showUpdateDialog = false }) { Text("Позже") }
                }
            }
        )
    }
}
// ================= ЭКРАН 1: СПИСОК ЗАКАЗОВ =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(
    orders: List<Order>,
    onOrderClick: (Order) -> Unit,
    onFileLoaded: (List<Order>) -> Unit,
    onClearOrders: () -> Unit,
    imagesFolderUri: String?,
    isAutoScanning: Boolean,
    listState: LazyListState,
    sharedPrefs: android.content.SharedPreferences,
    refreshAction: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchType: String,
    onSearchTypeChange: (String) -> Unit,
    onModelClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    var showTopMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showSearchMenu by remember { mutableStateOf(false) }

    var selectedFactories by remember { mutableStateOf(setOf<String>()) }
    var tempSelectedFactories by remember { mutableStateOf(setOf<String>()) }
    var showFactoryMenu by remember { mutableStateOf(false) }

    var orderToEdit by remember { mutableStateOf<Order?>(null) }
    var orderToDelete by remember { mutableStateOf<Order?>(null) }
    var orderMenuForLongPress by remember { mutableStateOf<Order?>(null) }
    var editFactory by remember { mutableStateOf("") }
    var editFormat by remember { mutableStateOf("") }
    var editDelivery by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val fileName = getDocumentDisplayName(context, uri) ?: "Manual_Order.xlsx"
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val order = parseExcel(inputStream, fileName)
                    withContext(Dispatchers.Main) {
                        when {
                            order == null -> Toast.makeText(context, "Не удалось прочитать Excel-файл", Toast.LENGTH_SHORT).show()
                            orders.any { it.id == order.id } -> Toast.makeText(context, "Этот заказ уже загружен", Toast.LENGTH_SHORT).show()
                            else -> {
                                if (imagesFolderUri != null) createOrderFolderSAF(context, imagesFolderUri, order.internalFolderName)
                                onFileLoaded(orders + order)
                                Toast.makeText(context, "Загружен: ${order.factory}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Ошибка загрузки Excel-файла", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val filteredOrders = remember(orders, searchQuery, selectedFactories, searchType) {
        if (searchQuery.isEmpty()) {
            val formatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val orderDates = orders.associate { order ->
                order.id to try {
                    formatter.parse(order.date) ?: Date(Long.MAX_VALUE)
                } catch (e: Exception) {
                    Date(Long.MAX_VALUE)
                }
            }
            orders
                .filter { if (selectedFactories.isEmpty()) true else it.factory in selectedFactories }
                .sortedWith(compareBy { orderDates[it.id] ?: Date(Long.MAX_VALUE) })
        } else if (searchType == "По заказу") {
            orders
                .filter { it.factory.contains(searchQuery, true) || it.deliveryNum.contains(searchQuery, true) || it.format.contains(searchQuery, true) }
                .filter { if (selectedFactories.isEmpty()) true else it.factory in selectedFactories }
        } else {
            emptyList()
        }
    }

    val foundModels = remember(orders, searchQuery, searchType, selectedFactories) {
        if (searchQuery.isNotEmpty() && searchType == "По артикулу") {
            searchModelsByArticle(orders, searchQuery, selectedFactories)
        } else emptyList()
    }

    val uniqueFactories = remember(orders) { orders.map { it.factory }.distinct().sorted() }

    AppBottomSheet(
        visible = showClearDialog,
        onDismiss = { showClearDialog = false },
        title = "Очистить все заказы?"
    ) {
        Text(
            "Вы уверены, что хотите удалить ВСЕ заказы? Это действие нельзя отменить.",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HoldToConfirmButton(
            actionText = "Удалить все",
            onConfirm = { onClearOrders(); showClearDialog = false },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        )
        SheetMenuItem(
            text = "Отмена",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Default.Close,
            onClick = { showClearDialog = false }
        )
    }

    AppBottomSheet(
        visible = orderMenuForLongPress != null,
        onDismiss = { orderMenuForLongPress = null },
        title = orderMenuForLongPress?.let { "${it.factory} ${it.format} ${it.deliveryNum}" }
    ) {
        SheetMenuItem(
            text = "Редактировать",
            icon = Icons.Default.Edit,
            onClick = {
                orderToEdit = orderMenuForLongPress
                editFactory = orderMenuForLongPress!!.factory
                editFormat = orderMenuForLongPress!!.format
                editDelivery = orderMenuForLongPress!!.deliveryNum
                editDate = orderMenuForLongPress!!.date
                orderMenuForLongPress = null
            }
        )
        SheetMenuItem(
            text = "Удалить",
            color = Color.Red,
            icon = Icons.Default.Delete,
            onClick = {
                orderToDelete = orderMenuForLongPress
                orderMenuForLongPress = null
            }
        )
        SheetMenuItem(
            text = "Отмена",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Default.Close,
            onClick = { orderMenuForLongPress = null }
        )
    }

    AppBottomSheet(
        visible = showTopMenu,
        onDismiss = { showTopMenu = false },
        title = "Действия"
    ) {
        SheetMenuItem(
            text = "Загрузить вручную",
            icon = Icons.Default.UploadFile,
            onClick = { showTopMenu = false; filePickerLauncher.launch("*/*") }
        )
        SheetMenuItem(
            text = "Очистить все",
            color = Color.Red,
            icon = Icons.Default.DeleteSweep,
            onClick = { showTopMenu = false; showClearDialog = true }
        )
        SheetMenuItem(
            text = "Отмена",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Default.Close,
            onClick = { showTopMenu = false }
        )
    }

    AppBottomSheet(
        visible = showFactoryMenu,
        onDismiss = { showFactoryMenu = false },
        title = "Фильтр по фабрикам"
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth()
        ) {
            items(uniqueFactories) { factory ->
                val isSelected = tempSelectedFactories.contains(factory)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .safeClickable {
                            tempSelectedFactories = if (isSelected) tempSelectedFactories - factory else tempSelectedFactories + factory
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelegramCheckbox(
                        checked = isSelected,
                        onClick = {
                            tempSelectedFactories = if (isSelected) tempSelectedFactories - factory else tempSelectedFactories + factory
                        }
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(factory, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { tempSelectedFactories = emptySet() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Сбросить все", color = Color.Red, style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = {
                    selectedFactories = tempSelectedFactories
                    showFactoryMenu = false
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Применить", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (orderToEdit != null) {
        AlertDialog(
            onDismissRequest = { orderToEdit = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Редактировать заказ", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    OutlinedTextField(value = editFactory, onValueChange = { editFactory = it }, label = { Text("Фабрика") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editFormat, onValueChange = { editFormat = it }, label = { Text("Формат") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editDelivery, onValueChange = { editDelivery = it }, label = { Text("Поставка") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editDate, onValueChange = { editDate = it }, label = { Text("Дата") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val oldOrder = orderToEdit!!
                    val updatedOrder = oldOrder.copy(factory = editFactory, format = editFormat, deliveryNum = editDelivery, date = editDate)
                    onFileLoaded(orders.map { if (it == oldOrder) updatedOrder else it })
                    orderToEdit = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { orderToEdit = null }) { Text("Отмена") } }
        )
    }

    if (orderToDelete != null) {
        AlertDialog(
            onDismissRequest = { orderToDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Удалить заказ?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Заказ ${orderToDelete!!.factory} ${orderToDelete!!.deliveryNum} будет удален вместе с макетами и Excel-файлом.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                HoldToConfirmButton(
                    actionText = "Удалить",
                    onConfirm = {
                        val target = orderToDelete ?: return@HoldToConfirmButton
                        val folderUri = imagesFolderUri
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                folderUri == null ||
                                    (deleteExcelFileSAF(context, folderUri, target.id) &&
                                        deleteOrderFolderSAF(context, folderUri, target.internalFolderName))
                            }
                            if (deleted) {
                                onFileLoaded(orders.filter { it.id != target.id })
                                orderToDelete = null
                                Toast.makeText(context, "Заказ удален", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Не удалось полностью удалить файлы заказа", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            },
            dismissButton = { TextButton(onClick = { orderToDelete = null }) { Text("Отмена") } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Мои заказы", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { refreshAction() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        tempSelectedFactories = selectedFactories
                        showFactoryMenu = true
                    }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Фабрики",
                            tint = if (selectedFactories.isNotEmpty()) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showTopMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    leadingIcon = { Icon(if (searchType == "По заказу") Icons.Default.Search else Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start),
                    placeholder = { Text(if (searchType == "По заказу") "Поиск по заказу..." else "Поиск по артикулу...", style = MaterialTheme.typography.bodyMedium) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    singleLine = true,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить", modifier = Modifier.size(20.dp))
                                }
                            }
                            Box {
                                IconButton(onClick = { showSearchMenu = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(
                                    expanded = showSearchMenu,
                                    onDismissRequest = { showSearchMenu = false },
                                    offset = DpOffset(x = 0.dp, y = 0.dp),
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    DropdownMenuItem(text = { Text("По заказу", style = MaterialTheme.typography.bodyMedium) }, onClick = { onSearchTypeChange("По заказу"); showSearchMenu = false; onSearchQueryChange("") })
                                    DropdownMenuItem(text = { Text("По артикулу", style = MaterialTheme.typography.bodyMedium) }, onClick = { onSearchTypeChange("По артикулу"); showSearchMenu = false; onSearchQueryChange("") })
                                }
                            }
                        }
                    }
                )
            }

            if (selectedFactories.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Фабрики: ${selectedFactories.joinToString(", ")}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedFactories = emptySet() }) { Text("Сбросить", style = MaterialTheme.typography.labelMedium) }
                }
            }

            if (searchQuery.isNotEmpty() && searchType == "По артикулу") {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(foundModels, key = { "${it.first.id}_${it.second.article}" }) { (order, model) ->
                        Card(modifier = Modifier.fillMaxWidth().safeClickable { onModelClick(order.id, model.article) }) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(model.article, style = MaterialTheme.typography.titleMedium)
                                Text("${order.factory} ${order.format} ${order.deliveryNum}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else if (orders.isEmpty() && !isAutoScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет заказов. Закиньте Excel-файл в папку.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 1.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        val (accepted, rejected, rework) = getOrderStats(order, sharedPrefs)
                        val overdueDays = getOverdueDays(order.date)
                        val isAllAccepted = accepted > 0 && accepted == order.models.size
                        val bgColor = if (isAllAccepted) allAcceptedBg() else MaterialTheme.colorScheme.surface

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .safeCombinedClickable(
                                    onTap = { onOrderClick(order) },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        orderMenuForLongPress = order
                                    }
                                ),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = bgColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OrderStatsRing(
                                    accepted = accepted,
                                    rejected = rejected,
                                    rework = rework,
                                    total = order.models.size
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            order.factory,
                                            modifier = Modifier.weight(1f, fill = false),
                                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${order.format} | ${order.deliveryNum}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Дата: ${order.date}",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (overdueDays > 0) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Просрочено: $overdueDays дн.",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = Color(0xFFD32F2F),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Моделей: ${order.models.size}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(12.dp))
                                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), RoundedCornerShape(4.dp)))
                                        Spacer(Modifier.width(4.dp))
                                        Text("$accepted", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Color(0xFF4CAF50))
                                        Spacer(Modifier.width(12.dp))
                                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFF44336), RoundedCornerShape(4.dp)))
                                        Spacer(Modifier.width(4.dp))
                                        Text("$rejected", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Color(0xFFF44336))
                                        Spacer(Modifier.width(12.dp))
                                        Box(modifier = Modifier.size(8.dp).background(warnText(), RoundedCornerShape(4.dp)))
                                        Spacer(Modifier.width(4.dp))
                                        Text("$rework", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = warnText())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= ФУНКЦИИ SAF =================

fun hasPersistedFolderPermission(context: Context, folderUri: String): Boolean =
    context.contentResolver.persistedUriPermissions.any { permission ->
        permission.uri.toString() == folderUri && permission.isReadPermission && permission.isWritePermission
    }

fun createOrderFolderSAF(context: Context, folderUri: String, folderName: String): Boolean = try {
    val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
    val existing = tree?.findFile(folderName)
    when {
        existing != null -> existing.isDirectory
        else -> tree?.createDirectory(folderName)?.isDirectory == true
    }
} catch (e: Exception) {
    e.printStackTrace()
    false
}

fun deleteOrderFolderSAF(context: Context, folderUri: String, folderName: String): Boolean = try {
    val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) ?: return false
    tree.findFile(folderName)?.delete() ?: true
} catch (e: Exception) {
    e.printStackTrace()
    false
}

fun deleteExcelFileSAF(context: Context, folderUri: String, fileName: String): Boolean = try {
    val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) ?: return false
    tree.findFile(fileName)?.delete() ?: true
} catch (e: Exception) {
    e.printStackTrace()
    false
}

fun deleteDocument(context: Context, uri: Uri): Boolean = try {
    DocumentFile.fromSingleUri(context, uri)?.delete() == true
} catch (e: Exception) {
    e.printStackTrace()
    false
}

fun copyDocumentContent(context: Context, sourceUri: Uri, target: DocumentFile): Boolean = try {
    val input = context.contentResolver.openInputStream(sourceUri)
        ?: throw java.io.IOException("Не удалось открыть исходный файл")
    val copied = input.use { source ->
        val output = context.contentResolver.openOutputStream(target.uri, "wt")
            ?: throw java.io.IOException("Не удалось открыть файл назначения")
        output.use { destination -> source.copyTo(destination) }
        true
    }
    if (!copied) target.delete()
    copied
} catch (e: Exception) {
    e.printStackTrace()
    target.delete()
    false
}

// ================= ЭКРАН 2: СПИСОК МОДЕЛЕЙ =================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelListScreen(
    order: Order,
    getStatus: (String) -> String,
    getDate: (String) -> String,
    getTestDate: (String) -> String,
    getTestReceivedDate: (String) -> String,
    onBack: () -> Unit,
    onModelClick: (ModelInfo) -> Unit,
    onOrdersUpdated: (List<Order>) -> Unit,
    allOrders: List<Order>,
    imagesFolderUri: String?,
    imagesCache: MutableMap<String, List<Uri>>,
    listState: LazyListState,
    sharedPrefs: android.content.SharedPreferences,
    sortOption: String,
    onSortOptionChange: (String) -> Unit,
    selectedDate: String,
    onSelectedDateChange: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val touchProtectionMode = getTouchProtectionMode(sharedPrefs)

    fun modelKey(article: String, field: String): String = ModelProgressStore.key(order.id, article, field)

    var progressRevision by remember { mutableIntStateOf(0) }
    var tempSortOptions by remember { mutableStateOf(setOf<String>()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<ModelInfo?>(null) }
    var longPressModel by remember { mutableStateOf<ModelInfo?>(null) }

    var showMainMenu by remember { mutableStateOf(false) }
    var deletedModelsList by remember { mutableStateOf<List<ModelInfo>?>(null) }
    var restoring by remember { mutableStateOf(false) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedArticles by remember { mutableStateOf(setOf<String>()) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var bulkCommentText by remember { mutableStateOf("") }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    var showBulkTestWarning by remember { mutableStateOf(false) }
    var bulkTestAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showBulkReworkDialog by remember { mutableStateOf(false) }
    var bulkSelectedReasons by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(isSelectionMode) {
        onSelectionModeChange(isSelectionMode)
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedArticles = emptySet()
    }

    val datesWithChecks = remember(order.models, order.id, progressRevision) {
        order.models.map { getDate(it.article) }.filter { it.isNotEmpty() }.toSet()
    }

    LaunchedEffect(showSortMenu) {
        if (showSortMenu) {
            tempSortOptions = if (sortOption.isEmpty()) emptySet() else sortOption.split(", ").toSet()
        }
    }

    if (showBulkTestWarning) {
        AlertDialog(
            onDismissRequest = { showBulkTestWarning = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Внимание!", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Среди выбранных моделей есть те, которые необходимо отправить на тест (текстильная подкладка и размеры < 27). Продолжить?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showBulkTestWarning = false
                    bulkTestAction?.invoke()
                    bulkTestAction = null
                }) { Text("Да, продолжить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBulkTestWarning = false
                    bulkTestAction = null
                }) { Text("Отмена") }
            }
        )
    }

    ReworkReasonPicker(
        visible = showBulkReworkDialog,
        sharedPrefs = sharedPrefs,
        multiSelect = true,
        selectedReasons = bulkSelectedReasons,
        onReasonClick = { reason ->
            bulkSelectedReasons = if (reason in bulkSelectedReasons) {
                bulkSelectedReasons - reason
            } else {
                bulkSelectedReasons + reason
            }
        },
        onReasonDeleted = { reason -> bulkSelectedReasons = bulkSelectedReasons - reason },
        onDismiss = {
            showBulkReworkDialog = false
            bulkSelectedReasons = emptySet()
        },
        onConfirm = {
            val articles = selectedArticles
            val reasons = bulkSelectedReasons
            if (articles.isNotEmpty() && reasons.isNotEmpty()) {
                val editor = sharedPrefs.edit()
                val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                articles.forEach { article ->
                    editor.putString(modelKey(article, ModelProgressStore.DECISION), "ДОРАБОТКА")
                    editor.putString(modelKey(article, ModelProgressStore.DATE), date)
                    val noteKey = modelKey(article, ModelProgressStore.NOTE)
                    val oldNote = sharedPrefs.getString(noteKey, "") ?: ""
                    editor.putString(noteKey, appendReworkReasons(oldNote, reasons))
                    val selectedModel = order.models.find { it.article == article }
                    if (selectedModel != null && shouldSuggestTest(selectedModel)) {
                        val testDateKey = modelKey(article, ModelProgressStore.TEST_DATE)
                        val existingTestDate = sharedPrefs.getString(testDateKey, "") ?: ""
                        if (existingTestDate.isEmpty()) {
                            editor.putString(testDateKey, date)
                        }
                    }
                }
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { editor.commit() }
                    if (saved) {
                        progressRevision++
                        StatusHapticHelper.onStatusApplied(sharedPrefs, context)
                        showBulkReworkDialog = false
                        bulkSelectedReasons = emptySet()
                        isSelectionMode = false
                        selectedArticles = emptySet()
                        Toast.makeText(context, "Статус ДОРАБОТКА установлен", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Не удалось сохранить статусы", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    if (showCommentDialog) {
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Комментарий к ${selectedArticles.size} моделям", style = MaterialTheme.typography.titleLarge) },
            text = { OutlinedTextField(value = bulkCommentText, onValueChange = { bulkCommentText = it }, label = { Text("Текст") }, minLines = 3, textStyle = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    val editor = sharedPrefs.edit()
                    selectedArticles.forEach { article ->
                        val noteKey = modelKey(article, ModelProgressStore.NOTE)
                        val oldNote = sharedPrefs.getString(noteKey, "") ?: ""
                        val newNote = if (oldNote.isEmpty()) bulkCommentText else "$oldNote\n$bulkCommentText"
                        editor.putString(noteKey, newNote)
                    }
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { editor.commit() }
                        if (saved) {
                            Toast.makeText(context, "Добавлено", Toast.LENGTH_SHORT).show()
                            showCommentDialog = false
                            bulkCommentText = ""
                            isSelectionMode = false
                            selectedArticles = emptySet()
                        } else {
                            Toast.makeText(context, "Не удалось сохранить комментарии", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showCommentDialog = false }) { Text("Отмена") } }
        )
    }

    if (modelToDelete != null) {
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Удалить модель?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Удалить ${modelToDelete!!.article}?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                HoldToConfirmButton(
                    actionText = "Удалить",
                    onConfirm = {
                        val target = modelToDelete!!
                        modelToDelete = null
                        val newOrder = order.copy(models = order.models.filter { it != target })
                        onOrdersUpdated(allOrders.map { if (it.id == order.id) newOrder else it })
                    }
                )
            },
            dismissButton = { TextButton(onClick = { modelToDelete = null }) { Text("Отмена") } }
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Удалить выбранные?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Будет удалено ${selectedArticles.size} моделей. Это действие нельзя отменить.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                HoldToConfirmButton(
                    actionText = "Удалить",
                    onConfirm = {
                        val newOrder = order.copy(models = order.models.filter { it.article !in selectedArticles })
                        onOrdersUpdated(allOrders.map { if (it.id == order.id) newOrder else it })
                        showDeleteSelectedDialog = false
                        isSelectionMode = false
                        selectedArticles = emptySet()
                    }
                )
            },
            dismissButton = { TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("Отмена") } }
        )
    }

    if (deletedModelsList != null) {
        AlertDialog(
            onDismissRequest = { if (!restoring) deletedModelsList = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Восстановить модель", style = MaterialTheme.typography.titleLarge) },
            text = {
                if (deletedModelsList!!.isEmpty()) {
                    Text("Удалённых моделей не найдено.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn {
                        items(deletedModelsList!!) { model ->
                            Text(
                                model.article,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .safeClickable {
                                        restoring = true
                                        val newOrder = order.copy(models = order.models + model)
                                        deletedModelsList = deletedModelsList!!.filter { it != model }
                                        restoring = false
                                        onOrdersUpdated(allOrders.map { if (it.id == order.id) newOrder else it })
                                    }
                                    .padding(12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { deletedModelsList = null }, enabled = !restoring) { Text("Закрыть") } }
        )
    }

    if (showCalendar) {
        CalendarDialog(
            datesWithChecks = datesWithChecks,
            selectedDate = selectedDate,
            onDateSelected = { date -> onSelectedDateChange(date); showCalendar = false },
            onResetDate = { onSelectedDateChange(""); showCalendar = false },
            onDismiss = { showCalendar = false }
        )
    }

    AppBottomSheet(
        visible = showSortMenu,
        onDismiss = { showSortMenu = false },
        title = "Сортировка"
    ) {
        val options = listOf("Принят", "Не принят", "Доработка", "Пустое")
        LazyColumn(
            modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth()
        ) {
            items(options) { option ->
                val isSelected = tempSortOptions.contains(option)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .safeClickable {
                            tempSortOptions = if (isSelected) tempSortOptions - option else tempSortOptions + option
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelegramCheckbox(
                        checked = isSelected,
                        onClick = {
                            tempSortOptions = if (isSelected) tempSortOptions - option else tempSortOptions + option
                        }
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(option, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { tempSortOptions = emptySet() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Сбросить все", color = Color.Red, style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = {
                    val newSort = if (tempSortOptions.isEmpty()) "" else tempSortOptions.joinToString(", ")
                    onSortOptionChange(newSort)
                    showSortMenu = false
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Применить", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    AppBottomSheet(
        visible = longPressModel != null,
        onDismiss = { longPressModel = null },
        title = longPressModel?.article
    ) {
        SheetMenuItem(
            text = "Выбрать",
            icon = Icons.Default.CheckCircle,
            onClick = {
                val target = longPressModel!!
                longPressModel = null
                isSelectionMode = true
                selectedArticles = setOf(target.article)
            }
        )
        SheetMenuItem(
            text = "Удалить",
            color = Color.Red,
            icon = Icons.Default.Delete,
            onClick = {
                modelToDelete = longPressModel
                longPressModel = null
            }
        )
        SheetMenuItem(
            text = "Отмена",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Default.Close,
            onClick = { longPressModel = null }
        )
    }

    AppBottomSheet(
        visible = showMainMenu,
        onDismiss = { showMainMenu = false },
        title = "Действия"
    ) {
        SheetMenuItem(
            text = "Выбрать несколько",
            icon = Icons.Default.CheckCircle,
            onClick = { isSelectionMode = true; showMainMenu = false }
        )
        SheetMenuItem(
            text = "Восстановить удалённые",
            icon = Icons.Default.Restore,
            onClick = {
                showMainMenu = false
                val folderUri = imagesFolderUri
                if (folderUri != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val file = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))?.findFile(order.id)
                            val parsed = file?.let {
                                parseExcel(context.contentResolver.openInputStream(it.uri), it.name ?: order.id)
                            }
                            withContext(Dispatchers.Main) {
                                if (parsed == null) {
                                    Toast.makeText(context, "Не удалось прочитать исходный Excel-файл", Toast.LENGTH_LONG).show()
                                } else {
                                    val currentArticles = order.models.map { it.article }.toSet()
                                    deletedModelsList = parsed.models.filter { it.article !in currentArticles }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Ошибка доступа к исходному Excel-файлу", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )
        SheetMenuItem(
            text = "Отмена",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Default.Close,
            onClick = { showMainMenu = false }
        )
    }

    val activeSortOptions = remember(sortOption) {
        if (sortOption.isEmpty()) emptySet() else sortOption.split(", ").toSet()
    }

    val filteredModels = remember(order.models, searchQuery, selectedDate, activeSortOptions, progressRevision) {
        order.models
            .filter { it.article.contains(searchQuery, ignoreCase = true) }
            .filter { if (selectedDate.isEmpty()) true else getDate(it.article) == selectedDate }
            .filter { model ->
                if (activeSortOptions.isEmpty()) {
                    true
                } else {
                    var match = false
                    activeSortOptions.forEach { option ->
                        when (option) {
                            "Принят" -> if (getStatus(model.article) == "ПРИНЯТО") match = true
                            "Не принят" -> if (getStatus(model.article) == "НЕ ПРИНЯТО" || getStatus(model.article) == "НЕ ГОТОВО") match = true
                            "Доработка" -> if (getStatus(model.article) == "ДОРАБОТКА") match = true
                            "Пустое" -> if (getStatus(model.article).isEmpty()) match = true
                        }
                    }
                    match
                }
            }
            .sortedWith(compareBy { it.article })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${selectedArticles.size}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        TextButton(
                            onClick = {
                                isSelectionMode = false
                                selectedArticles = emptySet()
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("Отменить", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    actions = {
                        val allSelected = filteredModels.isNotEmpty() && filteredModels.all { selectedArticles.contains(it.article) }
                        TextButton(
                            onClick = {
                                if (allSelected) {
                                    selectedArticles = emptySet()
                                } else {
                                    selectedArticles = filteredModels.map { it.article }.toSet()
                                }
                            }
                        ) {
                            Text(
                                if (allSelected) "Снять все" else "Выбрать все",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (allSelected) Icons.Default.CheckCircle else Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                order.factory,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${order.format} • ${order.deliveryNum}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Сортировка", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showCalendar = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Календарь", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showMainMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIconButton(
                            icon = Icons.Default.Check,
                            label = "Принято",
                            tint = Color(0xFF4CAF50),
                            requireHold = touchProtectionMode == TouchProtectionMode.STRONG,
                            onClick = {
                                val needsTest = selectedArticles.any { article ->
                                    val m = order.models.find { it.article == article }
                                    m != null && shouldSuggestTest(m)
                                }
                                val doAction: () -> Unit = {
                                    val editor = sharedPrefs.edit()
                                    val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                                    selectedArticles.forEach { article ->
                                        editor.putString(modelKey(article, ModelProgressStore.DECISION), "ПРИНЯТО")
                                        editor.putString(modelKey(article, ModelProgressStore.DATE), date)
                                        val m = order.models.find { it.article == article }
                                        if (m != null && shouldSuggestTest(m)) {
                                            val testDateKey = modelKey(article, ModelProgressStore.TEST_DATE)
                                            val existingTestDate = sharedPrefs.getString(testDateKey, "") ?: ""
                                            if (existingTestDate.isEmpty()) {
                                                editor.putString(testDateKey, date)
                                            }
                                        }
                                    }
                                    scope.launch {
                                        val saved = withContext(Dispatchers.IO) { editor.commit() }
                                        if (saved) {
                                            progressRevision++
                                            StatusHapticHelper.onStatusApplied(sharedPrefs, context)
                                            isSelectionMode = false
                                            selectedArticles = emptySet()
                                            Toast.makeText(context, "Статус ПРИНЯТО установлен", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Не удалось сохранить статусы", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                if (needsTest) {
                                    bulkTestAction = doAction
                                    showBulkTestWarning = true
                                } else {
                                    doAction()
                                }
                            }
                        )
                        ActionIconButton(
                            icon = Icons.Default.Warning,
                            label = "Доработка",
                            tint = Color(0xFFF9A825),
                            requireHold = touchProtectionMode == TouchProtectionMode.STRONG,
                            onClick = {
                                val needsTest = selectedArticles.any { article ->
                                    val m = order.models.find { it.article == article }
                                    m != null && shouldSuggestTest(m)
                                }
                                val showReasonPicker = {
                                    showBulkReworkDialog = true
                                }
                                if (needsTest) {
                                    bulkTestAction = showReasonPicker
                                    showBulkTestWarning = true
                                } else {
                                    showReasonPicker()
                                }
                            }
                        )
                        ActionIconButton(
                            icon = Icons.Default.Close,
                            label = "Не готово",
                            tint = Color(0xFFF44336),
                            requireHold = touchProtectionMode == TouchProtectionMode.STRONG,
                            onClick = {
                                val editor = sharedPrefs.edit()
                                val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                                selectedArticles.forEach { article ->
                                    editor.putString(modelKey(article, ModelProgressStore.DECISION), "НЕ ГОТОВО")
                                    editor.putString(modelKey(article, ModelProgressStore.DATE), date)
                                }
                                scope.launch {
                                    val saved = withContext(Dispatchers.IO) { editor.commit() }
                                    if (saved) {
                                        progressRevision++
                                        StatusHapticHelper.onStatusApplied(sharedPrefs, context)
                                        isSelectionMode = false
                                        selectedArticles = emptySet()
                                        Toast.makeText(context, "Статус НЕ ГОТОВО установлен", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Не удалось сохранить статусы", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        ActionIconButton(
                            icon = Icons.Default.EditNote,
                            label = "Коммент",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = { showCommentDialog = true }
                        )
                        ActionIconButton(
                            icon = Icons.Default.Delete,
                            label = "Удалить",
                            tint = Color(0xFFD32F2F),
                            onClick = { showDeleteSelectedDialog = true }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (!isSelectionMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                )

                if (activeSortOptions.isNotEmpty() || selectedDate.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedDate.isNotEmpty()) {
                            Text("Дата: $selectedDate", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (activeSortOptions.isNotEmpty()) {
                            Text("Статус: ${activeSortOptions.joinToString(", ")}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(8.dp))
                        }
                        TextButton(onClick = { onSelectedDateChange(""); onSortOptionChange("") }) {
                            Text("Сбросить", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredModels, key = { it.article }) { model ->
                    val status = getStatus(model.article)
                    val note = sharedPrefs.getString(modelKey(model.article, ModelProgressStore.NOTE), "") ?: ""
                    val statusDate = getDate(model.article)
                    val testSent = getTestDate(model.article)
                    val testReceived = getTestReceivedDate(model.article)
                    val isSelected = selectedArticles.contains(model.article)

                    val cardColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else when (status) {
                        "ПРИНЯТО" -> acceptedBg()
                        "НЕ ПРИНЯТО", "НЕ ГОТОВО" -> rejectedBg()
                        "ДОРАБОТКА" -> reworkBg()
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .safeCombinedClickable(
                                onTap = {
                                    if (isSelectionMode) {
                                        selectedArticles = if (isSelected) selectedArticles - model.article else selectedArticles + model.article
                                    } else onModelClick(model)
                                },
                                onLongPress = {
                                    if (isSelectionMode) {
                                        selectedArticles = if (isSelected) selectedArticles - model.article else selectedArticles + model.article
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        longPressModel = model
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isSelectionMode) {
                                TelegramCheckbox(
                                    checked = isSelected,
                                    onClick = {
                                        selectedArticles = if (isSelected) selectedArticles - model.article else selectedArticles + model.article
                                    }
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.article, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                if (note.isNotEmpty()) {
                                    val shortNote = if (note.length > 30) note.take(30) + "…" else note
                                    Text(shortNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                }
                                if (statusDate.isNotEmpty()) {
                                    Text("Дата: $statusDate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                }
                                if (testSent.isNotEmpty()) {
                                    if (testReceived.isNotEmpty()) {
                                        Text(
                                            "Тест получен: $testReceived",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = successText(),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    } else {
                                        Text(
                                            "На тесте с: $testSent",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = warnText(),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (status.isNotEmpty()) {
                                Text(status, style = MaterialTheme.typography.labelSmall, color = statusLabelColor())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    requireHold: Boolean = false,
    onClick: () -> Unit
) {
    HoldableActionArea(
        requireHold = requireHold,
        onClick = onClick,
        modifier = Modifier.padding(8.dp)
    ) { holdProgress ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(tint.copy(alpha = 0.12f + holdProgress * 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
            if (requireHold && holdProgress > 0f) {
                LinearProgressIndicator(
                    progress = { holdProgress },
                    modifier = Modifier.width(44.dp).height(3.dp),
                    color = tint,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

// ================= ЭКРАН 3: КАРТОЧКА ПРОВЕРКИ =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionScreen(
    model: ModelInfo,
    onBack: () -> Unit,
    sharedPrefs: android.content.SharedPreferences,
    currentOrder: Order,
    imagesFolderUri: String?,
    imagesCache: MutableMap<String, List<Uri>>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val touchProtectionMode = getTouchProtectionMode(sharedPrefs)
    var fullScreenImage by remember { mutableStateOf<Uri?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showExtraPhotos by remember { mutableStateOf(false) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }

    var showResetDialog by remember { mutableStateOf(false) }
    var showReworkDialog by remember { mutableStateOf(false) }

    var isLoadingImages by remember { mutableStateOf(true) }
    var currentImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var extraPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var extraPhotoToDelete by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(imagesFolderUri, model.article) {
        isLoadingImages = true
        withContext(Dispatchers.IO) {
            val cacheKey = "${currentOrder.internalFolderName}_${model.article}"
            if (imagesCache.containsKey(cacheKey)) {
                currentImages = imagesCache[cacheKey] ?: emptyList()
            } else {
                val allImages = loadImagesFromFolderSAF(context, imagesFolderUri, currentOrder)
                imagesCache.putAll(allImages)
                currentImages = allImages[cacheKey] ?: emptyList()
            }
            extraPhotos = loadExtraPhotos(context, imagesFolderUri, currentOrder, model.article)
            isLoadingImages = false
        }
    }

    fun modelKey(field: String): String = ModelProgressStore.key(currentOrder.id, model.article, field)

    var upperChecked by remember { mutableStateOf(sharedPrefs.getBoolean(modelKey(ModelProgressStore.UPPER), false)) }
    var liningChecked by remember { mutableStateOf(sharedPrefs.getBoolean(modelKey(ModelProgressStore.LINING), false)) }
    var insoleChecked by remember { mutableStateOf(sharedPrefs.getBoolean(modelKey(ModelProgressStore.INSOLE), false)) }
    var outsoleChecked by remember { mutableStateOf(sharedPrefs.getBoolean(modelKey(ModelProgressStore.OUTSOLE), false)) }
    var decision by remember { mutableStateOf(sharedPrefs.getString(modelKey(ModelProgressStore.DECISION), "") ?: "") }
    var noteText by remember { mutableStateOf(sharedPrefs.getString(modelKey(ModelProgressStore.NOTE), "") ?: "") }
    var testDate by remember { mutableStateOf(sharedPrefs.getString(modelKey(ModelProgressStore.TEST_DATE), "") ?: "") }
    var testReceivedDate by remember { mutableStateOf(sharedPrefs.getString(modelKey(ModelProgressStore.TEST_RECEIVED_DATE), "") ?: "") }

    fun saveData(noteOverride: String? = null) {
        val finalNote = noteOverride ?: noteText
        sharedPrefs.edit().apply {
            putBoolean(modelKey(ModelProgressStore.UPPER), upperChecked)
            putBoolean(modelKey(ModelProgressStore.LINING), liningChecked)
            putBoolean(modelKey(ModelProgressStore.INSOLE), insoleChecked)
            putBoolean(modelKey(ModelProgressStore.OUTSOLE), outsoleChecked)
            putString(modelKey(ModelProgressStore.DECISION), decision)
            putString(modelKey(ModelProgressStore.NOTE), finalNote)
            putString(modelKey(ModelProgressStore.TEST_DATE), testDate)
            putString(modelKey(ModelProgressStore.TEST_RECEIVED_DATE), testReceivedDate)
            if (decision.isNotEmpty()) {
                val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                putString(modelKey(ModelProgressStore.DATE), date)
            }
            apply()
        }
    }

    fun resetData(deleteComment: Boolean) {
        sharedPrefs.edit().apply {
            remove(modelKey(ModelProgressStore.UPPER)); remove(modelKey(ModelProgressStore.LINING))
            remove(modelKey(ModelProgressStore.INSOLE)); remove(modelKey(ModelProgressStore.OUTSOLE))
            remove(modelKey(ModelProgressStore.DECISION))
            remove(modelKey(ModelProgressStore.DATE)); remove(modelKey(ModelProgressStore.TEST_DATE))
            remove(modelKey(ModelProgressStore.TEST_RECEIVED_DATE))
            if (deleteComment) {
                remove(modelKey(ModelProgressStore.NOTE))
            }
            apply()
        }
        upperChecked = false; liningChecked = false; insoleChecked = false; outsoleChecked = false
        decision = ""; testDate = ""; testReceivedDate = ""
        if (deleteComment) {
            noteText = ""
        }
    }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val sourceUri = tempCameraUri
        if (success && sourceUri != null && imagesFolderUri != null) {
            scope.launch(Dispatchers.IO) {
                val savedUri = copyToOrderFolder(context, sourceUri, imagesFolderUri, currentOrder.internalFolderName, model.article)
                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        extraPhotos = extraPhotos + savedUri
                        Toast.makeText(context, "Фото с камеры добавлено", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Не удалось сохранить фото с камеры", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val tempFile = File.createTempFile("camera_", ".jpg", context.cacheDir)
            tempCameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            takePictureLauncher.launch(tempCameraUri!!)
        } else {
            Toast.makeText(context, "Нет разрешения на камеру", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && imagesFolderUri != null) {
            scope.launch(Dispatchers.IO) {
                val savedUri = copyToOrderFolder(context, uri, imagesFolderUri, currentOrder.internalFolderName, model.article)
                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        extraPhotos = extraPhotos + savedUri
                        Toast.makeText(context, "Фото добавлено", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Не удалось сохранить фото", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Сбросить проверку?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    if (noteText.isNotEmpty()) {
                        "Все чекбоксы и статус будут сброшены. Удалить комментарий к этой модели?"
                    } else {
                        "Все чекбоксы и статус будут сброшены."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (noteText.isNotEmpty()) {
                        HoldToConfirmButton(
                            actionText = "Сбросить и удалить комментарий",
                            onConfirm = {
                                resetData(deleteComment = true)
                                showResetDialog = false
                                Toast.makeText(context, "Сброшено, комментарий удалён", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    HoldToConfirmButton(
                        actionText = if (noteText.isNotEmpty()) "Сбросить, оставить комментарий" else "Сбросить",
                        onConfirm = {
                            resetData(deleteComment = false)
                            showResetDialog = false
                            Toast.makeText(
                                context,
                                if (noteText.isNotEmpty()) "Сброшено, комментарий сохранён" else "Проверка сброшена",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    TextButton(onClick = { showResetDialog = false }) { Text("Отмена") }
                }
            }
        )
    }

    ReworkReasonPicker(
        visible = showReworkDialog,
        sharedPrefs = sharedPrefs,
        multiSelect = false,
        onReasonClick = { reason ->
            val newNote = appendReworkReasons(noteText, listOf(reason))
            noteText = newNote
            saveData(noteOverride = newNote)
            showReworkDialog = false
            StatusHapticHelper.onStatusApplied(sharedPrefs, context)
        },
        onDismiss = { showReworkDialog = false }
    )

    if (fullScreenImage != null) {
        FullScreenImageDialog(uri = fullScreenImage!!, onDismiss = { fullScreenImage = null })
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Добавить фото", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        "Камера",
                        modifier = Modifier.fillMaxWidth().safeClickable {
                            showPhotoSourceDialog = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                val tempFile = File.createTempFile("camera_", ".jpg", context.cacheDir)
                                tempCameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                                takePictureLauncher.launch(tempCameraUri!!)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Галерея",
                        modifier = Modifier.fillMaxWidth().safeClickable {
                            showPhotoSourceDialog = false
                            galleryLauncher.launch("image/*")
                        }.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPhotoSourceDialog = false }) { Text("Отмена") } }
        )
    }

    if (extraPhotoToDelete != null) {
        AlertDialog(
            onDismissRequest = { extraPhotoToDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Удалить фото?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Вы уверены, что хотите удалить эту фотографию?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                HoldToConfirmButton(
                    actionText = "Удалить",
                    onConfirm = {
                        val target = extraPhotoToDelete ?: return@HoldToConfirmButton
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) { deleteDocument(context, target) }
                            if (deleted) {
                                extraPhotos = extraPhotos.filter { it != target }
                                extraPhotoToDelete = null
                                Toast.makeText(context, "Фото удалено", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Не удалось удалить фото", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            },
            dismissButton = { TextButton(onClick = { extraPhotoToDelete = null }) { Text("Отмена") } }
        )
    }

    if (showExtraPhotos) {
        Dialog(onDismissRequest = { showExtraPhotos = false }) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                    Text("Прикрепленные фото", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    if (extraPhotos.isEmpty()) Text("Нет фото", style = MaterialTheme.typography.bodyMedium)
                    else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(extraPhotos) { uri ->
                            Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp))) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(uri).memoryCacheKey(uri.toString()).build(),
                                    contentDescription = "Фото",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().safeClickable { fullScreenImage = uri }
                                )
                                IconButton(
                                    onClick = { extraPhotoToDelete = uri },
                                    modifier = Modifier.size(24.dp).align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Удалить", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { showExtraPhotos = false }, shape = RoundedCornerShape(16.dp)) { Text("Закрыть") }
                }
            }
        }
    }

    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = { Text("Внимание!", style = MaterialTheme.typography.titleLarge) },
            shape = RoundedCornerShape(24.dp),
            text = { Text("Материал подкладки: «${model.lining}».\nРазмеры меньше 27.\n\nНеобходимо отправить подкладку на тест!", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    val currentDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                    testDate = currentDate
                    saveData()
                    showTestDialog = false
                    Toast.makeText(context, "Отметка о тесте добавлена", Toast.LENGTH_SHORT).show()
                    if (decision == "ДОРАБОТКА") {
                        showReworkDialog = true
                    }
                }) { Text("Отправить на тест") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTestDialog = false
                    if (decision == "ДОРАБОТКА") {
                        showReworkDialog = true
                    }
                }) { Text("Закрыть") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                model.article,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${currentOrder.factory} ${currentOrder.format} ${currentOrder.deliveryNum}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (decision.isNotEmpty()) {
                                Text(decision, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (testReceivedDate.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text("Тест: ✓ $testReceivedDate", style = MaterialTheme.typography.labelSmall, color = successText())
                            } else if (testDate.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text("На тесте: $testDate", style = MaterialTheme.typography.labelSmall, color = warnText())
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 28.sp, modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                    }
                }
            )
        }
    ) { paddingValues ->
        AppBottomSheet(
            visible = showMenu,
            onDismiss = { showMenu = false },
            title = "Действия"
        ) {
            SheetMenuItem(
                text = "Сбросить проверку",
                color = Color.Red,
                icon = Icons.Default.Refresh,
                onClick = {
                    showMenu = false
                    showResetDialog = true
                }
            )
            SheetMenuItem(
                text = "Отмена",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = Icons.Default.Close,
                onClick = { showMenu = false }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                if (isLoadingImages) {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (currentImages.isNotEmpty()) {
                    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(currentImages, key = { it.toString() }) { uri ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .pointerInput(Unit) { detectTapGestures(onTap = { fullScreenImage = uri }) }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(uri).memoryCacheKey(uri.toString()).build(),
                                    contentDescription = "Макет",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Text("Файлы не найдены.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text("Бренд: ${model.brand}", style = MaterialTheme.typography.titleMedium)
            Text("Цвет: ${model.color}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))

            Text("МАТЕРИАЛЫ", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            MaterialCheckItem("Верх", model.upper, upperChecked) { upperChecked = it; saveData() }
            MaterialCheckItem("Подкладка", model.lining, liningChecked) { liningChecked = it; saveData() }
            MaterialCheckItem("Стелька", model.insole, insoleChecked) { insoleChecked = it; saveData() }
            MaterialCheckItem("Подошва", model.outsole, outsoleChecked) { outsoleChecked = it; saveData() }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            Text("УПАКОВКА", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            InfoRow("Пар в коробе", model.pairsPerBox)
            Text("Размерные: Всего ${model.sizeBoxesCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Детализация: ${model.sizeBoxes.ifEmpty { "-" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Ростовочные: Всего ${model.mixedBoxesCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Состав: ${model.mixedBoxes.ifEmpty { "-" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            if (model.comments.isNotEmpty() && model.comments != "-") {
                Text("Комментарии из файла:", style = MaterialTheme.typography.titleSmall)
                Text(model.comments, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
            }

            if (testReceivedDate.isNotEmpty()) {
                Text("Тест получен: $testReceivedDate", style = MaterialTheme.typography.titleSmall, color = successText())
                Text("Отправлен на тест: $testDate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            } else if (testDate.isNotEmpty()) {
                Text("Отправлено на тест: $testDate", style = MaterialTheme.typography.titleSmall, color = Color.Red)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val currentDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                        testReceivedDate = currentDate
                        saveData()
                        Toast.makeText(context, "Тест получен", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)
                ) { Text("Тест получен", style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.height(8.dp))
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it; saveData() },
                    label = { Text("Заметка") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                if (noteText.isNotEmpty()) {
                    IconButton(
                        onClick = { noteText = ""; saveData() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 16.dp).size(28.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(
                    onClick = { showPhotoSourceDialog = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 12.dp).size(36.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Добавить фото", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("РЕШЕНИЕ:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HoldableActionButton(
                    text = "ПРИНЯТО",
                    onClick = {
                        decision = "ПРИНЯТО"
                        saveData()
                        StatusHapticHelper.onStatusApplied(sharedPrefs, context)
                        if (shouldSuggestTest(model) && testDate.isEmpty()) showTestDialog = true
                    },
                    modifier = Modifier.weight(1.1f).heightIn(min = 46.dp),
                    requireHold = touchProtectionMode == TouchProtectionMode.STRONG,
                    containerColor = if (decision == "ПРИНЯТО") Color(0xFF2E7D32) else Color(0xFF4CAF50),
                    contentColor = Color.White,
                    contentPadding = PaddingValues(0.dp),
                    textStyle = MaterialTheme.typography.labelSmall
                )

                HoldableActionButton(
                    text = "ДОРАБОТКА",
                    onClick = {
                        decision = "ДОРАБОТКА"
                        saveData()
                        if (shouldSuggestTest(model) && testDate.isEmpty()) {
                            showTestDialog = true
                        } else {
                            showReworkDialog = true
                        }
                    },
                    modifier = Modifier.weight(1.1f).heightIn(min = 46.dp),
                    requireHold = touchProtectionMode == TouchProtectionMode.STRONG,
                    containerColor = if (decision == "ДОРАБОТКА") Color(0xFFF9A825) else Color(0xFFFFEB3B),
                    contentColor = Color.Black,
                    contentPadding = PaddingValues(0.dp),
                    textStyle = MaterialTheme.typography.labelSmall
                )

                HoldableActionButton(
                    text = "НЕ ГОТОВО",
                    onClick = {
                        decision = "НЕ ГОТОВО"
                        saveData()
                        StatusHapticHelper.onStatusApplied(sharedPrefs, context)
                    },
                    modifier = Modifier.weight(0.8f).heightIn(min = 46.dp),
                    requireHold = touchProtectionMode == TouchProtectionMode.STRONG,
                    containerColor = if (decision == "НЕ ГОТОВО" || decision == "НЕ ПРИНЯТО") Color(0xFFC62828) else Color(0xFFF44336),
                    contentColor = Color.White,
                    contentPadding = PaddingValues(0.dp),
                    textStyle = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showExtraPhotos = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Посмотреть прикрепленные фото", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ================= ПОЛНОЭКРАННЫЙ ПРОСМОТР =================

@Composable
fun FullScreenImageDialog(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offset += panChange
        } else {
            offset = Offset.Zero
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDismiss() }) }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .memoryCacheKey(uri.toString())
                    .build(),
                contentDescription = "Просмотр макета",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
            }
        }
    }
}

// ================= ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ =================

fun loadExtraPhotos(context: Context, folderUri: String?, order: Order, article: String): List<Uri> {
    val result = mutableListOf<Uri>()
    if (folderUri == null) return result
    try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        val orderFolder = tree?.findFile(order.internalFolderName)
        if (orderFolder != null) {
            val searchName = "extra_${article.replace("/", "-").replace(" ", "-")}"
            orderFolder.listFiles().forEach { file ->
                if (file.name?.startsWith(searchName) == true) {
                    result.add(file.uri)
                }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return result
}

fun copyToOrderFolder(context: Context, sourceUri: Uri, folderUri: String, orderFolderName: String, article: String): Uri? {
    return try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        val orderFolder = tree?.findFile(orderFolderName) ?: return null
        val fileName = "extra_${article.replace("/", "-").replace(" ", "-")}_${System.currentTimeMillis()}.jpg"
        val newFile = orderFolder.createFile("image/jpeg", fileName) ?: return null
        if (copyDocumentContent(context, sourceUri, newFile)) newFile.uri else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun loadImagesFromFolderSAF(context: Context, folderUri: String?, order: Order): Map<String, List<Uri>> {
    val result = mutableMapOf<String, MutableList<Uri>>()
    if (folderUri == null) return result
    val articlesByBase = mutableMapOf<String, String>()
    order.models.forEach { model ->
        articlesByBase.putIfAbsent(normalizeArticle(model.article), model.article)
    }
    try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        val orderFolder = tree?.findFile(order.internalFolderName)
        if (orderFolder != null && orderFolder.isDirectory) {
            orderFolder.listFiles().forEach { file ->
                if (file.isFile && file.name?.startsWith("extra_") == false &&
                    (file.name?.endsWith(".jpg", true) == true || file.name?.endsWith(".png", true) == true || file.name?.endsWith(".jpeg", true) == true)) {
                    val base = macroFileBaseName(file.name ?: "") ?: return@forEach
                    val matchingArticle = articlesByBase[base]
                    if (matchingArticle != null) {
                        val cacheKey = "${order.internalFolderName}_${matchingArticle}"
                        if (!result.containsKey(cacheKey)) result[cacheKey] = mutableListOf()
                        result[cacheKey]!!.add(file.uri)
                    }
                }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return result
}

fun parseExcel(inputStream: InputStream?, fileName: String): Order? {
    if (inputStream == null) return null
    try {
        inputStream.use { input ->
            WorkbookFactory.create(input).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val formatter = org.apache.poi.ss.usermodel.DataFormatter()
                var currentDate = "Дата не найдена"
                val currentModels = mutableListOf<ModelInfo>()

                val cleanName = fileName.substringBeforeLast(".")
                val parts = cleanName.split("_")
                val factory = if (parts.isNotEmpty()) parts[0] else "Unknown"
                val deliveryNum = extractDeliveryNum(cleanName)
                val formatFromName = extractFormat(cleanName)
                val commentsColIdx = findCommentsColumnIndex(sheet).let { if (it >= 0) it else 19 }

                for (row in sheet) {
                    val modelCell = row.getCell(2)
                    if (modelCell != null && !modelCell.toString().contains("Артикул") && !modelCell.toString().contains("款号")) {
                        val article = modelCell.toString().trim()
                        if (article.isNotEmpty()) {
                            val brand = formatter.formatCellValue(row.getCell(5)).trim(); val color = formatter.formatCellValue(row.getCell(6)).trim()
                            val upper = formatter.formatCellValue(row.getCell(7)).trim(); val lining = formatter.formatCellValue(row.getCell(8)).trim()
                            val insole = formatter.formatCellValue(row.getCell(9)).trim(); val outsole = formatter.formatCellValue(row.getCell(10)).trim()
                            val pairsPerBox = formatter.formatCellValue(row.getCell(11)).trim(); val sizeBoxes = formatter.formatCellValue(row.getCell(12)).trim()
                            val mixedBoxes = formatter.formatCellValue(row.getCell(13)).trim(); val mixedCount = formatter.formatCellValue(row.getCell(14)).trim()
                            var sizeBoxesCount = 0
                            if (sizeBoxes.isNotEmpty()) { sizeBoxes.split(";").forEach { part -> part.substringAfter("-").trim().toIntOrNull()?.let { sizeBoxesCount += it } } }
                            if (currentDate == "Дата не найдена") { val dateCell = row.getCell(15); if (dateCell != null) { val d = formatter.formatCellValue(dateCell); if (d.isNotEmpty() && !d.contains("Дата")) currentDate = d } }
                            val comments = formatter.formatCellValue(row.getCell(commentsColIdx)).trim()
                            currentModels.add(ModelInfo(article, brand, color, upper, lining, insole, outsole, pairsPerBox, sizeBoxes, sizeBoxesCount, mixedBoxes, mixedCount, comments))
                        }
                    }
                }
                if (currentModels.isNotEmpty()) {
                    val internalName = "${factory}_${formatFromName}_${deliveryNum}_${currentDate.replace(".", "_")}"
                    return Order(fileName, internalName, factory, formatFromName, deliveryNum, currentDate, currentModels)
                }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return null
}

@Composable
fun MaterialCheckItem(title: String, value: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(value.ifEmpty { "-" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(value.ifEmpty { "-" }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CalendarDialog(
    datesWithChecks: Set<String>,
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    onResetDate: () -> Unit,
    onDismiss: () -> Unit
) {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val startOffset = mondayFirstDayOffset(year, month)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Даты приемки", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                var currentDay = 1
                for (week in 0..5) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (dayOfWeek in 0..6) {
                            if ((week == 0 && dayOfWeek < startOffset) || currentDay > daysInMonth) {
                                Spacer(modifier = Modifier.weight(1f).height(48.dp))
                            } else {
                                val dateStr = String.format("%02d.%02d.%d", currentDay, month + 1, year)
                                val hasChecks = datesWithChecks.contains(dateStr)
                                val isSelected = dateStr == selectedDate
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else if (hasChecks) acceptedBg() else Color.Transparent, RoundedCornerShape(8.dp))
                                        .safeClickable { onDateSelected(dateStr) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(currentDay.toString(), style = MaterialTheme.typography.bodyMedium, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
                                }
                                currentDay++
                            }
                        }
                    }
                }
                if (selectedDate.isNotEmpty()) {
                    TextButton(onClick = onResetDate) { Text("Сбросить дату", color = Color.Red) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}