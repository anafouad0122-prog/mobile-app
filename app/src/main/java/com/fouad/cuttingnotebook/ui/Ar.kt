package com.fouad.cuttingnotebook.ui

import com.fouad.cuttingnotebook.data.backup.BackupError
import com.fouad.cuttingnotebook.data.settings.AccentColor
import com.fouad.cuttingnotebook.data.settings.PinError
import com.fouad.cuttingnotebook.data.settings.ThemeMode
import com.fouad.cuttingnotebook.domain.AppError
import com.fouad.cuttingnotebook.domain.PieceCountError
import com.fouad.cuttingnotebook.domain.ReportPeriod
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every piece of Arabic in the interface.
 *
 * The app ships in one language, so the strings live in Kotlin rather than in strings.xml:
 * the parameterised ones stay readable, and nothing can drift out of sync with a format
 * argument. Only the launcher label is a resource, because the manifest needs it there.
 */
object Ar {

    // ---- shared ------------------------------------------------------------------------
    const val APP_NAME = "دفتر القص"
    const val SAVE = "حفظ"
    const val CANCEL = "إلغاء"
    const val CONFIRM = "تأكيد"
    const val EDIT = "تعديل"
    const val BACK = "رجوع"
    const val CLOSE = "إغلاق"
    const val SEARCH = "بحث"
    const val OK = "تمام"
    const val NOTES = "ملاحظات"
    const val NOTES_OPTIONAL = "ملاحظات (اختياري)"
    const val OPTIONAL = "اختياري"
    const val NONE = "لا يوجد"
    const val TOTAL = "الإجمالي"

    // ---- quantity vocabulary -----------------------------------------------------------
    const val REQUIRED = "المطلوب"
    const val CUT = "المقصوص"
    const val REMAINING = "المتبقي"
    const val EXCESS = "الزيادة"
    const val COLOR = "اللون"
    const val SIZE = "المقاس"
    const val QUANTITY = "الكمية"
    const val PIECES = "قطعة"

    fun pieces(count: Int): String = when {
        count == 1 -> "قطعة واحدة"
        count == 2 -> "قطعتان"
        count in 3..10 -> "$count قطع"
        else -> "$count قطعة"
    }

    // ---- lock screen -------------------------------------------------------------------
    const val LOCK_TITLE = "أدخل الرقم السري"
    const val LOCK_SETUP_TITLE = "اختر رقمًا سريًا"
    const val LOCK_SETUP_HINT = "من ٤ إلى ١٢ رقمًا. هذا الرقم يحمي بيانات الدفتر على الهاتف."
    const val PIN = "الرقم السري"
    const val PIN_CONFIRM = "أعد كتابة الرقم السري"
    const val PIN_CURRENT = "الرقم السري الحالي"
    const val PIN_NEW = "الرقم السري الجديد"
    const val UNLOCK = "فتح"
    const val PIN_WRONG = "الرقم السري غير صحيح."
    const val CHANGE_PIN = "تغيير الرقم السري"
    const val PIN_CHANGED = "تم تغيير الرقم السري."

    fun pinError(error: PinError): String = when (error) {
        PinError.TOO_SHORT -> "الرقم السري قصير. أقل عدد ٤ أرقام."
        PinError.TOO_LONG -> "الرقم السري طويل. أكبر عدد ١٢ رقمًا."
        PinError.NOT_DIGITS -> "اكتب أرقامًا فقط."
        PinError.MISMATCH -> "الرقمان غير متطابقين."
        PinError.WRONG -> PIN_WRONG
        PinError.WRONG_CURRENT -> "الرقم السري الحالي غير صحيح."
        PinError.NOT_SET -> "لم يُضبط رقم سري بعد."
    }

    // ---- home --------------------------------------------------------------------------
    const val HOME_TITLE = "أوامر القص المفتوحة"
    const val NEW_ORDER = "أمر جديد"
    const val HOME_EMPTY_TITLE = "لا توجد أوامر مفتوحة"
    const val HOME_EMPTY_BODY = "اضغط «أمر جديد» لتسجيل أول أمر قص."
    const val SEARCH_HINT = "ابحث بالموديل أو اللون أو المقاس"
    const val HISTORY = "السجل"
    const val REPORTS = "التقارير"
    const val SETTINGS = "الإعدادات"
    const val COMPLETE_BADGE = "اكتمل"
    const val COLORS_SUMMARY = "الألوان"
    const val SIZES_SUMMARY = "المقاسات"
    const val SINGLE_TOTAL = "كمية إجمالية"

