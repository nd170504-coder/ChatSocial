package com.chatsocial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.supabase")
public record SupabaseProperties(String url, String secretKey, String bucket) {
    @Override
    public String toString() {
        return "SupabaseProperties[credentials redacted]";
    }
}
