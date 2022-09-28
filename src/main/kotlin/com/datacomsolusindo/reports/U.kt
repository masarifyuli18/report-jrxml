package com.datacomsolusindo.reports

import com.datacomsolusindo.reports.config.Email
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kong.unirest.*
import kong.unirest.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.math.abs
import kotlin.system.exitProcess

object U {
    private val logger = LoggerFactory.getLogger(U::class.java)
    val spr = File.separator
    val tmpMyReportsConfig = "conf${U.spr}tmp${U.spr}pa-myreports-config.yml"
    val pathDirectoryMyReport = "myreport${U.spr}tmp"
    val pathDirectoryTemplate: String by lazy {
        loadConfigYml("conf/pa-setting.yml")?.get("report-template")?.toString() ?: "template/report"
    }
    var tokenAccess : String = ""
    val urlResource : String by lazy {
        val p = paSettingConfig
        "http://${p.resource.ip ?: p.ip}:${p.resource.port}"
    }

    private fun loadConfigYml(path: String): Map<String, Any>? {
        val f = File(path)
        return if (f.exists()) {
            val yaml = Yaml()
            yaml.load(FileInputStream(f))
        } else null
    }

    private fun pairToMap(key: String, obj: Any): MutableMap<String, Any> = mutableMapOf(Pair(key, obj))

    fun buildConfiguration() {
        logger.info("Build configuration...")
        val fileConfig = File(tmpMyReportsConfig)
        if (fileConfig.exists()) {
            fileConfig.delete()
        }

        if (!File("conf/tmp").exists()) {
            File("conf/tmp").mkdirs()
        }

        val conf: MutableMap<String, Any> = mutableMapOf()
        val fi = paSettingConfig
        val urlAuth = "${fi.auth.ip ?: fi.ip}:${fi.auth.port}"
        conf["server"] = pairToMap("port", 9099)
        conf["spring"] = mutableMapOf(
            Pair(
                "security", pairToMap(
                    "oauth2", pairToMap(
                        "resourceserver", pairToMap(
                            "jwt",
                            pairToMap("issuer-uri", "http://$urlAuth")
                        )
                    )
                )
            ),
            Pair("data", pairToMap("rest", pairToMap("basePath", "/api"))),
            Pair(
                "jpa", mutableMapOf(
                    Pair(
                        "properties", mutableMapOf(
                            Pair(
                                "hibernate", mutableMapOf(
                                    Pair("dialect", "org.hibernate.dialect.SQLServer2012Dialect"),
                                    Pair("format_sql", true)
                                )
                            )
                        )
                    ),
                    Pair(
                        "hibernate", mutableMapOf(
                            Pair("ddl-auto", "update"),
                            Pair(
                                "naming",
                                pairToMap(
                                    "physical-strategy",
                                    "org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy"
                                )
                            )
                        )
                    )
                )
            ),
            Pair(
                "datasource", mutableMapOf(
                    Pair("driver-class-name", "com.microsoft.sqlserver.jdbc.SQLServerDriver"),
                    Pair(
                        "url",
                        "jdbc:sqlserver://${fi.database.ip ?: fi.ip}:${fi.database.port};databaseName=${fi.database.name}"
                    ),
                    Pair("username", fi.database.username),
                    Pair("password", fi.database.password)
                )
            ),
            Pair(
                "flyway", mutableMapOf(
                    Pair("enabled", true),
                    Pair("locations", "classpath:db/migration"),
                    Pair("table", "powerassistant_schema_history"),
                    Pair("baselineOnMigrate", true)
                )
            ),
            Pair("main.banner-mode", "LOG")
        )

        conf["logging"] = mutableMapOf(
            Pair("level", pairToMap("root", "INFO")),
            Pair(
                "file", mutableMapOf(Pair("name", "logs/myrepots/myrepots.log"))
            ),
            Pair(
                "logback.rollingpolicy", mutableMapOf(
                    Pair("file-name-pattern", "logs/myrepots/myrepots-%d{yyyy-MM-dd}.%i.log"),
                    Pair("max-file-size", "1MB"),
                    Pair("total-size-cap", "128MB")
                )
            )
        )

        YAMLMapper().writeValue(File(tmpMyReportsConfig), conf)
    }

