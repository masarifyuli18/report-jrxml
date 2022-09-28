package com.datacomsolusindo.reports.config

import org.aopalliance.intercept.MethodInvocation
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.expression.SecurityExpressionRoot
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations
import org.springframework.security.authentication.AuthenticationTrustResolverImpl
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@EnableWebSecurity
class ResourceServerConfig {

    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain? {
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .authorizeRequests()
            .antMatchers("/api/**").authenticated()
            .and()
            .oauth2ResourceServer().jwt()

        http.cors().configurationSource(corsConfigurationSource())
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = mutableListOf("*")
        configuration.allowedMethods = mutableListOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = mutableListOf(
            "x-requested-with",
            "authorization",
            "Content-Type",
            "Authorization",
            "credential",
            "X-XSRF-TOKEN",
            "x-auth-token"
        )
        configuration.exposedHeaders = mutableListOf("x-auth-token")
        configuration.maxAge = 3600
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

}

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
class MethodSecurityConfig : GlobalMethodSecurityConfiguration() {
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    override fun createExpressionHandler(): MethodSecurityExpressionHandler {
        return CustomMethodSecurityExpressionHandler()
    }
}

class CustomMethodSecurityExpressionHandler : DefaultMethodSecurityExpressionHandler() {
    private val trustResolver = AuthenticationTrustResolverImpl()

    override fun createSecurityExpressionRoot(
        authentication: Authentication?,
        invocation: MethodInvocation?
    ): MethodSecurityExpressionOperations {
        if (authentication == null) {
            LoggerFactory.getLogger("DatacomLog").info("no auth here")
        }
        val root = CustomMethodSecurityExpressionRoot(authentication)
        root.setPermissionEvaluator(permissionEvaluator)
        root.setTrustResolver(this.trustResolver)
        root.setRoleHierarchy(roleHierarchy)
        return root
    }
}

class CustomMethodSecurityExpressionRoot(authentication: Authentication?) : SecurityExpressionRoot(authentication),
    MethodSecurityExpressionOperations {
    private var filterObject: Any? = null
    private var returnObject: Any? = null

    override fun setFilterObject(filterObject: Any?) {
        this.filterObject = filterObject
    }

    override fun getFilterObject() = this.filterObject

    override fun setReturnObject(returnObject: Any?) {
        this.returnObject = returnObject
    }

    override fun getReturnObject() = returnObject

    override fun getThis() = this
}
