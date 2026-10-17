package com.zenithai.countai.util

import android.content.Context
import com.zenithai.countai.data.SessionEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a real Excel-compatible `.xls` file using SpreadsheetML (the
 * XML format Excel 2003+ opens natively) — no POI dependency needed.
 *
 * Honest note: this is SpreadsheetML, not the modern .xlsx format. Excel,
 * Google Sheets and LibreOffice all open it; very old software may not.
 */
object ExcelExporter {

    fun export(context: Context, sessions: List<SessionEntity>): File {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""").append('\n')
        sb.append("<Worksheet ss:Name=\"CountAI\"><Table>\n")
        sb.append(row("Session ID", "Date", "Mode", "Object", "Count"))
        for (s in sessions) {
            val date = dateFmt.format(Date(s.createdAt))
            val counts = s.counts()
            if (counts.isEmpty()) {
                sb.append(row(s.id.toString(), date, s.mode, "(none)", "0"))
            } else {
                for ((label, count) in counts.toList().sortedByDescending { it.second }) {
                    sb.append(row(s.id.toString(), date, s.mode, label, count.toString()))
                }
            }
        }
        sb.append("</Table></Worksheet>\n</Workbook>\n")
        val file = File(context.cacheDir, "countai_history.xls")
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    /** Single-session export used by CountCam's Export button. */
    fun exportCounts(context: Context, title: String, counts: Map<String, Int>): File {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""").append('\n')
        sb.append("<Worksheet ss:Name=\"CountAI\"><Table>\n")
        sb.append(row("Object", "Count"))
        for ((label, count) in counts.toList().sortedByDescending { it.second }) {
            sb.append(row(label, count.toString()))
        }
        sb.append(row("TOTAL", counts.values.sum().toString()))
        sb.append("</Table></Worksheet>\n</Workbook>\n")
        val safe = title.replace(Regex("[^A-Za-z0-9]+"), "_").take(20)
        val file = File(context.cacheDir, "countai_${safe}_${System.currentTimeMillis()}.xls")
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    private fun row(vararg cells: String): String {
        val escaped = cells.joinToString("") { "<Cell><Data ss:Type=\"String\">${xmlEscape(it)}</Data></Cell>" }
        return "<Row>$escaped</Row>\n"
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
