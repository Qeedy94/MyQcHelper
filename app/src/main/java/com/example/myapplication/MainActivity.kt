package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
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
) {
    fun getDisplayFolderName(): String = "${factory}_${format}_${deliveryNum}_${date.replace(".", "_")}"
}

// ================= ВСПОМОГАТЕЛЬНЫЕ МОДИФИКАТОРЫ =================

fun Modifier.safeClickable(onClick: () -> Unit) = composed {
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val up = waitForUpOrCancellation()
            if (up != null) {
                onClick()
            }
        }
    }
}

fun Modifier.safeCombinedClickable(onTap: () -> Unit, onLongPress: () -> Unit) = composed {
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
            var isLongPress = false
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                kotlinx.coroutines.delay(longPressTimeout)
                isLongPress = true
                onLongPress()
            }
            val up = waitForUpOrCancellation()
            job.cancel()
            if (up != null && !isLongPress) {
                onTap()
            }
        }
    }
}

// ================= ЛОГИКА ПРОВЕРКИ ТЕСТА =================

fun shouldSuggestTest(model: ModelInfo): Boolean {
    val isTextileLining = model.lining.contains("натуральный", true)
    val sizes = model.sizeBoxes.split(";").mapNotNull { it.substringBefore("-").trim().toIntOrNull() }
    val hasSmallSizes = sizes.any { it < 27 }
    return isTextileLining && hasSmallSizes
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
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) Color(0xFF3D8DFF) else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (checked) Color(0xFF3D8DFF) else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = "Выбрано",
            tint = Color.White,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
        )
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

fun loadSavedOrders(sharedPrefs: android.content.SharedPreferences): List<Order> {
    return try {
        val json = sharedPrefs.getString("saved_orders", null)
        if (json != null) {
            val type = object : com.google.gson.reflect.TypeToken<List<Order>>() {}.type
            val loaded: List<Order> = Gson().fromJson(json, type) ?: emptyList()
            loaded.map { order ->
                if (order.internalFolderName.isBlank()) {
                    order.copy(internalFolderName = "${order.factory}_${order.format}_${order.deliveryNum}_${order.date.replace(".", "_")}")
                } else order
            }
        } else emptyList()
    } catch (e: Exception) { emptyList() }
}