    // ---- order form --------------------------------------------------------------------
    const val ORDER_NEW_TITLE = "أمر قص جديد"
    const val ORDER_EDIT_TITLE = "تعديل الأمر"
    const val MODEL_NAME = "اسم الموديل أو كوده"
    const val MODEL_CODE = "كود إضافي (اختياري)"
    const val MODEL_IMAGE = "صورة الموديل"
    const val PICK_IMAGE = "اختر صورة"
    const val CHANGE_IMAGE = "تغيير الصورة"
    const val REMOVE_IMAGE = "إزالة الصورة"
    const val IMAGE_MISSING = "الصورة غير موجودة"
    const val BREAKDOWN_QUESTION = "طريقة إدخال الكميات"
    const val BREAKDOWN_DETAILED = "تفصيل بالألوان والمقاسات"
    const val BREAKDOWN_TOTAL = "كمية إجمالية فقط"
    const val BREAKDOWN_DETAILED_HINT =
        "كل لون ومقاس له كمية مستقلة، فيظهر المتبقي لكل تركيبة على حدة."
    const val BREAKDOWN_TOTAL_HINT = "رقم واحد للأمر كله، بدون تفصيل."
    const val ADD_LINE = "إضافة سطر"
    const val REMOVE_LINE = "حذف السطر"
    const val TOTAL_REQUIRED = "إجمالي المطلوب"
    const val ADD_QUANTITY = "إضافة كمية"
    const val ADD_QUANTITY_TITLE = "إضافة كمية إلى الأمر"
    const val ADD_QUANTITY_HINT =
        "تُضاف الكمية إلى المطلوب الحالي. عمليات القص المسجلة لا تتغير."
    const val ORDER_SAVED = "تم حفظ الأمر."
    const val ORDER_UPDATED = "تم تحديث الأمر."
    const val QUANTITY_ADDED = "تمت إضافة الكمية."

    // ---- order detail ------------------------------------------------------------------
    const val ORDER_DETAIL_TITLE = "تفاصيل الأمر"
    const val RECORD_CUT = "تسجيل قص"
    const val CREATED_AT = "تاريخ الإنشاء"
    const val CLOSED_AT = "تاريخ الإغلاق"
    const val STATUS = "الحالة"
    const val STATUS_OPEN = "مفتوح"
    const val STATUS_CLOSED = "مغلق"
    const val QUANTITIES = "الكميات"
    const val OPERATIONS_LOG = "سجل العمليات"
    const val NO_OPERATIONS = "لم تُسجَّل أي عملية قص بعد."
    const val CLOSE_ORDER = "إغلاق الأمر"
    const val REOPEN_ORDER = "إعادة فتح الأمر"
    const val ORDER_CLOSED_NOTICE = "هذا الأمر مغلق. أعد فتحه قبل تسجيل أي عملية."

    const val CLOSE_ORDER_TITLE = "إغلاق أمر القص"
    const val CLOSE_ORDER_INTRO = "راجع الملخص قبل الإغلاق:"
    const val CLOSE_ORDER_HAS_REMAINING =
        "ما زال هناك متبقٍ لم يُقص. الإغلاق لن يحذف البيانات، لكن الأمر سيخرج من قائمة المفتوح."
    const val CLOSE_ORDER_CONFIRM = "إغلاق الأمر"
    const val ORDER_CLOSED_DONE = "تم إغلاق الأمر."

    const val REOPEN_TITLE = "إعادة فتح الأمر"
    const val REOPEN_BODY =
        "لمنع الفتح بالخطأ، اكتب الرقم السري للتطبيق. ستبقى كل العمليات السابقة كما هي."
    const val REOPEN_CONFIRM = "إعادة الفتح"
    const val ORDER_REOPENED = "تمت إعادة فتح الأمر."

    // ---- operation form ----------------------------------------------------------------
    const val OPERATION_NEW_TITLE = "تسجيل عملية قص"
    const val OPERATION_EDIT_TITLE = "تعديل عملية القص"
    const val COMBINATION = "اللون والمقاس"
    const val METHOD = "طريقة التسجيل"
    const val METHOD_DIRECT = "إدخال العدد"
    const val METHOD_LAYERS = "حساب بالطبقات"
    const val PIECES_CUT = "عدد القطع المقصوصة"
    const val LAYERS = "عدد الطبقات"
    const val PIECES_PER_LAYER = "عدد القطع في الطبقة"
    const val OPERATION_TIME = "وقت العملية"
    const val OPERATION_SAVED = "تم تسجيل عملية القص."
    const val OPERATION_UPDATED = "تم تعديل عملية القص."
    const val EDITED_MARK = "مُعدَّلة"

