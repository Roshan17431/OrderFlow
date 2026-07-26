package com.orderflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.auth.config.CookieProperties;
import com.orderflow.auth.domain.user.Role;
import com.orderflow.auth.domain.user.User;
import com.orderflow.auth.repository.UserRepository;
import com.orderflow.auth.security.TokenHasher;
import com.orderflow.auth.security.jwt.JwtService;
import com.orderflow.auth.security.oauth2.OAuthExchangeCodeStore;
import com.orderflow.auth.security.refresh.RefreshTokenStore;
import com.orderflow.auth.security.revocation.RevocationStateService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthenticationFlowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("orderflow_auth_flow")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private TokenHasher hasher;

    @Autowired
    private CookieProperties cookieProperties;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RevocationStateService revocations;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private OAuthExchangeCodeStore oauthCodes;

    @Autowired
    private RefreshTokenStore refreshTokens;

    @BeforeEach
    void cleanState() {
        jdbc.execute("TRUNCATE TABLE oauth_accounts, user_roles, users CASCADE");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void registrationPersistsHashRoleJwtAndHashedRefreshSession() throws Exception {
        MvcResult result = register("Flow.User@Example.COM");
        JsonNode body = json(result);
        String accessToken = body.path("accessToken").asText();
        Cookie refresh = result.getResponse().getCookie(cookieProperties.refreshCookieName());
        Jwt jwt = jwtDecoder.decode(accessToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(refresh).isNotNull();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("flow.user@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?",
                String.class,
                "flow.user@example.com");
        assertThat(passwordHash)
                .startsWith("$2")
                .doesNotContain("very strong password");
        assertThat(jdbc.queryForObject(
                "SELECT role FROM user_roles WHERE user_id = ?::uuid",
                String.class,
                jwt.getSubject())).isEqualTo("CUSTOMER");
        String refreshHash = hasher.sha256(refresh.getValue());
        String refreshKey = "security:refresh:" + refreshHash;
        assertThat(redis.type(refreshKey).code()).isEqualTo("hash");
        assertThat(redis.opsForHash()
                .get(refreshKey, "tokenHash"))
                .isEqualTo(refreshHash);
        assertThat(redis.getExpire(refreshKey, TimeUnit.SECONDS))
                .isPositive()
                .isLessThanOrEqualTo(TimeUnit.DAYS.toSeconds(14));
        assertThat(redis.keys("*" + refresh.getValue() + "*")).isEmpty();
    }

    @Test
    void csrfDuplicateLoginAndRoleSecurityUseSafeErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("person@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","displayName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        register("person@example.com");
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("PERSON@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@example.com","password":"incorrect password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));

        MvcResult login = login("person@example.com");
        String access = json(login).path("accessToken").asText();
        mockMvc.perform(get("/api/security/customer")
                        .header("X-Correlation-ID", "missing-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", "missing-token"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/security/customer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/security/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .header("X-Correlation-ID", "role-denied"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Correlation-ID", "role-denied"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void accountStatusAndSuccessfulLoginAttemptResetAreEnforced() throws Exception {
        register("status@example.com");
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"status@example.com","password":"wrong password value"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
        Set<String> attemptKeys = redis.keys("security:login-attempt:*");
        assertThat(attemptKeys).hasSize(1);
        assertThat(redis.getExpire(attemptKeys.iterator().next(), TimeUnit.SECONDS)).isPositive();

        login("status@example.com");
        assertThat(redis.keys("security:login-attempt:*")).isEmpty();

        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE email = ?", "status@example.com");
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"status@example.com","password":"very strong password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        jdbc.update("UPDATE users SET status = 'LOCKED' WHERE email = ?", "status@example.com");
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"status@example.com","password":"very strong password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void refreshRotatesAndReuseRevokesTheFamily() throws Exception {
        MvcResult registration = register("rotate@example.com");
        Cookie original = registration.getResponse().getCookie(cookieProperties.refreshCookieName());

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(original))
                .andExpect(status().isOk())
                .andReturn();
        Cookie replacement =
                refreshed.getResponse().getCookie(cookieProperties.refreshCookieName());
        assertThat(replacement).isNotNull();
        assertThat(replacement.getValue()).isNotEqualTo(original.getValue());

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(original))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(replacement))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAndLogoutAllRevokeAccessAndRefreshSessions() throws Exception {
        MvcResult first = register("logout@example.com");
        String firstAccess = json(first).path("accessToken").asText();
        Cookie firstRefresh =
                first.getResponse().getCookie(cookieProperties.refreshCookieName());
        MvcResult second = login("logout@example.com");
        String secondAccess = json(second).path("accessToken").asText();
        Cookie secondRefresh =
                second.getResponse().getCookie(cookieProperties.refreshCookieName());

        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .cookie(firstRefresh)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccess))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(cookieProperties.refreshCookieName(), 0));
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .cookie(firstRefresh)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccess))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));

        mockMvc.perform(post("/api/auth/logout-all")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccess))
                .andExpect(status().isNoContent());
        assertThat(refreshTokens.find(secondRefresh.getValue()))
                .get()
                .extracting(com.orderflow.auth.security.refresh.RefreshTokenMetadata::revoked)
                .isEqualTo(true);
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccess))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginThrottlingReturnsRetryAfterAtThreshold() throws Exception {
        register("throttle@example.com");
        for (int attempt = 1; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"throttle@example.com","password":"wrong password value"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"throttle@example.com","password":"wrong password value"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    @Test
    void concurrentRefreshAllowsExactlyOneSuccess() throws Exception {
        MvcResult registration = register("concurrent@example.com");
        Cookie original = registration.getResponse().getCookie(cookieProperties.refreshCookieName());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> attempts = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/auth/refresh")
                                    .with(csrf())
                                    .cookie(original))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = List.of(
                    attempts.get(0).get(),
                    attempts.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        }
    }

    @Test
    void adminRoleIsAcceptedAndInvalidTokensAreRejectedWithCorrelation() throws Exception {
        User admin = User.registeredCustomer(
                "admin@example.com",
                "$2a$12$placeholder",
                "Admin",
                Instant.now());
        admin.addRole(Role.ADMIN);
        users.saveAndFlush(admin);
        revocations.publishTokenVersion(admin.getId(), admin.getTokenVersion());
        String adminToken = jwtService.issue(admin, java.util.UUID.randomUUID()).value();
        mockMvc.perform(get("/api/security/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed.token.value")
                        .header("X-Correlation-ID", "invalid-token-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", "invalid-token-test"))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + customToken("https://wrong.example", Instant.now().plusSeconds(60))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + customToken("https://orderflow.local", Instant.now().minusSeconds(120))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + tamperSignature(customToken(
                                        "https://orderflow.local",
                                        Instant.now().plusSeconds(60)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oauthExchangeCodeIsShortLivedAndSingleUse() {
        java.util.UUID userId = java.util.UUID.randomUUID();
        String rawCode = oauthCodes.issue(userId);
        String key = "security:oauth-code:" + hasher.sha256(rawCode);
        assertThat(rawCode).doesNotContain(userId.toString());
        assertThat(redis.getExpire(key, TimeUnit.SECONDS))
                .isPositive()
                .isLessThanOrEqualTo(60);
        assertThat(oauthCodes.consume(rawCode)).contains(userId);
        assertThat(oauthCodes.consume(rawCode)).isEmpty();
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"very strong password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String registerJson(String email) {
        return """
                {"email":"%s","password":"very strong password","displayName":"Flow User"}
                """.formatted(email);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String customToken(String issuer, Instant expiresAt) {
        Instant now = Instant.now().minusSeconds(10);
        Instant issuedAt = expiresAt.isAfter(now) ? now : expiresAt.minusSeconds(60);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(java.util.UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(java.util.UUID.randomUUID().toString())
                .claim("email", "invalid@example.com")
                .claim("roles", List.of("CUSTOMER"))
                .claim("session_id", java.util.UUID.randomUUID().toString())
                .claim("token_version", 0)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256)
                        .keyId("orderflow-local")
                        .build(),
                claims)).getTokenValue();
    }

    private String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);
    }
}
