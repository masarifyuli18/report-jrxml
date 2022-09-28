package com.datacomsolusindo.reports.config

import com.datacomsolusindo.reports.AppContext
import com.datacomsolusindo.reports.U
import com.datacomsolusindo.reports.entity.MyReport
import com.datacomsolusindo.reports.entity.MyReportRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.sf.jasperreports.engine.JREmptyDataSource
import net.sf.jasperreports.engine.JasperCompileManager
import net.sf.jasperreports.engine.JasperFillManager
import net.sf.jasperreports.engine.JasperPrint
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource
import net.sf.jasperreports.engine.export.FileHtmlResourceHandler
import net.sf.jasperreports.engine.export.HtmlExporter
import net.sf.jasperreports.engine.export.JRCsvExporter
import net.sf.jasperreports.engine.export.JRPdfExporter
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter
import net.sf.jasperreports.export.*
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.sql.DataSource


enum class ReportType { PDF, HTML, CSV, XLSX, SCREEN }
enum class ReportState { QUEUE, PROGRESS, DONE, FAIL }

class GenerateReport(
    private val template: Resource,
    private val dataSource: DataSource,
    private val myReportRepository: MyReportRepository
) {

    private val templateName = template.file.name
    private val logger = LoggerFactory.getLogger(GenerateReport::class.java)
    private var reportType: ReportType = ReportType.PDF
    private var parameters: MutableMap<String, Any> = mutableMapOf()
    var code = UUID.randomUUID().toString().replace("-", "").take(12).lowercase()
    private var reportFile = CompletableFuture<ByteArrayOutputStream>()
    private val path = when (reportType) {
        ReportType.SCREEN -> "pdf"
        ReportType.HTML -> "zip"
        else -> reportType.name.lowercase()
    }
    private var filename: String = code
    private var schedule: LocalDateTime? = null
    private var collectionObject: Collection<Any>? = null

    fun setCode(c: String): GenerateReport {
        this.code = c
        this.filename = c
        return this
    }

    fun setReportType(type: ReportType): GenerateReport {
        this.reportType = type
        return this
    }

    fun setSchedule(s: LocalDateTime): GenerateReport {
        this.schedule = s
        return this
    }

    fun addParameter(param: Map<String, Any>): GenerateReport {
        param.forEach { (t, u) -> parameters[t] = u.toString() }
        return this
    }

    fun setReportDatasourceJson(r: String): GenerateReport {
        this.collectionObject = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(r, object : TypeReference<List<Map<*, *>>>() {})
        return this
    }

    fun buildReport(): GenerateReport {
        val myRep = myReportRepository.findByCode(this.code)
            ?: MyReport(this.code, this.templateName, this.reportType, ReportState.QUEUE)
        parameters["reportEmail"]?.let { myRep.email = it.toString() }
        myRep.schedule = this.schedule
        myRep.startAt = myRep.schedule ?: LocalDateTime.now()
        myRep.parameter = jacksonObjectMapper().writeValueAsString(this.parameters)
        myReportRepository.save(myRep)
        this.schedule?.let { exec ->
            U.runningWithSchedule(this.code, "generate report $code", exec) run@{
                executionReport()
            }
        } ?: executionReport()
        return this
    }

    private fun executionReport() {
        val now = LocalDateTime.now()
        logger.info("generate report start at $now: code $code, template $templateName, reportType $reportType, parameters: $parameters")
        myReportRepository.findByCode(this.code)?.let {
            it.startAt = now
            it.reportState = ReportState.PROGRESS
            myReportRepository.save(it)
        }

        Thread({
            parameters["reportFilename"]?.let { filename = "${filename}_${it.toString().replace(" ", "_")}" }
            try {
                reportFile.complete(
                    dataSource.connection.use { con ->
                        val os = ByteArrayOutputStream()
                        val jasperRep = JasperCompileManager.compileReport(this.template.inputStream)

                        val jasperPrint: JasperPrint = collectionObject?.let {
                            parameters["collectionDataSource"] = JRBeanCollectionDataSource(it)
                            JasperFillManager.fillReport(jasperRep, parameters, JREmptyDataSource())
                        } ?: JasperFillManager.fillReport(jasperRep, parameters, con)

                        val toExporter = when (reportType) {
                            ReportType.PDF -> JRPdfExporter()
                            ReportType.SCREEN -> JRPdfExporter()
                            ReportType.XLSX -> JRXlsxExporter()
                            ReportType.CSV -> {
                                val cs = SimpleCsvExporterConfiguration()
                                cs.forceFieldEnclosure = true
                                val csvExporter = JRCsvExporter()
                                csvExporter.setConfiguration(cs)
                                csvExporter.exporterOutput = SimpleWriterExporterOutput(os)
                                csvExporter
                            }
                            ReportType.HTML -> {
                                if (!File(".tmp/$code").exists()) {
                                    File(".tmp/$code").mkdirs()
                                }
                                //reportGenerateId
                                val file = File(".tmp/$code/$templateName.html")
                                val htmlExp = HtmlExporter()
                                val configuration = SimpleHtmlExporterConfiguration()
                                htmlExp.setConfiguration(configuration)

                                val exporterOutput = SimpleHtmlExporterOutput(file)
                                val resourcesDir = File(file.parent, file.name.toString() + "_images")
                                val pathPattern = resourcesDir.name + "/{0}"
                                exporterOutput.imageHandler = FileHtmlResourceHandler(resourcesDir, pathPattern)
                                htmlExp.exporterOutput = exporterOutput
                                htmlExp
                            }
                        }
                        toExporter.setExporterInput(SimpleExporterInput(jasperPrint))
                        if (!(reportType == ReportType.CSV || reportType == ReportType.HTML)) {
                            toExporter.exporterOutput = SimpleOutputStreamExporterOutput(os)
                        } else if (reportType == ReportType.HTML) {
                            pack(".tmp/$code", os)
                        }
                        toExporter.exportReport()
                        os.flush()
                        os.close()
                        logger.info("success generate report with code $code")
                        os
                    }
                )
                toDirectory()
            } catch (e: Exception) {
                logger.error("failed generate report with code $code", e)
                myReportRepository.findByCode(this.code)?.let {
                    val n = LocalDateTime.now()
                    it.responseTime = n
                    it.lastExecution = n
                    it.info = "internal server error, generate report failed. ${e.message}"
                    it.reportState = ReportState.FAIL
                    myReportRepository.save(it)
                }
                sendWebSocket(ReportState.FAIL, "internal server error, generate report failed. ${e.message}")
            }
        }, "thread-generate-report-$code").start()
    }

    private fun toDirectory() {
        if (!File(U.pathDirectoryMyReport).exists()) {
            File(U.pathDirectoryMyReport).mkdirs()
        }
        val repFile = reportFile.get()
        File("${U.pathDirectoryMyReport}/$filename.$path").writeBytes(repFile.toByteArray())
        // find-myreport-database
        myReportRepository.findByCode(this.code)?.let {
            val now = LocalDateTime.now()
            it.responseTime = now
            it.lastExecution = now
            it.info = "success generate report."
            it.reportState = ReportState.DONE
            it.filename = "$filename.$path"
            myReportRepository.save(it)
            it.emails()?.let { e -> sendToEmail(e, repFile.toByteArray()) }
        }
        sendWebSocket(ReportState.DONE, "/api/myReports/download/$code")

        // schedule-to-delete-file (1 hari setelah generate)
        U.runningWithSchedule(this.code, "delete report $code", LocalDateTime.now().plusDays(1)) run@{
            if (File("${U.pathDirectoryMyReport}/$filename.$path").exists()) {
                File("${U.pathDirectoryMyReport}/$filename.$path").delete()
            }
        }

    }

    @Throws(IOException::class)
    private fun pack(sourceDirPath: String, os: OutputStream): OutputStream {
        ZipOutputStream(os).use { zs ->
            val pp: Path = Paths.get(sourceDirPath)
            Files.walk(pp)
                .filter { path -> !Files.isDirectory(path) }
                .forEach { path ->
                    val zipEntry = ZipEntry(pp.relativize(path).toString())
                    try {
                        zs.putNextEntry(zipEntry)
                        Files.copy(path, zs)
                        zs.closeEntry()
                    } catch (e: IOException) {
                        logger.error("pack error", e)
                    }
                }
        }
        return os
    }

    private fun sendToEmail(emails: MutableList<String>, attach: ByteArray) {
        // TO DO SEND EMAILS...
        AppContext.getBean(EmailSenderService::class.java).sendEmail(
            U.generalSettingEmail(),
            EmailMessage(
                emails.toTypedArray(),
                "Power Assistant - Schedule Report",
                "Hello, Power Assistant \n This is your report scheduled.",
                filename,
                ByteArrayResource(attach)
            )
        )
    }

    private fun sendWebSocket(st: ReportState, info: String) {
        // send-information-to-socket
        AppContext.getBean(SimpMessagingTemplate::class.java).convertAndSend(
            "/topic/report/state/$code", mapOf(
                Pair("code", code),
                Pair("state", st),
                Pair("info", info)
            )
        )
    }

}