    fun layersResult(layers: Int, perLayer: Int, total: Int): String =
        "$layers طبقة × $perLayer في الطبقة = $total قطعة"

    fun combinationLabel(color: String, size: String): String = when {
        color.isBlank() && size.isBlank() -> SINGLE_TOTAL
        color.isBlank() -> "مقاس $size"
        size.isBlank() -> color
        else -> "$color · مقاس $size"
    }

    fun remainingForCombination(remaining: Int): String = "المتبقي: $remaining"

    // ---- overflow ----------------------------------------------------------------------
    const val OVERFLOW_TITLE = "تجاوز الكمية المطلوبة"
    const val OVERFLOW_CONFIRM = "تسجيل مع الزيادة"

    fun overflowBody(required: Int, cutAfter: Int, excess: Int): String = buildString {
        append("هذه العملية تتجاوز المطلوب لهذه التركيبة.\n\n")
        append("المطلوب: $required\n")
        append("المقصوص بعد الحفظ: $cutAfter\n")
        append("الزيادة: $excess")
    }

    const val COMPLETED_TOAST = "اكتملت كميات هذا الأمر. يمكنك إغلاقه من شاشة التفاصيل."

    // ---- history -----------------------------------------------------------------------
    const val HISTORY_TITLE = "السجل والبحث"
    const val TAB_OPEN = "أوامر مفتوحة"
    const val TAB_CLOSED = "أوامر مغلقة"
    const val TAB_OPERATIONS = "عمليات القص"
    const val HISTORY_EMPTY = "لا توجد نتائج."
    const val FILTER_DATE = "تصفية بالتاريخ"
    const val DATE_ANY = "كل التواريخ"
    const val DATE_FROM = "من"
    const val DATE_TO = "إلى"
    const val CLEAR_DATES = "إلغاء التصفية"

    // ---- reports -----------------------------------------------------------------------
    const val REPORTS_TITLE = "التقارير"
    const val PERIOD_DAY = "يومي"
    const val PERIOD_WEEK = "أسبوعي"
    const val PERIOD_MONTH = "شهري"
    const val PERIOD_ALL = "الإجمالي"
    const val TOTAL_PIECES = "إجمالي القطع المقصوصة"
    const val OPERATIONS_COUNT = "عدد العمليات"
    const val BY_MODEL = "حسب الموديل"
    const val BY_DAY = "حسب اليوم"
    const val REPORT_EMPTY = "لا توجد عمليات في هذه الفترة."
    const val PREVIOUS_PERIOD = "الفترة السابقة"
    const val NEXT_PERIOD = "الفترة التالية"

    fun periodLabel(period: ReportPeriod): String = when (period) {
        ReportPeriod.DAY -> PERIOD_DAY
        ReportPeriod.WEEK -> PERIOD_WEEK
        ReportPeriod.MONTH -> PERIOD_MONTH
        ReportPeriod.ALL -> PERIOD_ALL
    }

    // ---- settings ----------------------------------------------------------------------
    const val SETTINGS_TITLE = "الإعدادات"
    const val SECTION_APPEARANCE = "المظهر"
    const val SECTION_SOUND = "الأصوات"
    const val SECTION_SECURITY = "الحماية"
    const val SECTION_BACKUP = "النسخ الاحتياطي"
    const val SECTION_ABOUT = "عن التطبيق"
    const val THEME = "الوضع"
    const val ACCENT = "لون التطبيق"
    const val SOUNDS_ENABLED = "تشغيل الأصوات"
    const val SOUNDS_ENABLED_HINT = "أصوات النجاح والخطأ والتنبيهات."
    const val BUTTON_SOUNDS = "صوت الأزرار"
    const val BUTTON_SOUNDS_HINT = "نقرة قصيرة عند الضغط. يمكن إيقافها وحدها."
    const val SILENT_MODE_NOTE = "لا يصدر التطبيق أي صوت والهاتف على الصامت أو الاهتزاز."
    const val EXPORT_BACKUP = "إنشاء نسخة احتياطية"
    const val EXPORT_BACKUP_HINT = "ملف واحد يحتوي على الأوامر والعمليات والصور."
    const val IMPORT_BACKUP = "استعادة نسخة احتياطية"
    const val IMPORT_BACKUP_HINT = "يستبدل كل البيانات الحالية بما في الملف."
    const val ABOUT_BODY =
        "دفتر القص الإلكتروني — إصدار ١٫٠\nيعمل دون إنترنت. كل البيانات محفوظة على هذا الهاتف."

