package io.joopang.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.License
import io.swagger.v3.oas.annotations.servers.Server
import io.swagger.v3.oas.annotations.servers.ServerVariable
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.context.annotation.Configuration

@OpenAPIDefinition(
    info = Info(
        title = "HP API",
        version = "1.0.0",
        description = "Mock APIs for hp labs joopang project (products, orders, payments, coupons)",
        contact = Contact(
            name = "HP API Team",
            email = "team@hp.dev",
        ),
        license = License(
            name = "MIT License",
            url = "https://opensource.org/license/mit/",
        ),
    ),
    servers = [
        Server(
            url = "http://localhost:{port}",
            description = "Local",
            variables = [
                ServerVariable(
                    name = "port",
                    description = "HTTP port",
                    defaultValue = "8080",
                ),
            ],
        ),
    ],
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
)
@Configuration
class OpenApiConfig
