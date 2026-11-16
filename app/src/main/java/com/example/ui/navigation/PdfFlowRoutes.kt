package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.localization.AppStrings
import com.example.ui.theme.FlowAmber
import com.example.ui.theme.FlowCrimson
import com.example.ui.theme.FlowEmerald
import com.example.ui.theme.FlowIndigo
import com.example.ui.theme.FlowRoyalBlue
import com.example.ui.theme.FlowSkyBlue
import com.example.ui.theme.FlowTeal
import com.example.ui.theme.FlowViolet

object Routes {
    const val HOME = "home"
    const val TOOLS = "tools"
    const val FILES = "files"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"

    const val READER = "reader/{docId}"
    fun reader(docId: String) = "reader/$docId"

    const val EDITOR = "editor?docId={docId}"
    fun editor(docId: String? = null) = if (docId != null) "editor?docId=$docId" else "editor"

    const val ORGANIZER = "organizer?docId={docId}"
    fun organizer(docId: String? = null) = if (docId != null) "organizer?docId=$docId" else "organizer"

    const val MERGE_SPLIT = "merge_split/{mode}"
    fun mergeSplit(mode: String = "merge") = "merge_split/$mode"

    const val CONVERTER = "converter/{mode}"
    fun converter(mode: String = "create") = "converter/$mode"

    const val COMPRESSOR = "compressor?docId={docId}"
    fun compressor(docId: String? = null) = if (docId != null) "compressor?docId=$docId" else "compressor"

    const val SECURITY = "security/{mode}?docId={docId}"
    fun security(mode: String = "protect", docId: String? = null) =
        if (docId != null) "security/$mode?docId=$docId" else "security/$mode"

    const val SCANNER = "scanner"
    const val OCR = "ocr"
    const val AI_STUDIO = "ai_studio?docId={docId}"
    fun aiStudio(docId: String? = null) = if (docId != null) "ai_studio?docId=$docId" else "ai_studio"

    const val UTILITIES = "utilities/{tab}?docId={docId}"
    fun utilities(tab: String = "compare", docId: String? = null) =
        if (docId != null) "utilities/$tab?docId=$docId" else "utilities/$tab"

    const val BACKUP = "backup"
    const val GUEST_MIGRATION = "guest_migration"
    const val PREMIUM = "premium"
    const val INFO_POLICY = "info_policy/{topic}"
    fun infoPolicy(topic: String) = "info_policy/$topic"
}

data class ToolDescriptor(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val category: String,
    val route: String
)

fun buildToolCatalog(strings: AppStrings): List<ToolDescriptor> = listOf(
    ToolDescriptor("create", strings.toolCreatePdf, "Rich text & formatted PDF", Icons.Default.NoteAdd, FlowIndigo, "Create", Routes.converter("create")),
    ToolDescriptor("scan", strings.toolScanDoc, "Camera & page filters", Icons.Default.DocumentScanner, FlowCrimson, "Scan & AI", Routes.SCANNER),
    ToolDescriptor("edit", strings.toolEditPdf, "Text, draw, sign & markup", Icons.Default.Edit, FlowRoyalBlue, "Edit & Organize", Routes.editor()),
    ToolDescriptor("merge", strings.toolMergePdf, "Combine multiple PDFs", Icons.Default.MergeType, FlowTeal, "Edit & Organize", Routes.mergeSplit("merge")),
    ToolDescriptor("split", strings.toolSplitPdf, "Split by pages or range", Icons.Default.CallSplit, FlowViolet, "Edit & Organize", Routes.mergeSplit("split")),
    ToolDescriptor("compress", strings.toolCompressPdf, "Reduce PDF file size", Icons.Default.Compress, FlowEmerald, "Optimize", Routes.compressor()),
    ToolDescriptor("convert", strings.toolConvertPdf, "DOCX, CSV, HTML, TXT, Images", Icons.Default.SwapHoriz, FlowAmber, "Convert", Routes.converter("pdf_to_docx")),
    ToolDescriptor("img2pdf", strings.toolImageToPdf, "Combine photos into PDF", Icons.Default.PhotoLibrary, FlowSkyBlue, "Convert", Routes.converter("img_to_pdf")),
    ToolDescriptor("pdf2img", strings.toolPdfToImage, "Export pages as JPG/PNG", Icons.Default.Image, FlowRoyalBlue, "Convert", Routes.converter("pdf_to_img")),
    ToolDescriptor("organize", "Organize Pages", "Reorder, rotate, crop, extract", Icons.Default.GridView, FlowIndigo, "Edit & Organize", Routes.organizer()),
    ToolDescriptor("ocr", strings.toolOcr, "Extract text from scans", Icons.Default.TextSnippet, FlowTeal, "Scan & AI", Routes.OCR),
    ToolDescriptor("ai", strings.toolAiAssistant, "Summarize, Q&A & translate", Icons.Default.AutoAwesome, FlowViolet, "Scan & AI", Routes.aiStudio()),
    ToolDescriptor("protect", strings.toolProtectPdf, "Add password & restrictions", Icons.Default.Lock, FlowCrimson, "Security", Routes.security("protect")),
    ToolDescriptor("unlock", strings.toolUnlockPdf, "Remove PDF password", Icons.Default.LockOpen, FlowEmerald, "Security", Routes.security("unlock")),
    ToolDescriptor("compare", strings.toolComparePdf, "Diff two PDFs side by side", Icons.Default.Compare, FlowRoyalBlue, "Utilities", Routes.utilities("compare")),
    ToolDescriptor("metadata", strings.toolMetadata, "Inspect & edit PDF properties", Icons.Default.Info, FlowIndigo, "Utilities", Routes.utilities("metadata")),
    ToolDescriptor("repair", strings.toolRepairPdf, "Rebuild PDF structure", Icons.Default.Build, FlowAmber, "Utilities", Routes.utilities("repair")),
    ToolDescriptor("extract_img", strings.toolExtractImages, "Pull embedded images", Icons.Default.CreateNewFolder, FlowSkyBlue, "Utilities", Routes.utilities("images")),
    ToolDescriptor("batch", strings.toolBatchProcess, "Process multiple PDFs", Icons.Default.Layers, FlowViolet, "Utilities", Routes.utilities("batch")),
    ToolDescriptor("downloads", strings.toolDownloadManager, "Saved device PDFs", Icons.Default.Download, FlowTeal, "Utilities", Routes.DOWNLOADS)
)
