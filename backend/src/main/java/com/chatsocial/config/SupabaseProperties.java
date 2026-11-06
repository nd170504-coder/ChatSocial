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
    private String postBucket = "post-images";

    public SupabaseProperties(String url, String secretKey, String bucket) {
        this.url = url;
        this.secretKey = secretKey;
        this.bucket = bucket;
    }
}
