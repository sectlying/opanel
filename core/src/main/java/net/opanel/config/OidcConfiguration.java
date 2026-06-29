package net.opanel.config;

import java.util.ArrayList;
import java.util.List;

public class OidcConfiguration {
    public boolean enabled;
    public String discoveryUrl;
    public String clientId;
    public String clientSecret;
    public List<String> allowedUserIds;
    public String displayName;

    public OidcConfiguration(boolean enabled) {
        this.enabled = enabled;
        this.discoveryUrl = "";
        this.clientId = "";
        this.clientSecret = "";
        this.allowedUserIds = new ArrayList<>();
        this.displayName = "OIDC";
    }
}
