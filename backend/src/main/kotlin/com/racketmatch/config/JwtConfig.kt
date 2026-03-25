package com.racketmatch.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.jwt")
data class JwtConfig(
    var secret: String = "",
    var accessExpiry: Long = 900,
    var refreshExpiry: Long = 2592000
)
