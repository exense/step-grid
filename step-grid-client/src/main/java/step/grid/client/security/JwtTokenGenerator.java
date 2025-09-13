/*
 * Copyright (C) 2025, exense GmbH
 *
 * This file is part of Step
 *
 * Step is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Step is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Step.  If not, see <http://www.gnu.org/licenses/>.
 */

package step.grid.client.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.security.SymmetricSecurityConfiguration;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class JwtTokenGenerator {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenGenerator.class);
    private final String jwtSecret;

    public JwtTokenGenerator(String jwtSecretKey) {
        this.jwtSecret = jwtSecretKey;
    }

    public String generateToken() {
        return generateToken(3600); // 1 hour default
    }

    public String generateToken(long expirationSeconds) {
        Instant now = Instant.now();
        Instant expiration = now.plus(expirationSeconds, ChronoUnit.SECONDS);

        return Jwts.builder()
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .signWith(SignatureAlgorithm.HS256, jwtSecret.getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    public static JwtTokenGenerator initializeJwtTokenGenerator(SymmetricSecurityConfiguration securityConfiguration, String clientName) {
        final JwtTokenGenerator jwtTokenGenerator;
        if (securityConfiguration != null && securityConfiguration.isJwtAuthenticationEnabled()) {
            logger.info("JWT authentication enabled for {}", clientName);
            jwtTokenGenerator = new JwtTokenGenerator(securityConfiguration.jwtSecretKey);
        } else {
            logger.info("JWT authentication is disabled for {}", clientName);
            jwtTokenGenerator = null;
        }
        return jwtTokenGenerator;
    }

    public static Invocation.Builder withAuthentication(JwtTokenGenerator jwtTokenGenerator, Invocation.Builder requestBuilder) {
        if (jwtTokenGenerator != null) {
            String token = jwtTokenGenerator.generateToken();
            requestBuilder = requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return requestBuilder;
    }
}