    private val paSettingConfig: PaSetting by lazy {
        val se = loadConfigYml("conf/pa-setting.yml")
        if (se == null) {
            logger.info("Cant build file configuration app.")
            exitProcess(1)
        }
        val seAuth = se["auth"] as MutableMap<*, *>
        val seRes = se["resource"] as MutableMap<*, *>
        val seDb = se["database"] as MutableMap<*, *>
        val seMsg = se["messaging"] as MutableMap<*, *>
        val seLic = se["license"] as MutableMap<*, *>

        PaSetting(
            se["ip"] as String,
            PaAuth(seAuth["ip"]?.let { it as String }, seAuth["port"]?.let { it as Int } ?: 8090),
            PaResource(seRes["ip"]?.let { it as String }, seRes["port"]?.let { it as Int } ?: 8080),
            PaDatabase(seDb["ip"]?.let { it as String }, seDb["port"]?.let { it as Int } ?: 1433,
                seDb["username"]?.let { it as String } ?: "", seDb["password"]?.let { it as String } ?: "",
                seDb["name"]?.let { it as String } ?: ""
            ),
            PaMessaging(
                seMsg["key"]?.let { it as String } ?: "ws",
                seMsg["ip"]?.let { it as String },
                seMsg["port"]?.let { it as Int } ?: 5000
            ),
            seLic["path"].toString()
        )
    }

    fun runningWithSchedule(code: String, title: String, dt: LocalDateTime, run: () -> Unit) {
        val now = LocalDateTime.now()
        logger.info("Registered scheduled $title at ${dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        val time = Timer("RunOnTime")
        time.schedule(timerTask {
            try {
                run()
            } catch (e: Exception) {
                logger.error("Error running schedule", e)
            }
        }, abs(ChronoUnit.MILLIS.between(dt, now)))
    }

    val unirestClient: UnirestInstance by lazy {
        val ni = Unirest.spawnInstance()
        ni.config().interceptor(UInterceptor())
        ni
    }

//    @Synchronized
//    fun loadToken(): String {
//        val resp = Unirest.post("$serviceUrl/oauth2/token")
//            .basicAuth("auth-client", "auth-secret")
//            .field("grant_type", "client_credentials")
//            .asJson().ifFailure {
//                logger.info("load token failed")
//            }
//        return resp.body.`object`["access_token"].toString()
//    }

    private val serviceUrl: String by lazy {
        val pa = paSettingConfig
        "http://${pa.auth.ip ?: pa.ip}:${pa.auth.port}"
    }

    fun generalSettingEmail(): Email? {
        return try {
            val gs = unirestClient.get("http://192.168.100.18:9090/api/generalSettings")
                .asJson().ifFailure {
                    logger.info("access general setting failed.")
                    it.parsingError.ifPresent { e ->
                        logger.error("access general setting failed.", e)
                    }
                }
            val dt = gs.body.`object`["setting"] as JSONObject
            jacksonObjectMapper().readValue(dt.get("email").toString(), Email::class.java)
        } catch (e: Exception) {
            null
        }
    }

}

class PaSetting(
    val ip: String, val auth: PaAuth, val resource: PaResource,
    val database: PaDatabase, val messaging: PaMessaging, val licensePath: String
)

class PaAuth(val ip: String?, val port: Int)
class PaResource(val ip: String?, val port: Int)
class PaDatabase(val ip: String?, val port: Int, val username: String, val password: String, val name: String)
class PaMessaging(val key: String, val ip: String?, val port: Int)

class UInterceptor : Interceptor {
    private val logger = LoggerFactory.getLogger(UInterceptor::class.java)
    override fun onRequest(request: HttpRequest<*>, config: Config) {
//        request.header("Authorization", "Bearer ${U.loadToken()}")
        request.header("Authorization", U.tokenAccess)
        super.onRequest(request, config)
    }

    override fun onResponse(response: HttpResponse<*>, request: HttpRequestSummary, config: Config) {
        response.ifFailure { it.parsingError.ifPresent { e ->
            logger.error("error response", e)
        } }
        super.onResponse(response, request, config)
    }
}

@Component
class AppContext : ApplicationContextAware {
    override fun setApplicationContext(context: ApplicationContext) {
        CONTEXT = context
    }

    companion object {
        private var CONTEXT: ApplicationContext? = null
        fun <T> getBean(clazz: Class<T>): T = CONTEXT!!.getBean(clazz)
    }
}
