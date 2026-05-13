package com.localrag.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "localrag.auth")
public class JwtProperties {

    private String jwtSecretKey;
    private int jwtExpirationDays = 7;
    private String registrationMode = "open";

    @Data
    public static class AdminBootstrap {
        private boolean enabled;
        private String username = "admin";
        private String password;
    }

    private AdminBootstrap adminBootstrap = new AdminBootstrap();
}
