package backlogs.dinamico.config;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Backlogs Dinámico API",
                version = "v1",
                description = """
                        API multi-tenant para ingestión, consulta y administración de logs.
                        
                        **Conceptos**
                        - **Tenant**: organización/empresa.
                        - **API Key**: credencial usada para ingerir logs.
                        - **Roles/Permisos**: control de acceso a endpoints.
                        
                        **Auth**
                        - La mayoría de endpoints requieren **JWT Bearer**.
                        """,
                contact = @Contact(
                        name = "Grupo Santoro",
                        email = "soporte@gruposantoro.com"
                ),
                license = @License(name = "Proprietary / Internal")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local"),
                @Server(url = "https://api.tu-dominio.com", description = "Producción")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
