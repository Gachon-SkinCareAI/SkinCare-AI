package skincare.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger(OpenAPI) 문서 설정 클래스 (JWT Bearer 인증 스키마 등록)
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class SwaggerConfig {
    // API 문서 기본 정보 및 전역 보안 요구사항 설정
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("3rd Project API Document")
                        .description("REST API 명세서입니다.")
                        .version("v0.0.1"))
                // 이 한 줄이 핵심: 모든 API에 bearerAuth 적용
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
