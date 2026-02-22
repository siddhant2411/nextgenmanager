package com.nextgenmanager.nextgenmanager.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_LENGTH = 64;
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String DEFAULT_SECRET_PREFIX = "ReplaceThisWithAtLeast64CharLongJwtSecretKeyForHS512";

    private final String jwtSecret;
    private final long accessTokenExpirationMillis;
    private final long refreshTokenExpirationMillis;
    private final String issuer;
    private final String audience;
    private final String algorithm;

    public JwtService(
            @Value("${security.jwt.secret}") String jwtSecret,
            @Value("${security.jwt.accessExpirationMillis:900000}") long accessTokenExpirationMillis,
            @Value("${security.jwt.refreshExpirationMillis:604800000}") long refreshTokenExpirationMillis,
            @Value("${security.jwt.issuer:https://auth.erp.nextgenmanager.com}") String issuer,
            @Value("${security.jwt.audience:erp-backend}") String audience,
            @Value("${security.jwt.algorithm:HS512}") String algorithm,
            @Value("${spring.profiles.active:local}") String activeProfiles
    ) {
        this.jwtSecret = jwtSecret;
        this.accessTokenExpirationMillis = accessTokenExpirationMillis;
        this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
        this.issuer = issuer;
        this.audience = audience;
        this.algorithm = algorithm;
        failFastForInsecureSecretOutsideLocal(activeProfiles, jwtSecret);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String generateAccessToken(UserDetails userDetails) {
        return generateAccessToken(Map.of(), userDetails);
    }

    @Deprecated
    public String generateToken(UserDetails userDetails) {
        return generateAccessToken(userDetails);
    }

    @Deprecated
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return generateAccessToken(extraClaims, userDetails);
    }

    public String generateAccessToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + accessTokenExpirationMillis);
        logger.debug("Generating access token for user: {}", userDetails.getUsername());

        return Jwts.builder()
                .claims(extraClaims)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .subject(userDetails.getUsername())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(getSigningKey(), signatureAlgorithm())
                .compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + refreshTokenExpirationMillis);
        logger.debug("Generating refresh token for user: {}", userDetails.getUsername());

        return Jwts.builder()
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .subject(userDetails.getUsername())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(getSigningKey(), signatureAlgorithm())
                .compact();
    }

    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        String username = extractUsername(token);
        if (!username.equals(userDetails.getUsername())) {
            logger.debug("JWT username mismatch. tokenUser={}, actualUser={}", username, userDetails.getUsername());
        }
        return username.equals(userDetails.getUsername())
                && ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                && !isTokenExpired(claims)
                && hasExpectedIssuerAndAudience(claims);
    }

    @Deprecated
    public boolean isTokenValid(String token, UserDetails userDetails) {
        return isAccessTokenValid(token, userDetails);
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        String username = claims.getSubject();
        return username != null
                && username.equals(userDetails.getUsername())
                && REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                && !isTokenExpired(claims)
                && hasExpectedIssuerAndAudience(claims);
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMillis / 1000;
    }

    @Deprecated
    public long getExpirationSeconds() {
        return getAccessTokenExpirationSeconds();
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationMillis / 1000;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private boolean isTokenExpired(Claims claims) {
        Date expirationDate = claims.getExpiration();
        return expirationDate.before(new Date());
    }

    private boolean hasExpectedIssuerAndAudience(Claims claims) {
        if (!issuer.equals(claims.getIssuer())) {
            return false;
        }
        return claims.getAudience() != null && claims.getAudience().contains(audience);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT secret must be at least 64 characters long for HS512.");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private SignatureAlgorithm signatureAlgorithm() {
        if (!"HS512".equalsIgnoreCase(algorithm)) {
            throw new IllegalStateException("Only HS512 is supported. Configure security.jwt.algorithm=HS512");
        }
        return SignatureAlgorithm.HS512;
    }

    private void failFastForInsecureSecretOutsideLocal(String activeProfiles, String secret) {
        String profiles = activeProfiles == null ? "" : activeProfiles.toLowerCase();
        boolean isLocalLike = profiles.contains("local") || profiles.contains("dev") || profiles.contains("test");
        if (!isLocalLike && secret != null && secret.startsWith(DEFAULT_SECRET_PREFIX)) {
            throw new IllegalStateException("Default JWT secret is not allowed outside local/dev/test profiles.");
        }
    }
}
