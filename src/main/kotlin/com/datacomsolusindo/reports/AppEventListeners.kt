package com.datacomsolusindo.reports

import com.datacomsolusindo.reports.config.GenerateReport
import com.datacomsolusindo.reports.entity.MyReportRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import javax.sql.DataSource
import kotlin.math.log


@Component
class AppEventListeners() {
    val logger: Logger = LoggerFactory.getLogger(AppEventListeners::class.java)

    @Autowired
    lateinit var myReportRepository: MyReportRepository

    @Autowired
    lateinit var dataSource: DataSource

    @Autowired
    lateinit var resourceLoader: ResourceLoader


    @EventListener(ApplicationReadyEvent::class)
    fun onAppReady() {
        logger.info("application ready")
        // check-myreport-schedule
        Thread({
            myReportRepository.findByScheduleGreaterThan(LocalDateTime.now()).forEach {
                val tmp = resourceLoader.getResource(
                    "file:template${U.spr}report${U.spr}${it.template}"
                )
                val report = GenerateReport(tmp, dataSource, myReportRepository)
                    .setCode(it.code)
                    .setReportType(it.reportType)
                    .setSchedule(it.schedule!!)
                it.parameter?.let { p ->
                    val pr = jacksonObjectMapper().readValue(p, Map::class.java) as Map<String, String>
                    report.addParameter(pr)
                    try {
                        pr["reportApiUrl"]?.let { api ->
                            val gs = U.unirestClient.get("${U.urlResource}$api")
                                .asString().ifFailure { f ->
                                    if (f.body.contains("Access is denied")) {
                                        logger.info("failed access ${U.urlResource}$api -> access denied")
                                    }
                                    f.parsingError.ifPresent { e ->
                                        logger.error("access ${U.urlResource}$api failed.", e)
                                    }
                                }
                            report.setReportDatasourceJson(gs.body)
                        }
                        report.buildReport()
                    } catch (e: Exception) {
                        logger.error("Error register schedule ${report.code}", e)
                    }
                }

            }
        }, "ThreadRegisterScheduleOnAppReady").start()

    }

}