    fun themeLabel(mode: ThemeMode): String = when (mode) {
        ThemeMode.SYSTEM -> "حسب النظام"
        ThemeMode.LIGHT -> "فاتح"
        ThemeMode.DARK -> "داكن"
    }

    fun accentLabel(accent: AccentColor): String = when (accent) {
        AccentColor.GREEN -> "أخضر"
        AccentColor.BLUE -> "أزرق"
        AccentColor.INDIGO -> "بنفسجي"
        AccentColor.TEAL -> "فيروزي"
        AccentColor.AMBER -> "عنبري"
        AccentColor.CRIMSON -> "أحمر"
    }

    // ---- backup ------------------------------------------------------------------------
    const val RESTORE_WARNING_TITLE = "استعادة نسخة احتياطية"
    const val RESTORE_WARNING_BODY =
        "سيتم استبدال كل البيانات الحالية — الأوامر والعمليات والصور — بما في ملف النسخة " +
            "الاحتياطية. لا يمكن التراجع عن هذه الخطوة.\n\n" +
            "إن كانت بياناتك الحالية مهمة، أنشئ منها نسخة احتياطية أولًا."
    const val RESTORE_CONFIRM = "استبدال البيانات"
    const val BACKUP_IN_PROGRESS = "جارٍ العمل…"

    fun backupExported(models: Int, orders: Int, operations: Int, images: Int): String =
        "تم إنشاء النسخة الاحتياطية: $orders أمرًا، $operations عملية، $models موديلًا، " +
            "$images صورة."

    fun backupRestored(models: Int, orders: Int, operations: Int, images: Int): String =
        "تمت الاستعادة: $orders أمرًا، $operations عملية، $models موديلًا، $images صورة."

    fun backupError(error: BackupError): String = when (error) {
        BackupError.NOT_A_BACKUP -> "هذا الملف ليس نسخة احتياطية من دفتر القص."
        BackupError.CORRUPT -> "ملف النسخة الاحتياطية تالف. لم تتغير بياناتك الحالية."
        BackupError.UNSUPPORTED_VERSION ->
            "النسخة الاحتياطية من إصدار أحدث من التطبيق. حدّث التطبيق ثم أعد المحاولة."
        BackupError.INCONSISTENT ->
            "بيانات الملف غير متسقة. لم تتغير بياناتك الحالية."
        BackupError.OUT_OF_SPACE -> "لا توجد مساحة كافية على الهاتف."
        BackupError.IO -> "تعذّر قراءة الملف أو كتابته."
    }

    // ---- errors ------------------------------------------------------------------------
    fun appError(error: AppError): String = when (error) {
        AppError.OrderClosed -> "الأمر مغلق. أعد فتحه أولًا."
        AppError.ItemHasOperations ->
            "لا يمكن حذف سطر مسجَّل عليه عمليات قص. عدّل كميته بدل حذفه."
        AppError.NoQuantities -> "أدخل كمية واحدة على الأقل."
        AppError.DuplicateCombination -> "تكرر نفس اللون والمقاس أكثر من مرة."
        AppError.InvalidQuantity -> "الكمية غير صالحة."
        AppError.ModelNameRequired -> "اكتب اسم الموديل أو كوده."
        AppError.NotFound -> "العنصر غير موجود."
        is AppError.Storage -> "تعذّر حفظ البيانات. تأكد من وجود مساحة على الهاتف."
    }

    fun countError(error: PieceCountError): String = when (error) {
        PieceCountError.EMPTY -> "اكتب العدد."
        PieceCountError.NOT_A_NUMBER -> "اكتب رقمًا صحيحًا."
        PieceCountError.NOT_POSITIVE -> "العدد يجب أن يكون أكبر من صفر."
        PieceCountError.TOO_LARGE -> "العدد كبير أكثر من اللازم."
    }

    // ---- formatting --------------------------------------------------------------------

    private val locale: Locale = Locale.forLanguageTag("ar-EG")
    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val shortDateFormat = DateTimeFormatter.ofPattern("d/M/yyyy", locale)
    private val dateTimeFormat = DateTimeFormatter.ofPattern("d MMMM yyyy · h:mm a", locale)

    fun formatDate(millis: Long, zone: ZoneId): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).format(dateFormat)

    fun formatDateTime(millis: Long, zone: ZoneId): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).format(dateTimeFormat)

    fun formatDate(date: LocalDate): String = date.format(dateFormat)

    fun formatShortDate(date: LocalDate): String = date.format(shortDateFormat)

    fun rangeLabel(from: LocalDate, to: LocalDate): String =
        if (from == to) formatDate(from) else "${formatShortDate(from)} — ${formatShortDate(to)}"
}
