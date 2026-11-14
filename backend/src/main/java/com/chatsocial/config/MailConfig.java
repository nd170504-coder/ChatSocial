package com.chatsocial.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({EmailVerificationProperties.class, BrevoProperties.class})
public class MailConfig {}
