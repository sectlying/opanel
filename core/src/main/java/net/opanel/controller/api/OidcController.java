package net.opanel.controller.api;

import com.nimbusds.jwt.JWTClaimsSet;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import net.opanel.OPanel;
import net.opanel.config.OidcConfiguration;
import net.opanel.controller.BaseController;
import net.opanel.storage.Storage;
import net.opanel.storage.StorageKey;
import net.opanel.web.JwtManager;
import net.opanel.web.OidcManager;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class OidcController extends BaseController {
    private OidcManager oidcManager;
    private boolean initialized = false;

    private static final int MAX_VERIFY_TRIES = 5;
    private static final long BANNED_PERIOD = 10 * 60 * 1000; // 10 min
    private final ConcurrentHashMap<String, Integer> failedVerifyRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> temporaryBannedRecords = new ConcurrentHashMap<>();

    public OidcController(OPanel plugin, OidcManager oidcManager) {
        super(plugin);
        this.oidcManager = oidcManager;
    }

    /**
     * Initialize the OIDC provider discovery if not already done.
     * Called lazily on first request.
     */
    private void ensureInitialized() throws Exception {
        if (initialized && oidcManager.isDiscovered()) return;

        OidcConfiguration oidcConfig = Storage.get().getStoredData(StorageKey.OIDC_CONFIG);
        if (oidcConfig == null || !oidcConfig.enabled) return;

        oidcManager.discover(oidcConfig.discoveryUrl, oidcConfig.clientId);
        initialized = true;
    }

    /**
     * GET /api/auth/oidc/login
     * Redirects the user to the OIDC provider's authorization endpoint.
     */
    public Handler login = ctx -> {
        OidcConfiguration oidcConfig = Storage.get().getStoredData(StorageKey.OIDC_CONFIG);
        if (oidcConfig == null || !oidcConfig.enabled) {
            sendResponse(ctx, HttpStatus.SERVICE_UNAVAILABLE, "OIDC is not enabled.");
            return;
        }

        try {
            ensureInitialized();
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(ctx, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to discover OIDC provider: " + e.getMessage());
            return;
        }

        try {
            String redirectUri = buildRedirectUri(ctx);
            String authUrl = oidcManager.buildAuthorizationUrl(oidcConfig.clientId, redirectUri);
            ctx.redirect(authUrl, HttpStatus.FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(ctx, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build OIDC authorization URL: " + e.getMessage());
        }
    };

    /**
     * GET /api/auth/oidc/callback
     * Handles the OIDC provider's callback, validates the ID token,
     * and either logs the user in or redirects to the bind page.
     */
    public Handler callback = ctx -> {
        OidcConfiguration oidcConfig = Storage.get().getStoredData(StorageKey.OIDC_CONFIG);
        if (oidcConfig == null || !oidcConfig.enabled) {
            sendResponse(ctx, HttpStatus.SERVICE_UNAVAILABLE, "OIDC is not enabled.");
            return;
        }

        try {
            ensureInitialized();
        } catch (Exception e) {
            e.printStackTrace();
            ctx.redirect("/login?oidc-error=true", HttpStatus.FOUND);
            return;
        }

        String callbackUrl = ctx.fullUrl();
        String redirectUri = buildRedirectUri(ctx);

        JWTClaimsSet claims;
        try {
            claims = oidcManager.handleCallback(callbackUrl, oidcConfig.clientId, oidcConfig.clientSecret, redirectUri);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.redirect("/login?oidc-error=true", HttpStatus.FOUND);
            return;
        }

        String userId = claims.getSubject();
        if (userId == null || userId.isEmpty()) {
            ctx.redirect("/login?oidc-error=true", HttpStatus.FOUND);
            return;
        }

        // Check if this user ID is in the allowed list
        if (oidcConfig.allowedUserIds != null && oidcConfig.allowedUserIds.contains(userId)) {
            // User is allowed - issue JWT and redirect to dashboard
            String token = JwtManager.generateToken(plugin.getConfig().accessKey, plugin.getConfig().salt);
            try {
                ctx.cookie(JwtManager.createCookie("token", token, (int) TimeUnit.DAYS.toSeconds(1), plugin.getConfig().cookieSecure));
            } catch (NoSuchMethodError e) {
                // Java < 21 compatibility
            }
            ctx.redirect("/panel/dashboard", HttpStatus.FOUND);
        } else {
            // User is not allowed - redirect to login with bind flag, store pending userId in cookie
            try {
                ctx.cookie(JwtManager.createCookie("oidc-pending-user", userId, 600, plugin.getConfig().cookieSecure));
            } catch (NoSuchMethodError e) {
                // Java < 21 compatibility
            }
            ctx.redirect("/login?oidc-bind=true", HttpStatus.FOUND);
        }
    };

    /**
     * POST /api/auth/oidc/verify-secret
     * Verifies the access key for a pending OIDC user.
     * If the key matches, adds the user ID to allowed-user-ids and issues a JWT.
     */
    public Handler verifySecret = ctx -> {
        OidcConfiguration oidcConfig = Storage.get().getStoredData(StorageKey.OIDC_CONFIG);
        if (oidcConfig == null || !oidcConfig.enabled) {
            sendResponse(ctx, HttpStatus.SERVICE_UNAVAILABLE, "OIDC is not enabled.");
            return;
        }

        String reqIp = getClientIp(ctx);
        if (reqIp == null || reqIp.isBlank()) {
            sendResponse(ctx, HttpStatus.FORBIDDEN, "Cannot determine client IP address.");
            return;
        }
        if (checkTemporaryBan(reqIp)) {
            sendResponse(ctx, HttpStatus.FORBIDDEN, "The IP is banned temporarily.");
            return;
        }

        String pendingUserId = ctx.cookie("oidc-pending-user");
        if (pendingUserId == null || pendingUserId.isEmpty()) {
            sendResponse(ctx, HttpStatus.BAD_REQUEST, "No pending OIDC user. Please try logging in again.");
            return;
        }

        RequestBodyType reqBody = ctx.bodyAsClass(RequestBodyType.class);
        if (reqBody == null || reqBody.accessKey == null) {
            sendResponse(ctx, HttpStatus.BAD_REQUEST, "Access key is missing.");
            return;
        }

        // Verify the access key (reqBody.accessKey is hashed 2, same as stored accessKey)
        final String hashedAccessKey = plugin.getConfig().accessKey;
        if (!reqBody.accessKey.equals(hashedAccessKey)) {
            int current = failedVerifyRecords.merge(reqIp, 1, Integer::sum);
            if (current >= MAX_VERIFY_TRIES) {
                temporaryBannedRecords.put(reqIp, System.currentTimeMillis() + BANNED_PERIOD);
                failedVerifyRecords.remove(reqIp);
            }
            sendResponse(ctx, HttpStatus.FORBIDDEN, "Access key mismatch.");
            return;
        }

        // Success - reset failure count
        failedVerifyRecords.remove(reqIp);

        // Add the user ID to allowed-user-ids
        if (oidcConfig.allowedUserIds == null) {
            oidcConfig.allowedUserIds = new java.util.ArrayList<>();
        }
        if (!oidcConfig.allowedUserIds.contains(pendingUserId)) {
            oidcConfig.allowedUserIds.add(pendingUserId);
            Storage.get().setStoredData(StorageKey.OIDC_CONFIG, oidcConfig);
        }

        // Issue JWT and clear pending cookie
        String token = JwtManager.generateToken(hashedAccessKey, plugin.getConfig().salt);
        try {
            ctx.cookie(JwtManager.createCookie("token", token, (int) TimeUnit.DAYS.toSeconds(1), plugin.getConfig().cookieSecure));
        } catch (NoSuchMethodError e) {
            // Java < 21 compatibility
        }
        ctx.removeCookie("oidc-pending-user");

        sendResponse(ctx, new HashMap<>());
    };

    /**
     * GET /api/auth/oidc/config
     * Returns the OIDC configuration for the frontend to adjust the login UI.
     */
    public Handler getConfig = ctx -> {
        OidcConfiguration oidcConfig = Storage.get().getStoredData(StorageKey.OIDC_CONFIG);

        HashMap<String, Object> res = new HashMap<>();
        if (oidcConfig != null && oidcConfig.enabled) {
            res.put("enabled", true);
            res.put("displayName", oidcConfig.displayName);
        } else {
            res.put("enabled", false);
        }
        sendResponse(ctx, res);
    };

    private String buildRedirectUri(io.javalin.http.Context ctx) {
        String scheme = ctx.scheme();
        String host = ctx.host();
        return scheme + "://" + host + "/api/auth/oidc/callback";
    }

    private static class RequestBodyType {
        String accessKey; // hashed 2
    }

    private boolean checkTemporaryBan(String ip) {
        Long banUntil = temporaryBannedRecords.get(ip);
        if (banUntil == null) return false;
        if (System.currentTimeMillis() < banUntil) return true;
        temporaryBannedRecords.remove(ip, banUntil);
        return false;
    }
}
