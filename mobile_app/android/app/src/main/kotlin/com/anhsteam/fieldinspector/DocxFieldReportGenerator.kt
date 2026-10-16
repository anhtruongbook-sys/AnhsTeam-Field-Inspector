package com.anhsteam.fieldinspector

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Bộ Kết xuất Biên bản Kiểm tra, Khảo sát và Nghiệm thu Hiện trường (.docx) OpenXML
 * Chuẩn Nghị định số 30/2020/NĐ-CP, Nghị định số 207/2026/NĐ-CP, Nghị định số 217/2026/NĐ-CP, Nghị định số 347/2026/NĐ-CP.
 * - Phông chữ Times New Roman 100%, chữ đen tuyền (#000000) 100%.
 * - Bảng biểu nền trắng tự nhiên, viền đơn đen mảnh 0.5pt (w:sz="4"), lặp tiêu đề (w:tblHeader), chống cắt dòng (w:cantSplit).
 * - Tuyệt đối không dùng xuống dòng mềm (<w:br/>) trong đoạn căn đều (<w:jc w:val="both"/>).
 */
object DocxFieldReportGenerator {

    private fun xmlEscape(input: String): String {
        val clean = input.replace("\r", " ").replace("\n", " ").trim()
        return clean
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildRunXml(
        text: String,
        halfPtSize: Int = 26,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false
    ): String {
        val sb = StringBuilder()
        sb.append("<w:r><w:rPr>")
        sb.append("<w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\" w:cs=\"Times New Roman\" w:eastAsia=\"Times New Roman\"/>")
        if (bold) sb.append("<w:b/><w:bCs/>")
        if (italic) sb.append("<w:i/><w:iCs/>")
        if (underline) sb.append("<w:u w:val=\"single\"/>")
        sb.append("<w:color w:val=\"000000\"/>")
        sb.append("<w:sz w:val=\"$halfPtSize\"/><w:szCs w:val=\"$halfPtSize\"/>")
        sb.append("</w:rPr>")
        sb.append("<w:t xml:space=\"preserve\">${xmlEscape(text)}</w:t></w:r>")
        return sb.toString()
    }

    private fun buildParagraphXml(
        text: String,
        halfPtSize: Int = 26,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        jc: String = "left",
        spaceBeforeDxa: Int = 60,
        spaceAfterDxa: Int = 60,
        firstLineIndentDxa: Int = 0
    ): String {
        val sb = StringBuilder()
        sb.append("<w:p><w:pPr>")
        sb.append("<w:spacing w:before=\"$spaceBeforeDxa\" w:after=\"$spaceAfterDxa\" w:line=\"276\" w:lineRule=\"auto\"/>")
        if (firstLineIndentDxa > 0) {
            sb.append("<w:ind w:firstLine=\"$firstLineIndentDxa\"/>")
        }
        sb.append("<w:jc w:val=\"$jc\"/>")
        sb.append("</w:pPr>")
        if (text.isNotEmpty()) {
            sb.append(buildRunXml(text, halfPtSize, bold, italic, underline))
        }
        sb.append("</w:p>")
        return sb.toString()
    }

    fun generateFieldReportDocx(
        context: Context,
        outputDir: File,
        jsonString: String
    ): Map<String, Any> {
        val root = JSONObject(jsonString)
        val docMode = root.optString("docLanguageMode", "VI").ifBlank { "VI" }
        val orgName = root.optString("organizationName", "ANH'S TEAM - KHẢO SÁT & GIÁM SÁT HIỆN TRƯỜNG")
        val docNumber = root.optString("documentNumber", "Số: 01/2026/BBKT-HT")
        val locationDate = root.optString("locationDate", "Hà Nội, ngày 16 tháng 10 năm 2026")
        val reportTitle = root.optString(
            "reportTitle",
            "BIÊN BẢN KIỂM TRA, KHẢO SÁT VÀ NGHIỆM THU CÔNG VIỆC XÂY DỰNG TẠI HIỆN TRƯỜNG"
        )
        val projectName = root.optString("projectName", "Tòa nhà Trung tâm Nghiên cứu và Phát triển Công nghệ (DA01-IBST)")
        val zoneAxis = root.optString("zoneAxis", "Tầng hầm B1 - Trục (B-C)/(3-4) - Cao độ -4.50m")
        val gpsCoords = root.optString("gpsCoords", "21.004512°N, 105.798234°E (±1.5m) | Hướng nhìn: 135° Đông Nam")
        val startTime = root.optString("startTime", "08 giờ 30 phút ngày 16/10/2026")
        val endTime = root.optString("endTime", "11 giờ 30 phút cùng ngày")
        val supervisorName = root.optString("supervisorName", "KS. Nguyễn Văn A - Giám sát trưởng thi công xây dựng")
        val contractorName = root.optString("contractorName", "KS. Trần Văn B - Chỉ huy trưởng / Phụ trách kỹ thuật thi công trực tiếp")
        val checklistArray = root.optJSONArray("checklistItems") ?: JSONArray()
        val photosArray = root.optJSONArray("stampedPhotos") ?: JSONArray()
        val conclusionsArray = root.optJSONArray("conclusions") ?: JSONArray()

        val bodyXml = StringBuilder()

        // 1. Bảng Quốc hiệu & Tiêu ngữ (2 cột ẩn viền)
        bodyXml.append("<w:tbl><w:tblPr>")
        bodyXml.append("<w:tblW w:w=\"9355\" w:type=\"dxa\"/>")
        bodyXml.append("<w:jc w:val=\"center\"/>")
        bodyXml.append("<w:tblBorders>")
        for (b in listOf("top", "left", "bottom", "right", "insideH", "insideV")) {
            bodyXml.append("<w:$b w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>")
        }
        bodyXml.append("</w:tblBorders></w:tblPr>")
        bodyXml.append("<w:tblGrid><w:gridCol w:w=\"4000\"/><w:gridCol w:w=\"5355\"/></w:tblGrid>")
        bodyXml.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")

        bodyXml.append("<w:tc><w:tcPr><w:tcW w:w=\"4000\" w:type=\"dxa\"/></w:tcPr>")
        bodyXml.append(buildParagraphXml("BAN QUẢN LÝ DỰ ÁN ĐẦU TƯ XÂY DỰNG", halfPtSize = 23, bold = false, jc = "center", spaceBeforeDxa = 0, spaceAfterDxa = 20))
        bodyXml.append(buildParagraphXml(orgName.uppercase(Locale.getDefault()), halfPtSize = 23, bold = true, underline = true, jc = "center", spaceBeforeDxa = 0, spaceAfterDxa = 40))
        bodyXml.append(buildParagraphXml(docNumber, halfPtSize = 23, bold = false, jc = "center", spaceBeforeDxa = 20, spaceAfterDxa = 0))
        bodyXml.append("</w:tc>")

        bodyXml.append("<w:tc><w:tcPr><w:tcW w:w=\"5355\" w:type=\"dxa\"/></w:tcPr>")
        bodyXml.append(buildParagraphXml("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", halfPtSize = 24, bold = true, jc = "center", spaceBeforeDxa = 0, spaceAfterDxa = 20))
        bodyXml.append(buildParagraphXml("Độc lập - Tự do - Hạnh phúc", halfPtSize = 26, bold = true, underline = true, jc = "center", spaceBeforeDxa = 0, spaceAfterDxa = 60))
        bodyXml.append(buildParagraphXml(locationDate, halfPtSize = 24, italic = true, jc = "center", spaceBeforeDxa = 40, spaceAfterDxa = 0))
        bodyXml.append("</w:tc></w:tr></w:tbl>")

        // 2. Tiêu đề Biên bản
        bodyXml.append(buildParagraphXml(reportTitle.uppercase(Locale.getDefault()), halfPtSize = 27, bold = true, jc = "center", spaceBeforeDxa = 220, spaceAfterDxa = 40))
        bodyXml.append(buildParagraphXml("Dự án / Công trình: $projectName", halfPtSize = 26, bold = true, jc = "center", spaceBeforeDxa = 0, spaceAfterDxa = 160))

        // 3. Mục I: Căn cứ pháp lý
        bodyXml.append(buildParagraphXml("I. CĂN CỨ PHÁP LÝ VÀ TIÊU CHUẨN KỸ THUẬT ÁP DỤNG", halfPtSize = 26, bold = true, jc = "left", spaceBeforeDxa = 120, spaceAfterDxa = 60))
        val legalList = listOf(
            "- Căn cứ Luật Xây dựng số 135/2025/QH15;",
            "- Căn cứ Luật Phòng cháy, chữa cháy và cứu nạn, cứu hộ số 55/2024/QH15;",
            "- Căn cứ Nghị định số 207/2026/NĐ-CP quy định chi tiết về quản lý chất lượng, thi công xây dựng và bảo trì công trình xây dựng (Điều 22 nghiệm thu công việc, Điều 23 nghiệm thu giai đoạn, Điều 24 nghiệm thu hoàn thành);",
            "- Căn cứ Nghị định số 217/2026/NĐ-CP quy định chi tiết một số nội dung về quản lý dự án đầu tư xây dựng;",
            "- Căn cứ Nghị định số 105/2025/NĐ-CP và Nghị định số 347/2026/NĐ-CP quy định về kiểm tra hiện trường và nghiệm thu về phòng cháy, chữa cháy;",
            "- Căn cứ Hồ sơ thiết kế bản vẽ thi công, Nhiệm vụ khảo sát và Chỉ dẫn kỹ thuật đã được phê duyệt."
        )
        for (l in legalList) {
            bodyXml.append(buildParagraphXml(l, halfPtSize = 26, bold = false, jc = "left", spaceBeforeDxa = 30, spaceAfterDxa = 30))
        }

        // 4. Mục II: Thời gian, địa điểm, định vị và thành phần
        bodyXml.append(buildParagraphXml("II. THỜI GIAN, ĐỊA ĐIỂM ĐỊNH VỊ VÀ THÀNH PHẦN TRỰC TIẾP KIỂM TRA TẠI HIỆN TRƯỜNG", halfPtSize = 26, bold = true, jc = "left", spaceBeforeDxa = 140, spaceAfterDxa = 60))
        bodyXml.append(buildParagraphXml("- Thời gian kiểm tra: Bắt đầu lúc $startTime; kết thúc lúc $endTime.", halfPtSize = 26, jc = "left"))
        bodyXml.append(buildParagraphXml("- Vị trí / Khu vực / Trục định vị tại hiện trường: $zoneAxis.", halfPtSize = 26, jc = "left"))
        bodyXml.append(buildParagraphXml("- Tọa độ định vị GNSS/GPS & Hướng nhìn la bàn: $gpsCoords.", halfPtSize = 26, jc = "left"))
        bodyXml.append(buildParagraphXml("- Đại diện Chủ đầu tư / Bộ phận Giám sát thi công xây dựng: $supervisorName.", halfPtSize = 26, jc = "left"))
        bodyXml.append(buildParagraphXml("- Đại diện Nhà thầu khảo sát / Thi công xây dựng: $contractorName.", halfPtSize = 26, jc = "left"))

        // 5. Mục III: Bảng kết quả kiểm tra, đo đạc thực tế tại hiện trường
        bodyXml.append(buildParagraphXml("III. KẾT QUẢ KIỂM TRA, KHẢO SÁT VÀ ĐO ĐẠC THỰC TẾ TẠI HIỆN TRƯỜNG", halfPtSize = 26, bold = true, jc = "left", spaceBeforeDxa = 140, spaceAfterDxa = 60))

        bodyXml.append("<w:tbl><w:tblPr>")
        bodyXml.append("<w:tblW w:w=\"9355\" w:type=\"dxa\"/>")
        bodyXml.append("<w:jc w:val=\"center\"/>")
        bodyXml.append("<w:tblBorders>")
        for (b in listOf("top", "left", "bottom", "right", "insideH", "insideV")) {
            bodyXml.append("<w:$b w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>")
        }
        bodyXml.append("</w:tblBorders></w:tblPr>")

        val colWidths = listOf(600, 1450, 2600, 1900, 1905, 900)
        bodyXml.append("<w:tblGrid>")
        for (w in colWidths) bodyXml.append("<w:gridCol w:w=\"$w\"/>")
        bodyXml.append("</w:tblGrid>")

        val headers = listOf("TT", "Chuyên ngành", "Nội dung kiểm tra tại hiện trường", "Tiêu chuẩn / Thiết kế", "Số liệu thực đo", "Đánh giá")
        bodyXml.append("<w:tr><w:trPr><w:cantSplit/><w:tblHeader/></w:trPr>")
        for (idx in headers.indices) {
            bodyXml.append("<w:tc><w:tcPr><w:tcW w:w=\"${colWidths[idx]}\" w:type=\"dxa\"/><w:vAlign w:val=\"center\"/></w:tcPr>")
            bodyXml.append(buildParagraphXml(headers[idx], halfPtSize = 23, bold = true, jc = "center", spaceBeforeDxa = 60, spaceAfterDxa = 60))
            bodyXml.append("</w:tc>")
        }
        bodyXml.append("</w:tr>")

        var passedCount = 0
        val totalCount = checklistArray.length()
        for (i in 0 until totalCount) {
            val item = checklistArray.optJSONObject(i) ?: continue
            val stt = (i + 1).toString()
            val disc = item.optString("discipline", "Kết cấu")
            val content = item.optString("title", "")
            val std = item.optString("standard", "")
            val measured = item.optString("measured", "")
            val passed = item.optBoolean("passed", true)
            if (passed) passedCount++
            val evalStr = if (passed) "Đạt" else "Không đạt"

            val rowVals = listOf(stt, disc, content, std, measured, evalStr)
            val rowAligns = listOf("center", "center", "both", "left", "both", "center")
            val rowBolds = listOf(true, false, false, false, false, true)

            bodyXml.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")
            for (cIdx in rowVals.indices) {
                bodyXml.append("<w:tc><w:tcPr><w:tcW w:w=\"${colWidths[cIdx]}\" w:type=\"dxa\"/><w:vAlign w:val=\"center\"/></w:tcPr>")
                bodyXml.append(buildParagraphXml(rowVals[cIdx], halfPtSize = 23, bold = rowBolds[cIdx], jc = rowAligns[cIdx], spaceBeforeDxa = 50, spaceAfterDxa = 50))
                bodyXml.append("</w:tc>")
            }
            bodyXml.append("</w:tr>")
        }
        bodyXml.append("</w:tbl>")

        val ratePercent = if (totalCount > 0) String.format(Locale.US, "%.1f", passedCount * 100.0 / totalCount) else "100.0"
        bodyXml.append(
            buildParagraphXml(
                "- Tổng hợp kết quả kiểm tra danh mục hiện trường: Đạt $passedCount/$totalCount chỉ tiêu kỹ thuật (Tỷ lệ tuân thủ: $ratePercent%).",
                halfPtSize = 26,
                bold = true,
                jc = "left",
                spaceBeforeDxa = 80,
                spaceAfterDxa = 60
            )
        )

        // 6. Mục IV: Danh mục ảnh chụp hiện trường đã đóng dấu thông tin & SHA-256
        bodyXml.append(buildParagraphXml("IV. DANH MỤC ẢNH CHỤP CHỨNG CỨ HIỆN TRƯỜNG ĐÃ ĐÓNG DẤU ĐỊNH VỊ & MÃ BĂM SHA-256", halfPtSize = 26, bold = true, jc = "left", spaceBeforeDxa = 120, spaceAfterDxa = 60))
        if (photosArray.length() == 0) {
            bodyXml.append(buildParagraphXml("- Các tệp ảnh chụp chứng cứ hiện trường được lưu trữ đồng bộ trong cơ sở dữ liệu CDE của dự án.", halfPtSize = 26, jc = "left"))
        } else {
            for (i in 0 until photosArray.length()) {
                val pObj = photosArray.optJSONObject(i) ?: continue
                val pName = pObj.optString("name", "HT_STAMPED.jpg")
                val pSha = pObj.optString("sha256", "").take(24).uppercase(Locale.US)
                val pTime = pObj.optString("modified", "")
                bodyXml.append(
                    buildParagraphXml(
                        "- Ảnh chứng cứ số ${i + 1}: $pName (Thời gian: $pTime | Mã băm toàn vẹn SHA-256: $pSha...).",
                        halfPtSize = 25,
                        jc = "left"
                    )
                )
            }
        }

        // 7. Mục V: Kết luận nghiệm thu tại hiện trường
        bodyXml.append(buildParagraphXml("V. KẾT LUẬN NGHIỆM THU TẠI HIỆN TRƯỜNG (ĐIỀU 22 NGHỊ ĐỊNH SỐ 207/2026/NĐ-CP)", halfPtSize = 26, bold = true, jc = "left", spaceBeforeDxa = 140, spaceAfterDxa = 60))
        if (conclusionsArray.length() > 0) {
            for (i in 0 until conclusionsArray.length()) {
                val c = conclusionsArray.optString(i, "").trim()
                if (c.isNotEmpty()) {
                    val cleanC = if (c.startsWith("-")) c.substring(1).trim() else c
                    bodyXml.append(buildParagraphXml("- $cleanC", halfPtSize = 26, jc = "both", firstLineIndentDxa = 425))
                }
            }
        } else {
            bodyXml.append(
                buildParagraphXml(
                    "- Các công việc khảo sát, kiểm tra và thi công xây dựng tại hiện trường đã được thực hiện phù hợp với hồ sơ thiết kế bản vẽ thi công được duyệt và quy chuẩn, tiêu chuẩn kỹ thuật hiện hành. Đồng ý nghiệm thu theo quy định tại Điều 22 Nghị định số 207/2026/NĐ-CP và cho phép chuyển bước thi công tiếp theo.",
                    halfPtSize = 26,
                    jc = "both",
                    firstLineIndentDxa = 425
                )
            )
        }

        // 8. Bảng chữ ký xác nhận 2 cột không viền
        bodyXml.append("<w:tbl><w:tblPr>")
        bodyXml.append("<w:tblW w:w=\"9355\" w:type=\"dxa\"/>")
        bodyXml.append("<w:jc w:val=\"center\"/>")
        bodyXml.append("<w:tblBorders>")
        for (b in listOf("top", "left", "bottom", "right", "insideH", "insideV")) {
            bodyXml.append("<w:$b w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>")
        }
        bodyXml.append("</w:tblBorders></w:tblPr>")
        bodyXml.append("<w:tblGrid><w:gridCol w:w=\"4677\"/><w:gridCol w:w=\"4678\"/></w:tblGrid>")
        bodyXml.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")

        bodyXml.append("<w:tc><w:tcPr><w:tcW w:w=\"4677\" w:type=\"dxa\"/></w:tcPr>")
        bodyXml.append(buildParagraphXml("NGƯỜI PHỤ TRÁCH KỸ THUẬT THI CÔNG TRỰC TIẾP", halfPtSize = 23, bold = true, jc = "center", spaceBeforeDxa = 160, spaceAfterDxa = 20))
        bodyXml.append(buildParagraphXml("(Ký và ghi rõ họ tên)", halfPtSize = 22, italic = true, jc = "center", spaceBeforeDxa = 0, spaceAfterDxa = 920))
        bodyXml.append(buildParagraphXml(contractorName, halfPtSize = 24, bold = true, jc = "center", spaceBeforeDxa = 0, spaceAfterDxa = 0))
        bodyXml.append("</w:tc>")

        bodyXml.append("<w:tc><w:tcPr><w:tcW w:w=\"4678\" w:type=\"dxa\"/></w:tcPr>")
        bodyXml.append(buildParagraphXml("NGƯỜI GIÁM SÁT THI CÔNG XÂY DỰNG", halfPtSize = 23, bold = true, jc = "center", spaceBeforeDxa = 160, spaceAfterDxa = 20))
        bodyXml.append(buildParagraphXml("(Ký và ghi rõ họ tên)", halfPtSize = 22, italic = true, jc = "center", spaceBeforeDxa = 0, spaceAfterDxa = 920))
        bodyXml.append(buildParagraphXml(supervisorName, halfPtSize = 24, bold = true, jc = "center", spaceBeforeDxa = 0, spaceAfterDxa = 0))
        bodyXml.append("</w:tc></w:tr></w:tbl>")

        bodyXml.append("<w:sectPr>")
        bodyXml.append("<w:pgSz w:w=\"11906\" w:h=\"16838\"/>")
        bodyXml.append("<w:pgMar w:top=\"1134\" w:right=\"850\" w:bottom=\"1134\" w:left=\"1701\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>")
        bodyXml.append("</w:sectPr>")

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml"
            xmlns:w15="http://schemas.microsoft.com/office/word/2012/wordml"
            mc:Ignorable="w14 w15">
  <w:body>
    $bodyXml
  </w:body>
</w:document>"""

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val docRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:cs="Times New Roman" w:eastAsia="Times New Roman"/>
        <w:color w:val="000000"/>
        <w:sz w:val="26"/>
        <w:szCs w:val="26"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
</w:styles>"""

        if (!outputDir.exists()) outputDir.mkdirs()
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "BienBanHienTruong_AnhsTeam_${docMode}_$ts.docx"
        val outFile = File(outputDir, fileName)

        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            fun addEntry(entryPath: String, content: String) {
                zos.putNextEntry(ZipEntry(entryPath))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            addEntry("[Content_Types].xml", contentTypesXml)
            addEntry("_rels/.rels", relsXml)
            addEntry("word/_rels/document.xml.rels", docRelsXml)
            addEntry("word/styles.xml", stylesXml)
            addEntry("word/document.xml", documentXml)
        }

        val publicDownloadPath = copyToPublicDownloads(context, outFile)

        return mapOf(
            "path" to outFile.absolutePath,
            "name" to outFile.name,
            "publicDownloadPath" to (publicDownloadPath ?: outFile.absolutePath),
            "sizeKB" to (outFile.length() / 1024L).coerceAtLeast(1L)
        )
    }

    private fun copyToPublicDownloads(context: Context, srcFile: File): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, srcFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AnhsTeam_HienTruong")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(srcFile).use { input -> input.copyTo(out) }
                    }
                    "/Download/AnhsTeam_HienTruong/${srcFile.name}"
                } else null
            } else {
                @Suppress("DEPRECATION")
                val dlDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AnhsTeam_HienTruong")
                if (!dlDir.exists()) dlDir.mkdirs()
                val dest = File(dlDir, srcFile.name)
                srcFile.copyTo(dest, overwrite = true)
                dest.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }
}
