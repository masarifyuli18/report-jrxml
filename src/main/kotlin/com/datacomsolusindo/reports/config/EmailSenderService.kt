package com.datacomsolusindo.reports.config

import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import java.io.Serializable
import java.util.*
import javax.mail.internet.MimeMessage

@Component
class EmailSenderService {
    private val logger = LoggerFactory.getLogger(EmailSenderService::class.java)

    fun getJavaMailSender(u: Email?): JavaMailSender {
        val mailSender = JavaMailSenderImpl()
        mailSender.host = u?.serverName ?: "" //"smtp.gmail.com"
        mailSender.port = u?.port ?: 587 //587
        mailSender.username = u?.username ?: "" //"develope.dtcjogja@gmail.com"
        mailSender.password = u?.password ?: "" //"shdbmvbwajcuxbwh"
        configureJavaMailProperties(mailSender.javaMailProperties, u)

        return mailSender
    }

    private fun configureJavaMailProperties(properties: Properties, e: Email?) {
        properties["mail.transport.protocol"] = "smtp"
        properties["mail.smtp.auth"] = e?.smtpAuth?.toString()
        properties["mail.smtp.starttls.enable"] = e?.smtpStarttls?.toString()
        properties["mail.debug"] = "true"
    }

    fun sendEmail(
        emailConfig: Email?,
        email: EmailMessage,
        msgError: String? = null
    ): List<Exception> {
        val emailSender = getJavaMailSender(emailConfig)
        val content = emailConfig?.header + "<br><br>" + email.content + "<br><br>" + emailConfig?.footer
        return email.to.distinct().filterNot { it == "" }.mapNotNull { to ->
            try {
                logger.info("Sending email to $to")
                val message: MimeMessage = emailSender.createMimeMessage()
                val helper = MimeMessageHelper(message, true)
                helper.setTo(to)

                emailConfig?.username?.let { un -> helper.setFrom(un, "Power Assistant") }
                helper.setSubject(email.subject)
                helper.setText(content, true)
                email.attachment?.let { helper.addAttachment(email.attachmentName ?: "FileAttachment", it) }
                emailSender.send(message)
                null
            } catch (e: Exception) {
                logger.info("Invalid sending email, check format email")
                logger.error(msgError?.let { "$it to $to" } ?: "Error sending email to $to", e)
                e
            }
        }.toList()
    }

}

class EmailMessage(
    val to: Array<String>,
    val subject: String,
    val content: String,
    val attachmentName: String? = null,
    val attachment: ByteArrayResource? = null
)

class Email(
    val serverName: String,
    val port: Int,
    val username: String,
    val password: String,
    var smtpAuth:Boolean=true,
    var smtpStarttls:Boolean=true,
    var header: String = "",
    var footer: String = ""
) : Serializable