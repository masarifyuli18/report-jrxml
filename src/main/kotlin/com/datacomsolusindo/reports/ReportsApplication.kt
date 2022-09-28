package com.datacomsolusindo.reports

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.nativex.hint.TypeAccess
import org.springframework.nativex.hint.TypeHint
import org.springframework.nativex.hint.TypeHints
import org.springframework.scheduling.annotation.EnableScheduling

@TypeHints(
	TypeHint(
		types = arrayOf(
			PaSetting::class,
			PaAuth::class,
			PaResource::class,
			PaDatabase::class,
			PaMessaging::class
		),
		access = arrayOf(
			TypeAccess.PUBLIC_CLASSES,
			TypeAccess.DECLARED_CLASSES,
			TypeAccess.PUBLIC_METHODS,
			TypeAccess.PUBLIC_CONSTRUCTORS,
			TypeAccess.DECLARED_METHODS,
			TypeAccess.DECLARED_CONSTRUCTORS,
		)
	)
)
@SpringBootApplication
@EnableScheduling
class ReportsApplication

fun main(args: Array<String>) {
	System.setProperty("user.timezone", "Asia/Jakarta")
	U.buildConfiguration()
	SpringApplicationBuilder(ReportsApplication::class.java)
		.banner { _, _, out ->
			val banner: String = " --------------------------------\n" +
					" ____    _______    ____  \n" +
					" ]   \\      |      /    \\ \n" +
					" ]    |     |      |      \n" +
					" ]    |     |      |      \n" +
					" ]___/      |      \\____/ \n" +
					" Datacom Solusindo   2022 \n" +
					"                          \n" +
					" Power Assistant My Reports\n" +
					" --------------------------------\n"
			out.print(banner)
		}.run(*args, "--spring.config.location=./${U.tmpMyReportsConfig}")
}
