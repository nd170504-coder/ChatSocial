package com.chatsocial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@ConfigurationProperties(prefix = "app.supabase")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupabaseProperties {
    private String url;
    private String secretKey;
    private String bucket;
}
