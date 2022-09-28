package com.datacomsolusindo.reports.controller

import com.datacomsolusindo.reports.U
import com.datacomsolusindo.reports.config.GenerateReport
import com.datacomsolusindo.reports.config.ReportState
import com.datacomsolusindo.reports.config.ReportType
import com.datacomsolusindo.reports.entity.MyReport
import com.datacomsolusindo.reports.entity.MyReportRepository
import com.kawanansemut.simplequery.FilterData
import com.kawanansemut.simplequery.SimpleQuery
import org.apache.commons.io.IOUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ResourceLoader
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.rest.webmvc.BasePathAwareController
import org.springframework.data.web.PagedResourcesAssembler
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import javax.persistence.EntityManager
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.sql.DataSource

@BasePathAwareController
class ControllerReport(
    val resourceLoader: ResourceLoader,
    val dataSource: DataSource,
    val myReportRepository: MyReportRepository
) {

    val logger: Logger = LoggerFactory.getLogger(ControllerReport::class.java)

    @Autowired
    lateinit var entityManager: EntityManager

    @GetMapping("myReports/search/custom")
    fun custom(
        req: HttpServletRequest,
        pageable: Pageable,
        @RequestParam select: Array<String>,
        @RequestParam filter: String?,
        @RequestParam search: String?,
        @RequestParam sort: Array<String>?
    ): ResponseEntity<Any> {
        val sqBuilder = SimpleQuery
            .Builder(entityManager, MyReport::class.java)
            .select(*select)
        filter?.let { sqBuilder.andFilterDataJson(it) }
        search?.let {
            sqBuilder.andFilterData(
                FilterData.or(
                    FilterData.filter("code", FilterData.FILTEROP.LIKE, "%${it.lowercase()}%"),
                    FilterData.filter("template", FilterData.FILTEROP.LIKE, "%${it.lowercase()}%"),
                    FilterData.filter("filename", FilterData.FILTEROP.LIKE, "%${it.lowercase()}%")
                )
            )
        }
        sort?.let { sqBuilder.addSpringUrlSort(sort) }
        val sq = sqBuilder.build()
        val parameterList = req.parameterNames.toList()
        val limit = if (!parameterList.any { it == "size" }) null else pageable.pageSize
        val data = sq.resultListMap(limit = limit, offset = if (pageable.isPaged) pageable.pageNumber * (limit ?: 0) else 0)
        val totalData = limit?.let {
            if (pageable.pageNumber == 0 && data.size > it) {
                data.size.toLong()
            } else {
                sq.count()
            }
        } ?: data.size.toLong()
        val pg = limit?.let { pageable } ?: Pageable.unpaged()
        val pgData: Page<Map<String, *>> = PageImpl(data, pg, totalData)
        return ResponseEntity.ok(PagedResourcesAssembler<Map<String, *>>(null, null).toModel(pgData))
    }

    @GetMapping("myReports/generate")
    fun reportGenerate(
        req: HttpServletRequest, res: HttpServletResponse,
        @RequestParam reportTemplate: String,
        @RequestParam reportType: String
    ): ResponseEntity<Any> {
        val tmp = resourceLoader.getResource(
            "file:template${U.spr}report${U.spr}${reportTemplate.replace(".jrxml", "")}.jrxml"
        )
        if (!tmp.exists()) {
            return ResponseEntity.badRequest().body("Report template not found.")
        }
        if (!ReportType.values().any { it.name.lowercase() == reportType.lowercase() }) {
            return ResponseEntity.badRequest().body("Report type is not registered.")
        }

        val r = GenerateReport(tmp, dataSource, myReportRepository).setReportType(ReportType.valueOf(reportType))
        req.parameterMap.forEach { (k, _) -> r.addParameter(mapOf(Pair(k, req.getParameter(k).toString()))) }
        r.buildReport()

        return ResponseEntity.ok(
            mapOf(
                Pair("code", r.code),
                Pair("info", "success register generate report.")
            )
        )
    }

    @GetMapping("myReports/generate/schedule")
    fun reportGenerateSchedule(
        req: HttpServletRequest, res: HttpServletResponse,
        @RequestParam reportTemplate: String,
        @RequestParam reportType: String,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") reportScheduleTime: LocalDateTime
    ): ResponseEntity<Any> {
        val tmp = resourceLoader.getResource(
            "file:template${U.spr}report${U.spr}${reportTemplate.replace(".jrxml", "")}.jrxml"
        )
        if (!tmp.exists()) {
            return ResponseEntity.badRequest().body("Report template not found.")
        }
        if (!ReportType.values().any { it.name.lowercase() == reportType.lowercase() }) {
            return ResponseEntity.badRequest().body("Report type is not registered.")
        }

        val r = GenerateReport(tmp, dataSource, myReportRepository).setReportType(ReportType.valueOf(reportType))
        req.parameterMap.forEach { (k, _) -> r.addParameter(mapOf(Pair(k, req.getParameter(k).toString()))) }
        r.setSchedule(reportScheduleTime)
        r.buildReport()

        return ResponseEntity.ok(
            mapOf(
                Pair("code", r.code),
                Pair("info", "success register generate report.")
            )
        )
    }

    @GetMapping("myReports/state/{code}")
    fun reportState(
        req: HttpServletRequest, res: HttpServletResponse, @PathVariable code: String
    ): Any {
        return myReportRepository.findByCode(code)?.let {
            ResponseEntity.ok(
                mapOf(
                    Pair("code", it.code),
                    Pair("state", it.reportState),
                    Pair(
                        "info", when (it.reportState) {
                            ReportState.DONE -> "/api/myReports/download/${it.code}"
                            ReportState.FAIL -> it.info ?: "failed generate report"
                            else -> ""
                        }
                    )
                )
            )
        } ?: ResponseEntity.status(HttpStatus.NOT_FOUND)
    }

    @GetMapping("myReports/download/{code}")
    fun downloadReport(
        req: HttpServletRequest, res: HttpServletResponse, @PathVariable code: String
    ): Any {
        return myReportRepository.findByCode(code)?.let {
            val tmp = resourceLoader.getResource(
                "file:${U.pathDirectoryMyReport}${U.spr}${it.filename}"
            )
            if (!tmp.exists()) {
                return ResponseEntity.badRequest().body("Report not found.")
            }
            val fName = it.filename?.replace("${it.code}_", "") ?: code
            val header = HttpHeaders()
            header.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
            header.add("Cache-Control", "no-cache, no-store, must-revalidate")
            header.add("Pragma", "no-cache")
            header.add("Expires", "0")
            header.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$fName")
            res.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$fName")
            IOUtils.copy(ByteArrayInputStream(tmp.file.readBytes()), res.outputStream)
            res.flushBuffer()
            return ResponseEntity.ok()
        } ?: ResponseEntity.status(HttpStatus.NOT_FOUND)

    }

    @DeleteMapping("myReports/{code}")
    fun deleteFile(
        req: HttpServletRequest, res: HttpServletResponse, @PathVariable code: String
    ): Any {
        myReportRepository.findByCode(code)?.let {
            val tmp = resourceLoader.getResource(
                "file:${U.pathDirectoryMyReport}${U.spr}${it.filename}"
            )
            if (tmp.exists()) {
                tmp.file.delete()
            }
        }
        return ResponseEntity.status(HttpStatus.OK)
    }

    @DeleteMapping("myReports/schedule/cancel/{code}")
    fun deleteSchedule(
        req: HttpServletRequest, res: HttpServletResponse, @PathVariable code: String
    ): Any {
        myReportRepository.findByCode(code)?.let {
            it.enabled = false
            myReportRepository.save(it)
        }
        return ResponseEntity.status(HttpStatus.OK)
    }

    @GetMapping("myReports/generate/json")
    fun reportGenerateDatasourceJSON(
        req: HttpServletRequest, res: HttpServletResponse,
        @RequestParam reportTemplate: String,
        @RequestParam reportType: String,
        @RequestParam reportApiUrl: String
    ): ResponseEntity<Any> {
        U.tokenAccess = req.getHeader("authorization")
        val tmp = resourceLoader.getResource(
            "file:template${U.spr}report${U.spr}${reportTemplate.replace(".jrxml", "")}.jrxml"
        )
        if (!tmp.exists()) {
            return ResponseEntity.badRequest().body("Report template not found.")
        }
        if (!ReportType.values().any { it.name.lowercase() == reportType.lowercase() }) {
            return ResponseEntity.badRequest().body("Report type is not registered.")
        }

        try {
            val gs = U.unirestClient.get("${U.urlResource}$reportApiUrl")
                .asString().ifFailure {
                    if (it.body.contains("Access is denied")) {
                        logger.info("failed access ${U.urlResource}$reportApiUrl -> access denied")
                    }
                    it.parsingError.ifPresent { e ->
                        logger.error("access ${U.urlResource}$reportApiUrl failed.", e)
                    }
                }
            val r = GenerateReport(tmp, dataSource, myReportRepository).setReportType(ReportType.valueOf(reportType))
            req.parameterMap.forEach { (k, _) -> r.addParameter(mapOf(Pair(k, req.getParameter(k).toString()))) }
            r.setReportDatasourceJson(gs.body)
            r.buildReport()

            return ResponseEntity.ok(mapOf(Pair("code", r.code), Pair("info", "success register generate report.")))
        } catch (e: Exception) {
            return ResponseEntity.ok(mapOf(Pair("code", ""), Pair("info", "failed register generate report.")))
        }

    }


}