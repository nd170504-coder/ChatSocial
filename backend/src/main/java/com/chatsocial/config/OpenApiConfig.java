package com.chatsocial.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI chatSocialOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("ChatSocial API").version("v1")
                        .description("API đăng ký, đăng nhập và mạng xã hội ChatSocial"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")
                                .description("Nhập accessToken nhận được từ API login, không thêm tiền tố Bearer.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
