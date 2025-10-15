package step.grid.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;


@Secured
public class JwtAuthenticationFilter implements ContainerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] jwtSecret;

    public JwtAuthenticationFilter(String jwtSecretKey) {
        this.jwtSecret = jwtSecretKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            abortWithUnauthorized(requestContext, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        
        try {
            Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token).getBody();
            
            logger.debug("Successfully validated JWT");
        } catch (JwtException e) {
            logger.warn("JWT validation failed: {}", e.getMessage());
            abortWithUnauthorized(requestContext, "Invalid JWT token");
        }
    }
    
    private void abortWithUnauthorized(ContainerRequestContext requestContext, String message) {
        logger.warn("Authentication failed: {}", message);
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .entity("{\"error\": \"" + message + "\"}")
                .type("application/json")
                .build()
        );
    }

    public static void registerSecurityFilterIfAuthenticationIsEnabled(SymmetricSecurityConfiguration securityConfiguration, ResourceConfig resourceConfig, String serviceName) {
        if (securityConfiguration != null && securityConfiguration.isJwtAuthenticationEnabled()) {
            logger.info("JWT authentication is enabled for {} services", serviceName);
            if (securityConfiguration.jwtSecretKey == null || securityConfiguration.jwtSecretKey.trim().isEmpty()) {
                throw new IllegalStateException("JWT secret key must be configured when JWT authentication is enabled");
            }
            JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(securityConfiguration.jwtSecretKey);
            resourceConfig.register(jwtAuthenticationFilter);
        } else {
            logger.warn("JWT authentication is DISABLED - {} services are not protected", serviceName);
        }
    }
}