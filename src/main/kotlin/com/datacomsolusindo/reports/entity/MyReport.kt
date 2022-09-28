package com.datacomsolusindo.reports.entity

import com.datacomsolusindo.reports.config.ReportState
import com.datacomsolusindo.reports.config.ReportType
import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.rest.core.annotation.RestResource
import org.springframework.stereotype.Component
import java.io.Serializable
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["code"], name = "UKMyReportCode")
    ]
)
class MyReport(
    @Column(nullable = false, length = 12)
    var code: String = "",
    @Column(nullable = false, length = 255)
    var template: String = "",
    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    var reportType: ReportType = ReportType.PDF,
    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    var reportState: ReportState = ReportState.QUEUE
) : Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int = 0

    @Column(length = 255)
    var email: String? = null

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var schedule: LocalDateTime? = null

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var startAt: LocalDateTime? = null

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var responseTime: LocalDateTime? = null

    @Column(length = 255)
    var info: String? = null

    @Column(length = 255)
    var filename: String? = null

    @Column(length = 5000)
    var parameter: String? = null

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var lastExecution: LocalDateTime? = null

    var enabled: Boolean = true

    fun emails() : MutableList<String>? {
       return email?.split(";")?.distinct()?.toMutableList()
    }

}

@NoRepositoryBean
interface UniversalRepository<T, ID : Serializable> :
    PagingAndSortingRepository<T, ID>,
    JpaSpecificationExecutor<T>,
    CrudRepository<T, ID> {}

@Component
interface MyReportRepository : UniversalRepository<MyReport, Int> {

    @RestResource(exported = false)
    fun findByCode(code: String): MyReport?

    @RestResource(exported = false)
    fun findByScheduleGreaterThan(schedule: LocalDateTime): MutableList<MyReport>

    override fun <S : MyReport> save(entity: S): S

    override fun <S : MyReport> saveAll(entities: MutableIterable<S>): MutableIterable<S>

    override fun findById(id: Int): Optional<MyReport>

    override fun existsById(id: Int): Boolean

    override fun findAll(sort: Sort): MutableIterable<MyReport>

    override fun findAll(pageable: Pageable): Page<MyReport>

    override fun findAll(): MutableIterable<MyReport>

    override fun findAll(spec: Specification<MyReport>?): MutableList<MyReport>

    override fun findAll(spec: Specification<MyReport>?, pageable: Pageable): Page<MyReport>

    override fun findAll(spec: Specification<MyReport>?, sort: Sort): MutableList<MyReport>

    override fun findAllById(ids: MutableIterable<Int>): MutableIterable<MyReport>

    override fun count(): Long

    override fun count(spec: Specification<MyReport>?): Long

    override fun deleteById(id: Int)

    override fun delete(entity: MyReport)

    override fun deleteAllById(ids: MutableIterable<Int>)

    override fun deleteAll(entities: MutableIterable<MyReport>)

    override fun deleteAll()

    override fun findOne(spec: Specification<MyReport>?): Optional<MyReport>
}