fun getOrderStats(order: Order, sharedPrefs: android.content.SharedPreferences): Triple<Int, Int, Int> {
    var accepted = 0
    var rejected = 0
    var rework = 0
    order.models.forEach { model ->
        when (sharedPrefs.getString("${model.article}_decision", "")) {
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
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true

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

    var orders by remember { mutableStateOf<List<Order>>(loadSavedOrders(sharedPrefs)) }
    var imagesFolderUri by remember { mutableStateOf(sharedPrefs.getString("images_folder_uri", null)) }
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
        orders = newOrders
        scope.launch(Dispatchers.IO) {
            try {
                val json = gson.toJson(newOrders)
                sharedPrefs.edit().putString("saved_orders", json).apply()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun autoScan() {
        val currentUri = imagesFolderUri
        if (currentUri == null || isAutoScanning) return

        isAutoScanning = true
        scope.launch(Dispatchers.IO) {
            try {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(currentUri))
                val excelFiles = tree?.listFiles()?.filter {
                    it.name?.endsWith(".xlsx", true) == true || it.name?.endsWith(".xls", true) == true
                } ?: emptyList()

                var newOrdersList = orders.toMutableList()
                var addedCount = 0

                for (file in excelFiles) {
                    val inputStream = context.contentResolver.openInputStream(file.uri)
                    val order = parseExcel(inputStream, file.name ?: "")
                    if (order != null) {
                        if (newOrdersList.none { it.id == order.id }) {
                            newOrdersList.add(order)
                            createOrderFolderSAF(context, currentUri, order.internalFolderName)
                            addedCount++
                        }
                    }
                }

                if (addedCount > 0) {
                    withContext(Dispatchers.Main) {
                        saveOrders(newOrdersList)
                        Toast.makeText(context, "Авто-обновление: добавлено $addedCount заказов", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
            } finally {
                isAutoScanning = false
            }
        }
    }

    LaunchedEffect(selectedTab, imagesFolderUri) {
        if (selectedTab == 0 && imagesFolderUri != null) {
            autoScan()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            imagesFolderUri = uri.toString()
            sharedPrefs.edit().putString("images_folder_uri", uri.toString()).apply()
            imagesCache.clear()
            Toast.makeText(context, "Папка выбрана!", Toast.LENGTH_SHORT).show()
        }
    }

    if (imagesFolderUri == null) {
        AlertDialog(
            onDismissRequest = { },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Выбор папки", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Выберите папку, куда вы будете складывать Excel-файлы и макеты.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { Button(onClick = { folderPickerLauncher.launch(null) }, shape = RoundedCornerShape(16.dp)) { Text("Выбрать", style = MaterialTheme.typography.labelLarge) } }
        )
    }

    BackHandler(enabled = calendarSelectedOrderId != null) {
        calendarSelectedOrderId = null
    }

    BackHandler(enabled = selectedModel != null) {
        selectedModelArticle = null
        if (cameFromArticleSearch) {
            selectedOrderId = null
            cameFromArticleSearch = false
        }
    }
    BackHandler(enabled = selectedModel == null && selectedOrder != null) {
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
                    modifier = Modifier.height(56.dp)
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
                            onCacheClear = { imagesCache.clear() },
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
                            { article -> sharedPrefs.getString("${article}_decision", "") ?: "" },
                            { article -> sharedPrefs.getString("${article}_date", "") ?: "" },
                            { article -> sharedPrefs.getString("${article}_testDate", "") ?: "" },
                            { article -> sharedPrefs.getString("${article}_testReceivedDate", "") ?: "" },
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
                    items(displayOrders) { order ->
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
    var fullScreenImage by remember { mutableStateOf<Uri?>(null) }
    var imageToDelete by remember { mutableStateOf<Uri?>(null) }

    var orphanActionImage by remember { mutableStateOf<Uri?>(null) }
    var showModelListForLink by remember { mutableStateOf(false) }
    var modelToManualAttach by remember { mutableStateOf<ModelInfo?>(null) }

    var isLoading by remember { mutableStateOf(true) }
    var modelsWithoutImages by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }

    val manualPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && modelToManualAttach != null && imagesFolderUri != null) {
            val articleName = modelToManualAttach!!.article.replace("/", "-").replace(" ", "-")
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(imagesFolderUri))
            val orderFolder = tree?.findFile(order.internalFolderName)

            if (orderFolder != null) {
                orderFolder.listFiles().forEach { file ->
                    val name = file.name ?: ""
                    val base = macroFileBaseName(name)
                    if (base != null && base == normalizeArticle(articleName) &&
                        (name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".jpeg", true))) {
                        file.delete()
                    }
                }

                val fileName = "${articleName}.jpg"
                val newFile = orderFolder.createFile("image/jpeg", fileName)

                if (newFile != null) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val outputStream = context.contentResolver.openOutputStream(newFile.uri)
                    if (inputStream != null && outputStream != null) {
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        Toast.makeText(context, "Фото привязано", Toast.LENGTH_SHORT).show()
                        isLoading = true
                    }
                }
            }
            modelToManualAttach = null
        }
    }

    fun attachExistingOrphanToModel(targetModel: ModelInfo) {
        val srcUri = orphanActionImage ?: return
        val uri = imagesFolderUri ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(uri))
                val orderFolder = tree?.findFile(order.internalFolderName)
                if (orderFolder != null) {
                    val articleName = targetModel.article.replace("/", "-").replace(" ", "-")

                    orderFolder.listFiles().forEach { file ->
                        val name = file.name ?: ""
                        val base = macroFileBaseName(name)
                        if (base != null && base == normalizeArticle(articleName) &&
                            (name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".jpeg", true))) {
                            file.delete()
                        }
                    }

                    val newFile = orderFolder.createFile("image/jpeg", "${articleName}.jpg")
                    if (newFile != null) {
                        val inputStream = context.contentResolver.openInputStream(srcUri)
                        val outputStream = context.contentResolver.openOutputStream(newFile.uri)
                        if (inputStream != null && outputStream != null) {
                            inputStream.copyTo(outputStream)
                            inputStream.close()
                            outputStream.close()
                            DocumentFile.fromSingleUri(context, srcUri)?.delete()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Привязано к ${targetModel.article}", Toast.LENGTH_SHORT).show()
                                orphanActionImage = null
                                isLoading = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка привязки", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && imagesFolderUri != null) {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(imagesFolderUri))
            val orderFolder = tree?.findFile(order.internalFolderName)

            if (orderFolder != null) {
                uris.forEach { uri ->
                    val fileName = "extra_gallery_${System.currentTimeMillis()}_${(0..999).random()}.jpg"
                    val newFile = orderFolder.createFile("image/jpeg", fileName)
                    if (newFile != null) {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val outputStream = context.contentResolver.openOutputStream(newFile.uri)
                        if (inputStream != null && outputStream != null) {
                            inputStream.copyTo(outputStream); inputStream.close(); outputStream.close()
                        }
                    }
                }
                Toast.makeText(context, "Добавлено фото: ${uris.size}", Toast.LENGTH_SHORT).show()
                isLoading = true
            }
        }
    }

    LaunchedEffect(imagesFolderUri, order.internalFolderName, isLoading) {
        if (imagesFolderUri != null && isLoading) {
            withContext(Dispatchers.IO) {
                allImages = loadAllImagesFromFolder(context, imagesFolderUri, order)
                modelsWithoutImages = getModelsWithoutImages(order, allImages, context)
            }
            isLoading = false
        }
    }

    val sortedImages = remember(allImages) {
        allImages.sortedByDescending { uri ->
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: ""
            !isImageLinkedToModel(name, order.models)
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
        SheetMenuItem(
            text = "Удалить",
            color = Color.Red,
            icon = Icons.Default.Delete,
            onClick = {
                val target = imageToDelete!!
                imageToDelete = null
                val docFile = DocumentFile.fromSingleUri(context, target)
                docFile?.delete()
                allImages = allImages.filter { it != target }
                modelsWithoutImages = getModelsWithoutImages(order, allImages, context)
                Toast.makeText(context, "Фото удалено", Toast.LENGTH_SHORT).show()
            }
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
                items(modelsWithoutImages) { model ->
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
                title = { Text("${order.factory} ${order.format} ${order.deliveryNum}", style = MaterialTheme.typography.titleMedium) },
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
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
                    items(sortedImages.size, key = { it }) { index ->
                        val uri = sortedImages[index]
                        val docFile = DocumentFile.fromSingleUri(context, uri)
                        val fileName = docFile?.name ?: ""
                        val isOrphan = !isImageLinkedToModel(fileName, order.models)
                                && macroFileBaseName(fileName) != null

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isOrphan) 3.dp else 0.dp,
                                    color = if (isOrphan) Color.Red else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .pointerInput(Unit) {
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
                                model = ImageRequest.Builder(context)
                                    .data(uri)
                                    .memoryCacheKey(uri.toString())
                                    .build(),
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

fun getModelsWithoutImages(order: Order, allImages: List<Uri>, context: Context): List<ModelInfo> {
    val macroNames = allImages.mapNotNull { uri ->
        val docFile = DocumentFile.fromSingleUri(context, uri)
        val fileName = docFile?.name ?: ""
        macroFileBaseName(fileName)
    }.toSet()

    return order.models.filter { model ->
        val key = normalizeArticle(model.article)
        key !in macroNames
    }
}

fun isImageLinkedToModel(fileName: String, models: List<ModelInfo>): Boolean {
    val base = macroFileBaseName(fileName) ?: return false
    return models.any { model ->
        normalizeArticle(model.article) == base
    }
}

fun loadAllImagesFromFolder(context: Context, folderUri: String?, order: Order): List<Uri> {
    val result = mutableListOf<Uri>()
    if (folderUri == null) return result
    try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        val orderFolder = tree?.findFile(order.internalFolderName)
        if (orderFolder != null && orderFolder.isDirectory) {
            orderFolder.listFiles().forEach { file ->
                if (file.isFile && (file.name?.endsWith(".jpg", true) == true || file.name?.endsWith(".png", true) == true || file.name?.endsWith(".jpeg", true) == true)) {
                    result.add(file.uri)
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
                val decision = sharedPrefs.getString("${model.article}_decision", "") ?: ""
                val date = sharedPrefs.getString("${model.article}_date", "") ?: ""
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
        Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }.get(Calendar.DAY_OF_WEEK) - 1
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
                Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(4.dp))

        var day = 1
        for (week in 0..5) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (dayOfWeek in 0..6) {
                    if ((week == 0 && dayOfWeek < firstDayOfWeek) || day > daysInMonth) {
                        Spacer(modifier = Modifier.size(40.dp))
                    } else {
                        val dateStr = String.format("%02d.%02d.%d", day, currentMonth + 1, currentYear)
                        val hasWork = dayCounts.containsKey(dateStr)
                        val isSelected = dateStr == selectedDate
                        val count = dayCounts[dateStr] ?: 0

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        hasWork -> Color(0xFFC8E6C9)
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
                                    color = if (isSelected) Color.White else if (hasWork) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                )
                                if (hasWork && !isSelected) {
                                    Text(
                                        count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF2E7D32)
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
                                val decision = sharedPrefs.getString("${model.article}_decision", "") ?: ""
                                val note = sharedPrefs.getString("${model.article}_note", "") ?: ""
                                val testDate = sharedPrefs.getString("${model.article}_testDate", "") ?: ""
                                val testReceived = sharedPrefs.getString("${model.article}_testReceivedDate", "") ?: ""

                                val cardColor = when (decision) {
                                    "ПРИНЯТО" -> Color(0xFFC8E6C9)
                                    "ДОРАБОТКА" -> Color(0xFFFFF9C4)
                                    "НЕ ГОТОВО", "НЕ ПРИНЯТО" -> Color(0xFFFFCDD2)
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
                                            Text(decision, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                                        }
                                        if (note.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(note, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (testDate.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            if (testReceived.isNotEmpty()) {
                                                Text("Тест: ✓ $testReceived", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                            } else {
                                                Text("На тесте с: $testDate", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF57C00))
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
    val versionName = remember(context) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "?"
        } catch (e: Exception) { "?" }
    }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }

    var showThemeMenu by remember { mutableStateOf(false) }
    val themeLabel = when (themeMode) {
        "light" -> "Светлая"
        "dark" -> "Темная"
        else -> "Системная"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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

        Button(
            onClick = {
                scope.launch {
                    isCheckingUpdate = true
                    val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
                    val info = UpdateManager.checkForUpdate(current)
                    if (info != null) {
                        updateInfo = info
                        showUpdateDialog = true
                    } else {
                        Toast.makeText(context, "У вас последняя версия", Toast.LENGTH_SHORT).show()
                    }
                    isCheckingUpdate = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            enabled = !isCheckingUpdate
        ) {
            if (isCheckingUpdate) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Проверить обновления", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.weight(1f))
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
            onDismissRequest = { showUpdateDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Доступно обновление", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Новая версия ${updateInfo!!.version}. Скачать и установить?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        showUpdateDialog = false
                        Toast.makeText(context, "Скачивание...", Toast.LENGTH_SHORT).show()
                        val apk = UpdateManager.downloadApk(context, updateInfo!!.downloadUrl)
                        if (apk != null) {
                            UpdateManager.installApk(context, apk)
                        } else {
                            Toast.makeText(context, "Ошибка скачивания", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Обновить") }
            },
            dismissButton = { TextButton(onClick = { showUpdateDialog = false }) { Text("Позже") } }
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
    onCacheClear: () -> Unit,
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
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val order = parseExcel(inputStream, "Manual_Order.xlsx")
                    if (order != null) {
                        if (imagesFolderUri != null) createOrderFolderSAF(context, imagesFolderUri, order.internalFolderName)
                        withContext(Dispatchers.Main) {
                            onFileLoaded(orders + order)
                            Toast.makeText(context, "Загружен: ${order.factory}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    val filteredOrders = remember(orders, searchQuery, selectedFactories, searchType) {
        if (searchQuery.isEmpty()) {
            orders
                .filter { if (selectedFactories.isEmpty()) true else it.factory in selectedFactories }
                .sortedWith(compareBy { try { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(it.date) } catch (e: Exception) { Date(Long.MAX_VALUE) } })
        } else if (searchType == "По заказу") {
            orders
                .filter { it.factory.contains(searchQuery, true) || it.deliveryNum.contains(searchQuery, true) || it.format.contains(searchQuery, true) }
                .filter { if (selectedFactories.isEmpty()) true else it.factory in selectedFactories }
        } else {
            emptyList()
        }
    }

    val foundModels = remember(orders, searchQuery, searchType) {
        if (searchQuery.isNotEmpty() && searchType == "По артикулу") {
            val list = mutableListOf<Pair<Order, ModelInfo>>()
            orders.forEach { o -> o.models.forEach { m -> if (m.article.contains(searchQuery, true)) list.add(o to m) } }
            list
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
        SheetMenuItem(
            text = "Удалить все",
            color = Color.Red,
            icon = Icons.Default.Delete,
            onClick = { onClearOrders(); showClearDialog = false }
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
                TextButton(onClick = {
                    if (imagesFolderUri != null) {
                        deleteOrderFolderSAF(context, imagesFolderUri, orderToDelete!!.internalFolderName)
                        deleteExcelFileSAF(context, imagesFolderUri, orderToDelete!!.id)
                    }
                    onFileLoaded(orders.filter { it != orderToDelete })
                    orderToDelete = null
                    Toast.makeText(context, "Заказ удален", Toast.LENGTH_SHORT).show()
                }) { Text("Удалить", color = Color.Red) }
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
                    modifier = Modifier.fillMaxWidth().height(50.dp),
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
                    items(foundModels) { (order, model) ->
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
                        val notAccepted = rejected + rework
                        val overdueDays = getOverdueDays(order.date)
                        val isAllAccepted = accepted > 0 && accepted == order.models.size
                        val bgColor = if (isAllAccepted) Color(0xFFCBEFD1) else MaterialTheme.colorScheme.surface

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
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            order.factory,
                                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp)
                                        )
                                        Text(
                                            "${order.format} | ${order.deliveryNum}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Дата: ${order.date}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (overdueDays > 0) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Просрочено: $overdueDays дн.",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = Color(0xFFD32F2F)
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
                                        Text("$notAccepted", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Color(0xFFF44336))
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

fun createOrderFolderSAF(context: Context, folderUri: String, folderName: String) {
    try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        if (tree?.findFile(folderName) == null) tree?.createDirectory(folderName)
    } catch (e: Exception) { e.printStackTrace() }
}

fun deleteOrderFolderSAF(context: Context, folderUri: String, folderName: String) {
    try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        val folder = tree?.findFile(folderName)
        folder?.delete()
    } catch (e: Exception) { e.printStackTrace() }
}

fun deleteExcelFileSAF(context: Context, folderUri: String, fileName: String) {
    try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        val file = tree?.findFile(fileName)
        file?.delete()
    } catch (e: Exception) { e.printStackTrace() }
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

    LaunchedEffect(isSelectionMode) {
        onSelectionModeChange(isSelectionMode)
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedArticles = emptySet()
    }

    val datesWithChecks = remember(order.models, order.id) {
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
                        val oldNote = sharedPrefs.getString("${article}_note", "") ?: ""
                        val newNote = if (oldNote.isEmpty()) bulkCommentText else "$oldNote\n$bulkCommentText"
                        editor.putString("${article}_note", newNote)
                    }
                    editor.commit()

                    Toast.makeText(context, "Добавлено", Toast.LENGTH_SHORT).show()
                    showCommentDialog = false
                    bulkCommentText = ""
                    isSelectionMode = false
                    selectedArticles = emptySet()
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
                TextButton(onClick = {
                    val target = modelToDelete!!
                    modelToDelete = null
                    val newOrder = order.copy(models = order.models.filter { it != target })
                    onOrdersUpdated(allOrders.map { if (it.id == order.id) newOrder else it })
                }) { Text("Удалить", color = Color.Red) }
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
                TextButton(onClick = {
                    val newOrder = order.copy(models = order.models.filter { it.article !in selectedArticles })
                    onOrdersUpdated(allOrders.map { if (it.id == order.id) newOrder else it })
                    showDeleteSelectedDialog = false
                    isSelectionMode = false
                    selectedArticles = emptySet()
                }) { Text("Удалить", color = Color.Red) }
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
                if (imagesFolderUri != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val file = DocumentFile.fromTreeUri(context, Uri.parse(imagesFolderUri))?.findFile(order.id)
                            if (file != null) {
                                val parsed = parseExcel(context.contentResolver.openInputStream(file.uri), file.name ?: order.id)
                                val currentArticles = order.models.map { it.article }.toSet()
                                val deleted = parsed?.models?.filter { it.article !in currentArticles } ?: emptyList()
                                withContext(Dispatchers.Main) { deletedModelsList = deleted }
                            }
                        } catch (e: Exception) { }
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

    val filteredModels = remember(order.models, searchQuery, selectedDate, activeSortOptions) {
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
                            onClick = {
                                val needsTest = selectedArticles.any { article ->
                                    val m = order.models.find { it.article == article }
                                    m != null && shouldSuggestTest(m)
                                }
                                val doAction = {
                                    val editor = sharedPrefs.edit()
                                    val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                                    selectedArticles.forEach { article ->
                                        editor.putString("${article}_decision", "ПРИНЯТО")
                                        editor.putString("${article}_date", date)
                                        val m = order.models.find { it.article == article }
                                        if (m != null && shouldSuggestTest(m)) {
                                            val existingTestDate = sharedPrefs.getString("${article}_testDate", "") ?: ""
                                            if (existingTestDate.isEmpty()) {
                                                editor.putString("${article}_testDate", date)
                                            }
                                        }
                                    }
                                    editor.commit()
                                    isSelectionMode = false
                                    selectedArticles = emptySet()
                                    Toast.makeText(context, "Статус ПРИНЯТО установлен", Toast.LENGTH_SHORT).show()
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
                            onClick = {
                                val needsTest = selectedArticles.any { article ->
                                    val m = order.models.find { it.article == article }
                                    m != null && shouldSuggestTest(m)
                                }
                                val doAction = {
                                    val editor = sharedPrefs.edit()
                                    val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                                    selectedArticles.forEach { article ->
                                        editor.putString("${article}_decision", "ДОРАБОТКА")
                                        editor.putString("${article}_date", date)
                                        val m = order.models.find { it.article == article }
                                        if (m != null && shouldSuggestTest(m)) {
                                            val existingTestDate = sharedPrefs.getString("${article}_testDate", "") ?: ""
                                            if (existingTestDate.isEmpty()) {
                                                editor.putString("${article}_testDate", date)
                                            }
                                        }
                                    }
                                    editor.commit()
                                    isSelectionMode = false
                                    selectedArticles = emptySet()
                                    Toast.makeText(context, "Статус ДОРАБОТКА установлен", Toast.LENGTH_SHORT).show()
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
                            icon = Icons.Default.Close,
                            label = "Не готово",
                            tint = Color(0xFFF44336),
                            onClick = {
                                val editor = sharedPrefs.edit()
                                val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                                selectedArticles.forEach { article ->
                                    editor.putString("${article}_decision", "НЕ ГОТОВО")
                                    editor.putString("${article}_date", date)
                                }
                                editor.commit()
                                isSelectionMode = false
                                selectedArticles = emptySet()
                                Toast.makeText(context, "Статус НЕ ГОТОВО установлен", Toast.LENGTH_SHORT).show()
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(50.dp),
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
                    val note = sharedPrefs.getString("${model.article}_note", "") ?: ""
                    val statusDate = getDate(model.article)
                    val testSent = getTestDate(model.article)
                    val testReceived = getTestReceivedDate(model.article)
                    val isSelected = selectedArticles.contains(model.article)

                    val cardColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else when (status) {
                        "ПРИНЯТО" -> Color(0xFFC8E6C9)
                        "НЕ ПРИНЯТО", "НЕ ГОТОВО" -> Color(0xFFFFCDD2)
                        "ДОРАБОТКА" -> Color(0xFFFFF9C4)
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedArticles = if (isSelected) selectedArticles - model.article else selectedArticles + model.article
                                    } else onModelClick(model)
                                },
                                onLongClick = {
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
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    } else {
                                        Text(
                                            "На тесте с: $testSent",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFF57C00),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (status.isNotEmpty()) {
                                Text(status, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
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
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .safeClickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(tint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
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
    var fullScreenImage by remember { mutableStateOf<Uri?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showExtraPhotos by remember { mutableStateOf(false) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }

    var showResetDialog by remember { mutableStateOf(false) }

    var showReworkDialog by remember { mutableStateOf(false) }
    var reworkReasons by remember {
        mutableStateOf(sharedPrefs.getStringSet("rework_reasons", setOf("Брак", "Не тот цвет", "Плохое качество"))!!.toList())
    }
    var newReasonText by remember { mutableStateOf("") }
    var reasonToDelete by remember { mutableStateOf<String?>(null) }

    var isLoadingImages by remember { mutableStateOf(true) }
    var currentImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var extraPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }

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

    var upperChecked by remember { mutableStateOf(sharedPrefs.getBoolean("${model.article}_upper", false)) }
    var liningChecked by remember { mutableStateOf(sharedPrefs.getBoolean("${model.article}_lining", false)) }
    var insoleChecked by remember { mutableStateOf(sharedPrefs.getBoolean("${model.article}_insole", false)) }
    var outsoleChecked by remember { mutableStateOf(sharedPrefs.getBoolean("${model.article}_outsole", false)) }
    var decision by remember { mutableStateOf(sharedPrefs.getString("${model.article}_decision", "") ?: "") }
    var noteText by remember { mutableStateOf(sharedPrefs.getString("${model.article}_note", "") ?: "") }
    var testDate by remember { mutableStateOf(sharedPrefs.getString("${model.article}_testDate", "") ?: "") }
    var testReceivedDate by remember { mutableStateOf(sharedPrefs.getString("${model.article}_testReceivedDate", "") ?: "") }

    fun saveData(noteOverride: String? = null) {
        val finalNote = noteOverride ?: noteText
        sharedPrefs.edit().apply {
            putBoolean("${model.article}_upper", upperChecked)
            putBoolean("${model.article}_lining", liningChecked)
            putBoolean("${model.article}_insole", insoleChecked)
            putBoolean("${model.article}_outsole", outsoleChecked)
            putString("${model.article}_decision", decision)
            putString("${model.article}_note", finalNote)
            putString("${model.article}_testDate", testDate)
            putString("${model.article}_testReceivedDate", testReceivedDate)
            if (decision.isNotEmpty()) {
                val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                putString("${model.article}_date", date)
            }
            apply()
        }
    }

    fun resetData(deleteComment: Boolean) {
        sharedPrefs.edit().apply {
            remove("${model.article}_upper"); remove("${model.article}_lining")
            remove("${model.article}_insole"); remove("${model.article}_outsole")
            remove("${model.article}_decision")
            remove("${model.article}_date"); remove("${model.article}_testDate")
            remove("${model.article}_testReceivedDate")
            if (deleteComment) {
                remove("${model.article}_note")
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
        if (success && tempCameraUri != null && imagesFolderUri != null) {
            val savedUri = copyToOrderFolder(context, tempCameraUri!!, imagesFolderUri, currentOrder.internalFolderName, model.article)
            if (savedUri != null) {
                extraPhotos = extraPhotos + savedUri
                Toast.makeText(context, "Фото с камеры добавлено", Toast.LENGTH_SHORT).show()
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
            val savedUri = copyToOrderFolder(context, uri, imagesFolderUri, currentOrder.internalFolderName, model.article)
            if (savedUri != null) {
                extraPhotos = extraPhotos + savedUri
                Toast.makeText(context, "Фото добавлено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (reasonToDelete != null) {
        AlertDialog(
            onDismissRequest = { reasonToDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Удалить причину?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Удалить '${reasonToDelete!!}'?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = reasonToDelete!!
                    reworkReasons = reworkReasons.filter { it != toDelete }
                    sharedPrefs.edit().putStringSet("rework_reasons", reworkReasons.toSet()).apply()
                    reasonToDelete = null
                }) { Text("Удалить", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { reasonToDelete = null }) { Text("Отмена") } }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Сбросить проверку?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Все чекбоксы и статус будут сброшены. Удалить комментарий к этой модели?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    resetData(deleteComment = true)
                    showResetDialog = false
                    Toast.makeText(context, "Сброшено, комментарий удалён", Toast.LENGTH_SHORT).show()
                }) { Text("Да, удалить", color = Color.Red) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        resetData(deleteComment = false)
                        showResetDialog = false
                        Toast.makeText(context, "Сброшено, комментарий сохранён", Toast.LENGTH_SHORT).show()
                    }) { Text("Нет, оставить") }
                    TextButton(onClick = { showResetDialog = false }) { Text("Отмена") }
                }
            }
        )
    }

    AppBottomSheet(
        visible = showReworkDialog,
        onDismiss = { showReworkDialog = false },
        title = "Причина доработки"
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth()
        ) {
            items(
                count = reworkReasons.size,
                key = { index -> reworkReasons.getOrNull(index) ?: "empty_$index" }
            ) { index ->
                val reason = reworkReasons.getOrNull(index)
                if (reason != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(reason) {
                                detectTapGestures(
                                    onTap = {
                                        val newNote = if (noteText.isEmpty()) reason else "$noteText\n$reason"
                                        noteText = newNote
                                        saveData(noteOverride = newNote)
                                        showReworkDialog = false
                                    },
                                    onLongPress = { reasonToDelete = reason }
                                )
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF57C00),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(reason, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newReasonText,
                onValueChange = { newReasonText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Новая причина", style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (newReasonText.isNotBlank()) {
                        reworkReasons = reworkReasons + newReasonText
                        sharedPrefs.edit().putStringSet("rework_reasons", reworkReasons.toSet()).apply()
                        newReasonText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

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
                                    onClick = {
                                        val docFile = DocumentFile.fromSingleUri(context, uri)
                                        docFile?.delete()
                                        extraPhotos = extraPhotos.filter { it != uri }
                                    },
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

    // 👇 ДИАЛОГ ТЕСТА (открывается первым при доработке)
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

                    // 👇 После теста открываем причины доработки
                    if (decision == "ДОРАБОТКА") {
                        showReworkDialog = true
                    }
                }) { Text("Отправить на тест") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTestDialog = false
                    // 👇 Если закрыли без теста, все равно показываем причины
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
                            Text(model.article, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${currentOrder.factory} ${currentOrder.format} ${currentOrder.deliveryNum}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (decision.isNotEmpty()) {
                                Text(decision, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (testReceivedDate.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text("Тест: ✓ $testReceivedDate", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                            } else if (testDate.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text("На тесте: $testDate", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF57C00))
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
                    if (noteText.isNotEmpty()) {
                        showResetDialog = true
                    } else {
                        resetData(deleteComment = false)
                        Toast.makeText(context, "Проверка сброшена", Toast.LENGTH_SHORT).show()
                    }
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
                Text("Тест получен: $testReceivedDate", style = MaterialTheme.typography.titleSmall, color = Color(0xFF2E7D32))
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
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
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
                Button(
                    onClick = {
                        decision = "ПРИНЯТО"
                        saveData()
                        if (shouldSuggestTest(model) && testDate.isEmpty()) showTestDialog = true
                    },
                    modifier = Modifier.weight(1.1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (decision == "ПРИНЯТО") Color(0xFF2E7D32) else Color(0xFF4CAF50))
                ) { Text("ПРИНЯТО", style = MaterialTheme.typography.labelSmall) }

                Button(
                    onClick = {
                        decision = "ДОРАБОТКА"
                        saveData()

                        if (shouldSuggestTest(model) && testDate.isEmpty()) {
                            showTestDialog = true
                        } else {
                            showReworkDialog = true
                        }
                    },
                    modifier = Modifier.weight(1.1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (decision == "ДОРАБОТКА") Color(0xFFF9A825) else Color(0xFFFFEB3B),
                        contentColor = Color.Black
                    )
                ) { Text("ДОРАБОТКА", style = MaterialTheme.typography.labelSmall) }

                Button(
                    onClick = {
                        decision = "НЕ ГОТОВО"
                        saveData()
                    },
                    modifier = Modifier.weight(0.8f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (decision == "НЕ ГОТОВО" || decision == "НЕ ПРИНЯТО") Color(0xFFC62828) else Color(0xFFF44336))
                ) { Text("НЕ ГОТОВО", style = MaterialTheme.typography.labelSmall) }
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

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent) // 👈 Прозрачный фон
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .memoryCacheKey(uri.toString())
                    .build(),
                contentDescription = "Просмотр",
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
    try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        val orderFolder = tree?.findFile(orderFolderName) ?: return null
        val fileName = "extra_${article.replace("/", "-").replace(" ", "-")}_${System.currentTimeMillis()}.jpg"
        val newFile = orderFolder.createFile("image/jpeg", fileName)
        if (newFile != null) {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val outputStream = context.contentResolver.openOutputStream(newFile.uri)
            if (inputStream != null && outputStream != null) {
                inputStream.copyTo(outputStream); inputStream.close(); outputStream.close()
                return newFile.uri
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return null
}

fun loadImagesFromFolderSAF(context: Context, folderUri: String?, order: Order): Map<String, List<Uri>> {
    val result = mutableMapOf<String, MutableList<Uri>>()
    if (folderUri == null) return result
    try {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        val orderFolder = tree?.findFile(order.internalFolderName)
        if (orderFolder != null && orderFolder.isDirectory) {
            orderFolder.listFiles().forEach { file ->
                if (file.isFile && file.name?.startsWith("extra_") == false &&
                    (file.name?.endsWith(".jpg", true) == true || file.name?.endsWith(".png", true) == true || file.name?.endsWith(".jpeg", true) == true)) {
                    val base = macroFileBaseName(file.name ?: "") ?: return@forEach
                    val matchingArticle = order.models.find { model -> normalizeArticle(model.article) == base }?.article
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
        val workbook = WorkbookFactory.create(inputStream)
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
        workbook.close(); inputStream.close()
        if (currentModels.isNotEmpty()) {
            val internalName = "${factory}_${formatFromName}_${deliveryNum}_${currentDate.replace(".", "_")}"
            return Order(fileName, internalName, factory, formatFromName, deliveryNum, currentDate, currentModels)
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
    val firstDayOfWeek = calendar.clone() as Calendar
    firstDayOfWeek.set(Calendar.DAY_OF_MONTH, 1)
    val startOffset = firstDayOfWeek.get(Calendar.DAY_OF_WEEK) - 1

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
                                Spacer(modifier = Modifier.size(40.dp))
                            } else {
                                val dateStr = String.format("%02d.%02d.%d", currentDay, month + 1, year)
                                val hasChecks = datesWithChecks.contains(dateStr)
                                val isSelected = dateStr == selectedDate
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else if (hasChecks) Color(0xFFC8E6C9) else Color.Transparent, RoundedCornerShape(8.dp))
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