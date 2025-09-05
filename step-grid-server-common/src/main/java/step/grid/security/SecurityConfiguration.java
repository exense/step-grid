package step.grid.security;

public class SecurityConfiguration {
    
    public boolean jwtAuthenticationEnabled = false;
    public String jwtSecretKey;

    public SecurityConfiguration() {
    }
    
    public SecurityConfiguration(boolean jwtAuthenticationEnabled, String jwtSecretKey) {
        this.jwtAuthenticationEnabled = jwtAuthenticationEnabled;
        this.jwtSecretKey = jwtSecretKey;
    }
}