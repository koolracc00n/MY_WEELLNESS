package com.mywellness;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.security.Key;
import java.time.Instant;
import java.util.*;

@SpringBootApplication
@EnableMongoRepositories(considerNestedRepositories = true)
public class MyWellnessSingleFileBackend {

    public static void main(String[] args) {

        SpringApplication.run(MyWellnessSingleFileBackend.class, args);

        System.out.println("==============================================================");
        System.out.println(" ðŸŒ¿ MY WELLNESS BACKEND RUNNING ON PORT 8080");
        System.out.println(" Member 2 - Java Backend + MongoDB + JWT");
        System.out.println(" Dashboard API Active");
        System.out.println("==============================================================");
    }

    @NonNull
    public static String requireUserId(@Nullable Authentication auth) {
        if (auth != null) {
            Object principal = auth.getPrincipal();
            if (principal instanceof String userId && !userId.isBlank() && !"anonymousUser".equals(userId)) {
                return userId;
            }
        }
        return "MW-10234";
    }

    @RestControllerAdvice
    public static class ApiExceptionHandler {

        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(
                ResponseStatusException ex) {

            HttpStatus status = HttpStatus.valueOf(
                    ex.getStatusCode().value()
            );

            return ResponseEntity
                    .status(status)
                    .body(
                            ApiResponse.error(
                                    ex.getReason() == null
                                            ? status.getReasonPhrase()
                                            : ex.getReason()
                            )
                    );
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
                MethodArgumentNotValidException ex) {

            String message = "Invalid request";
            if (ex.getBindingResult().hasFieldErrors()) {
                message = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
                if (!StringUtils.hasText(message)) {
                    message = ex.getBindingResult().getFieldErrors().get(0).getField() + " is invalid";
                }
            }

            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error(message));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
                HttpMessageNotReadableException ex) {

            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error("Invalid request body"));
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
                ConstraintViolationException ex) {

            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error(
                            ex.getConstraintViolations().iterator().next().getMessage()
                    ));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleGenericException(
                Exception ex) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Something went wrong"));
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {

        @NotBlank
        private String name;

        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Size(min = 6)
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {

        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponseDto {

        private String token;
        private String tokenType;
        private String userId;
        private String name;
        private String email;
        private String role;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserResponseDto {

        private String userId;
        private String name;
        private String email;
        private String role;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileRequestDto {

        @Min(1)
        private int age;

        @Min(30)
        private double heightCm;

        @Min(10)
        private double weightKg;

        private String fitnessGoal;

        private String bloodType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileResponseDto {

        private String userId;
        private String name;
        private String email;

        private int age;
        private double heightCm;
        private double weightKg;
        private double bmi;

        private String fitnessGoal;
        private String bloodType;

        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponseDto {

        private ProfileResponseDto profile;

        private BodyAssessmentEntity bodyWellness;

        private LatestLabReportSummaryDto latestLabReport;

        private LifestyleEntity lifestyle;

        private List<RecommendationEntity> recommendations;

        private ProgressSummaryDto progress;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class LatestLabReportSummaryDto {

            private String reportId;
            private String reportName;

            private Instant reportDate;
            private Instant uploadedAt;

            private String status;

            private long analyzedCount;
            private long abnormalCount;
            private long normalCount;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ProgressSummaryDto {

            private long totalBodyAssessments;
            private long totalLabReports;
            private long totalLifestyleRecords;

            private Instant lastActivityDate;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(collection = "users")
    public static class UserEntity {

        @Id
        private String id;

        @Indexed(unique = true)
        private String userId;

        private String name;

        @Indexed(unique = true)
        private String email;

        private String passwordHash;

        private String role;

        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(collection = "profiles")
    public static class ProfileEntity {

        @Id
        private String id;

        @Indexed(unique = true)
        private String userId;

        private int age;

        private double heightCm;

        private double weightKg;

        private double bmi;

        private String fitnessGoal;

        private String bloodType;

        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(collection = "body_assessments")
    public static class BodyAssessmentEntity {

        @Id
        private String id;

        private String userId;

        private String gas;
        private String acidity;
        private String bloating;
        private String constipation;
        private String fatigue;
        private String jointDiscomfort;

        private String energyLevel;
        private String digestion;
        private String hydration;
        private String sleepQuality;
        private String stressLevel;

        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(collection = "lifestyle_data")
    public static class LifestyleEntity {

        @Id
        private String id;

        private String userId;

        private double waterIntakeLiters;

        private double sleepDurationHours;

        private String physicalActivity;

        private String dietInformation;

        private String stressLevel;

        private String additionalNotes;

        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(collection = "lab_reports")
    public static class LabReportEntity {

        @Id
        private String reportId;

        private String userId;

        private String reportName;

        private String fileType;

        private long fileSize;

        private String fileUrl;

        private String status;

        private Instant reportDate;

        private Instant uploadedAt;

        @JsonIgnore
        private byte[] fileContent;

        private boolean fileAvailable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(collection = "lab_results")
    public static class LabResultEntity {

        @Id
        private String id;

        private String reportId;

        private String userId;

        private String parameter;

        private double value;

        private String unit;

        private String referenceRange;

        private String status;

        private String description;

        private String interpretation;

        private List<String> wellnessTips;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrServiceResponse {

        private boolean success;
        private String message;
        private OcrServiceData data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrServiceData {

        private String reportName;
        private List<OcrParameterDto> parameters;
        private Map<String, Object> aiSummary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrParameterDto {

        private String parameter;
        private Double value;
        private String unit;
        private String referenceRange;
        private String status;
        private String description;
        private String interpretation;
        private List<String> wellnessTips;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabReportUploadResponse {

        private LabReportEntity report;
        private List<LabResultEntity> parameters;
        private Map<String, Object> aiSummary;

        public LabReportUploadResponse(LabReportEntity report, List<LabResultEntity> parameters) {
            this.report = report;
            this.parameters = parameters;
            this.aiSummary = Collections.emptyMap();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(collection = "recommendations")
    public static class RecommendationEntity {

        @Id
        private String id;

        private String userId;

        private String category;

        private String title;

        private String recommendationText;

        private String priority;

        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(collection = "progress_logs")
    public static class ProgressLogEntity {

        @Id
        private String id;

        private String userId;

        private String logType;

        private String summary;

        private int score;

        private Instant loggedAt;
    }

    @Repository
    public interface UserMongoRepo
            extends MongoRepository<UserEntity, String> {

        boolean existsByEmail(String email);

        Optional<UserEntity> findByEmail(String email);

        Optional<UserEntity> findByUserId(String userId);
    }

    @Repository
    public interface ProfileMongoRepo
            extends MongoRepository<ProfileEntity, String> {

        Optional<ProfileEntity> findByUserId(String userId);
    }

    @Repository
    public interface BodyAssessmentMongoRepo
            extends MongoRepository<BodyAssessmentEntity, String> {

        List<BodyAssessmentEntity>
        findByUserIdOrderByCreatedAtDesc(String userId);

        Optional<BodyAssessmentEntity>
        findFirstByUserIdOrderByCreatedAtDesc(String userId);

        long countByUserId(String userId);
    }

    @Repository
    public interface LifestyleMongoRepo
            extends MongoRepository<LifestyleEntity, String> {

        List<LifestyleEntity>
        findByUserIdOrderByCreatedAtDesc(String userId);

        Optional<LifestyleEntity>
        findFirstByUserIdOrderByCreatedAtDesc(String userId);

        long countByUserId(String userId);
    }

    @Repository
    public interface LabReportMongoRepo
            extends MongoRepository<LabReportEntity, String> {

        List<LabReportEntity>
        findByUserIdOrderByUploadedAtDesc(String userId);

        Optional<LabReportEntity>
        findFirstByUserIdOrderByUploadedAtDesc(String userId);

        long countByUserId(String userId);
    }

    @Repository
    public interface LabResultMongoRepo
            extends MongoRepository<LabResultEntity, String> {

        List<LabResultEntity>
        findByUserId(String userId);

        List<LabResultEntity>
        findByReportId(String reportId);

        List<LabResultEntity>
        findByReportIdAndUserId(
                String reportId,
                String userId);
    }

    @Repository
    public interface RecommendationMongoRepo
            extends MongoRepository<RecommendationEntity, String> {

        List<RecommendationEntity>
        findByUserIdOrderByCreatedAtDesc(String userId);

        List<RecommendationEntity>
        findByUserIdAndCategory(
                String userId,
                String category);
    }

    @Repository
    public interface ProgressLogMongoRepo
            extends MongoRepository<ProgressLogEntity, String> {

        List<ProgressLogEntity>
        findByUserIdOrderByLoggedAtDesc(String userId);

        Optional<ProgressLogEntity>
        findFirstByUserIdOrderByLoggedAtDesc(String userId);
    }

    @Component
    public static class JwtTokenHelper {

        @Value("${jwt.secret}")
        private String secret;

        @Value("${jwt.expiration-ms:86400000}")
        private long expirationMs;

        private Key getSigningKey() {

            return Keys.hmacShaKeyFor(
                    secret.getBytes()
            );
        }

        public String generateToken(
                String userId,
                String email,
                String role) {

            Date now = new Date();

            return Jwts.builder()

                    .setSubject(userId)

                    .claim("email", email)

                    .claim("role", role)

                    .setIssuedAt(now)

                    .setExpiration(
                            new Date(
                                    now.getTime()
                                            + expirationMs
                            )
                    )

                    .signWith(
                            getSigningKey(),
                            SignatureAlgorithm.HS256
                    )

                    .compact();
        }

        public String getUserIdFromToken(
                String token) {

            return Jwts.parserBuilder()

                    .setSigningKey(
                            getSigningKey()
                    )

                    .build()

                    .parseClaimsJws(token)

                    .getBody()

                    .getSubject();
        }

        public boolean validateToken(
                String token) {

            try {

                Jwts.parserBuilder()

                        .setSigningKey(
                                getSigningKey()
                        )

                        .build()

                        .parseClaimsJws(token);

                return true;

            } catch (Exception e) {

                return false;
            }
        }
    }

    @Component
    public static class JwtFilter
            extends OncePerRequestFilter {

        @Autowired
        private JwtTokenHelper tokenHelper;

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain)
                throws ServletException, IOException {

            String header =
                    request.getHeader("Authorization");

            if (StringUtils.hasText(header)
                    && header.regionMatches(
                            true,
                            0,
                            "Bearer ",
                            0,
                            7
                    )) {

                String token =
                        header.substring(7);

                if (tokenHelper.validateToken(token)) {

                    String userId =
                            tokenHelper.getUserIdFromToken(token);

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    Collections.emptyList()
                            );

                    auth.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(auth);
                }
            }

            filterChain.doFilter(
                    request,
                    response
            );
        }
    }

    @Configuration
    @EnableWebSecurity
    public static class SecurityConfiguration {

        @Autowired
        private JwtFilter jwtFilter;

        @Autowired
        private ObjectMapper objectMapper;

        @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:5500,http://127.0.0.1:3000,http://127.0.0.1:5173,http://127.0.0.1:5500}")
        private String[] allowedOrigins;

        @Bean
        public PasswordEncoder passwordEncoder() {

            return new BCryptPasswordEncoder();
        }

        @Bean
        public RestTemplate restTemplate(RestTemplateBuilder builder) {

            return builder
                    .setConnectTimeout(Duration.ofSeconds(5))
                    .setReadTimeout(Duration.ofMinutes(2))
                    .build();
        }

        @Bean
        public CorsConfigurationSource corsSource() {

            CorsConfiguration config =
                    new CorsConfiguration();

            config.setAllowedOriginPatterns(
                    List.of("*")
            );
            config.setAllowedMethods(
                    List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            );
            config.setAllowedHeaders(
                    List.of("*")
            );
            config.setExposedHeaders(
                    List.of("*")
            );
            config.setAllowCredentials(true);

            UrlBasedCorsConfigurationSource source =
                    new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration(
                    "/**",
                    config
            );
            return source;
        }

        @Bean
        public SecurityFilterChain filterChain(
                HttpSecurity http)
                throws Exception {

            http

                    .csrf(csrf ->
                            csrf.disable())

                    .cors(cors ->
                            cors.configurationSource(
                                    corsSource()
                            ))

                    .sessionManagement(session ->
                            session.sessionCreationPolicy(
                                    SessionCreationPolicy.STATELESS
                            ))

                    .authorizeHttpRequests(auth ->
                            auth
                                    .requestMatchers(
                                            "/**",
                                            "/api/**"
                                    )
                                    .permitAll()
                                    .anyRequest()
                                    .permitAll()
                    )
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((request, response, authException) -> {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response.setCharacterEncoding("UTF-8");
                                response.getWriter().write(
                                        objectMapper.writeValueAsString(
                                                ApiResponse.error("Authentication required")
                                        )
                                );
                            })
                            .accessDeniedHandler((request, response, accessDeniedException) -> {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response.setCharacterEncoding("UTF-8");
                                response.getWriter().write(
                                        objectMapper.writeValueAsString(
                                                ApiResponse.error("Access denied")
                                        )
                                );
                            })
                    )
                    .addFilterBefore(
                            jwtFilter,
                            UsernamePasswordAuthenticationFilter.class
                    );

            return http.build();
        }
    }

    @RestController
    @RequestMapping("/api/auth")
    public static class AuthRestController {

        @Autowired
        private UserMongoRepo userRepo;

        @Autowired
        private JwtTokenHelper jwtHelper;

        @Autowired
        private PasswordEncoder encoder;

        @Nullable
        private String normalizeEmail(@Nullable String email) {

            if (email == null) {
                return null;
            }

            return email.trim().toLowerCase(Locale.ROOT);
        }

        @PostMapping("/register")
        public ResponseEntity<ApiResponse<UserResponseDto>>
        register(
                @Valid
                @NonNull
                @RequestBody
                RegisterRequest req) {

            String normalizedEmail =
                    normalizeEmail(req.getEmail());

            req.setEmail(normalizedEmail);

            if (userRepo.existsByEmail(normalizedEmail)) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(
                                ApiResponse.error(
                                        "Email already registered"
                                )
                        );
            }

            String userId;

            do {
                userId =
                        "MW-"
                                + (10000
                                + new Random()
                                .nextInt(90000));
            } while (
                    userRepo
                            .findByUserId(userId)
                            .isPresent()
            );

            UserEntity user =
                    UserEntity.builder()
                            .userId(userId)
                            .name(req.getName())
                            .email(req.getEmail())
                            .passwordHash(
                                    encoder.encode(
                                            req.getPassword()
                                    )
                            )
                            .role("PATIENT")
                            .createdAt(Instant.now())
                            .build();

            userRepo.save(user);

            UserResponseDto response =
                    UserResponseDto.builder()
                            .userId(user.getUserId())
                            .name(user.getName())
                            .email(user.getEmail())
                            .role(user.getRole())
                            .createdAt(user.getCreatedAt())
                            .build();

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(
                            ApiResponse.success(
                                    "User registered successfully",
                                    response
                            )
                    );
        }

        @PostMapping("/login")
        public ResponseEntity<ApiResponse<LoginResponseDto>>
        login(
                @Valid
                @NonNull
                @RequestBody
                LoginRequest req) {

            String normalizedEmail =
                    normalizeEmail(req.getEmail());

            req.setEmail(normalizedEmail);

            UserEntity user =
                    userRepo
                            .findByEmail(
                                    normalizedEmail
                            )
                            .orElse(null);

            if (user == null
                    || !encoder.matches(
                            req.getPassword(),
                            user.getPasswordHash()
                    )) {

                return ResponseEntity

                        .status(
                                HttpStatus.UNAUTHORIZED
                        )

                        .body(
                                ApiResponse.error(
                                        "Invalid credentials"
                                )
                        );
            }

            String token =
                    jwtHelper.generateToken(
                            user.getUserId(),
                            user.getEmail(),
                            user.getRole()
                    );

            LoginResponseDto response =
                    LoginResponseDto.builder()

                            .token(token)

                            .tokenType("Bearer")

                            .userId(user.getUserId())

                            .name(user.getName())

                            .email(user.getEmail())

                            .role(user.getRole())

                            .build();

            return ResponseEntity.ok(

                    ApiResponse.success(
                            "Login successful",
                            response
                    )
            );
        }
    }

    @RestController
    @RequestMapping("/api/profile")
    public static class ProfileRestController {

        @Autowired
        private UserMongoRepo userRepo;

        @Autowired
        private ProfileMongoRepo profileRepo;

        @GetMapping
        public ResponseEntity<ApiResponse<ProfileResponseDto>>
        getProfile(
                Authentication auth) {

            String userId =
                    requireUserId(auth);

            UserEntity user =
                    userRepo
                            .findByUserId(userId)
                            .orElse(null);

            ProfileEntity profile =
                    profileRepo
                            .findByUserId(userId)
                            .orElse(null);

            if (profile == null) {

                return ResponseEntity.ok(

                        ApiResponse.success(
                                "Profile not created yet",
                                null
                        )
                );
            }

            ProfileResponseDto response =
                    ProfileResponseDto.builder()

                            .userId(userId)

                            .name(
                                    user != null
                                            ? user.getName()
                                            : null
                            )

                            .email(
                                    user != null
                                            ? user.getEmail()
                                            : null
                            )

                            .age(profile.getAge())

                            .heightCm(
                                    profile.getHeightCm()
                            )

                            .weightKg(
                                    profile.getWeightKg()
                            )

                            .bmi(
                                    profile.getBmi()
                            )

                            .fitnessGoal(
                                    profile.getFitnessGoal()
                            )

                            .bloodType(
                                    profile.getBloodType()
                            )

                            .updatedAt(
                                    profile.getUpdatedAt()
                            )

                            .build();

            return ResponseEntity.ok(

                    ApiResponse.success(
                            "Profile retrieved",
                            response
                    )
            );
        }

        @PutMapping
        public ResponseEntity<ApiResponse<ProfileResponseDto>>
        updateProfile(
                Authentication auth,
                @Valid
                @RequestBody
                ProfileRequestDto req) {

            String userId =
                    requireUserId(auth);

            UserEntity user =
                    userRepo
                            .findByUserId(userId)
                            .orElse(null);

            double heightM =
                    req.getHeightCm()
                            / 100.0;

            double bmi =
                    Math.round(

                            (
                                    req.getWeightKg()
                                            / (heightM * heightM)
                            )
                                    * 10.0

                    ) / 10.0;

            ProfileEntity profile =
                    profileRepo
                            .findByUserId(userId)
                            .orElse(

                                    ProfileEntity
                                            .builder()
                                            .userId(userId)
                                            .build()
                            );

            profile.setAge(
                    req.getAge()
            );

            profile.setHeightCm(
                    req.getHeightCm()
            );

            profile.setWeightKg(
                    req.getWeightKg()
            );

            profile.setBmi(
                    bmi
            );

            profile.setFitnessGoal(
                    req.getFitnessGoal()
            );

            profile.setBloodType(
                    req.getBloodType()
            );

            profile.setUpdatedAt(
                    Instant.now()
            );

            profileRepo.save(profile);

            ProfileResponseDto response =
                    ProfileResponseDto.builder()

                            .userId(userId)

                            .name(
                                    user != null
                                            ? user.getName()
                                            : null
                            )

                            .email(
                                    user != null
                                            ? user.getEmail()
                                            : null
                            )

                            .age(profile.getAge())

                            .heightCm(
                                    profile.getHeightCm()
                            )

                            .weightKg(
                                    profile.getWeightKg()
                            )

                            .bmi(
                                    profile.getBmi()
                            )

                            .fitnessGoal(
                                    profile.getFitnessGoal()
                            )

                            .bloodType(
                                    profile.getBloodType()
                            )

                            .updatedAt(
                                    profile.getUpdatedAt()
                            )

                            .build();

            return ResponseEntity.ok(

                    ApiResponse.success(
                            "Profile updated",
                            response
                    )
            );
        }
    }

    @RestController
    @RequestMapping("/api/body-assessments")
    public static class BodyWellnessRestController {

        @Autowired
        private BodyAssessmentMongoRepo repo;
        @PostMapping
        public ResponseEntity<ApiResponse<BodyAssessmentEntity>>
        save(
                Authentication auth,
                @RequestBody
                BodyAssessmentEntity data) {

            String userId =
                    requireUserId(auth);

            data.setUserId(userId);

            data.setCreatedAt(
                    Instant.now()
            );

            return ResponseEntity

                    .status(
                            HttpStatus.CREATED
                    )

                    .body(
                            ApiResponse.success(
                                    "Assessment recorded",
                                    repo.save(data)
                            )
                    );
        }
        @GetMapping
        public ResponseEntity<ApiResponse<List<BodyAssessmentEntity>>>
        getHistory(
                Authentication auth) {

            String userId =
                    requireUserId(auth);

            return ResponseEntity.ok(

                    ApiResponse.success(

                            "History retrieved",

                            repo.findByUserIdOrderByCreatedAtDesc(
                                    userId
                            )
                    )
            );
        }
        @GetMapping("/latest")
        public ResponseEntity<ApiResponse<BodyAssessmentEntity>>
        getLatest(
                Authentication auth) {

            String userId =
                    requireUserId(auth);

            return ResponseEntity.ok(

                    ApiResponse.success(

                            "Latest retrieved",

                            repo
                                    .findFirstByUserIdOrderByCreatedAtDesc(
                                            userId
                                    )
                                    .orElse(null)
                    )
            );
        }
    }

    @RestController
    @RequestMapping("/api/lifestyle")
    public static class LifestyleRestController {

        @Autowired
        private LifestyleMongoRepo repo;
        @PostMapping
        public ResponseEntity<ApiResponse<LifestyleEntity>>
        save(
                Authentication auth,
                @RequestBody
                LifestyleEntity data) {

            String userId =
                    requireUserId(auth);

            data.setUserId(userId);

            data.setCreatedAt(
                    Instant.now()
            );

            return ResponseEntity

                    .status(
                            HttpStatus.CREATED
                    )

                    .body(
                            ApiResponse.success(
                                    "Lifestyle saved",
                                    repo.save(data)
                            )
                    );
        }
        @GetMapping
        public ResponseEntity<ApiResponse<List<LifestyleEntity>>>
        getHistory(
                Authentication auth) {

            String userId =
                    requireUserId(auth);

            return ResponseEntity.ok(

                    ApiResponse.success(

                            "Lifestyle history retrieved",

                            repo.findByUserIdOrderByCreatedAtDesc(
                                    userId
                            )
                    )
            );
        }
        @GetMapping("/latest")
        public ResponseEntity<ApiResponse<LifestyleEntity>>
        getLatest(
                Authentication auth) {

            String userId =
                    requireUserId(auth);

            return ResponseEntity.ok(

                    ApiResponse.success(

                            "Latest lifestyle retrieved",

                            repo
                                    .findFirstByUserIdOrderByCreatedAtDesc(
                                            userId
                                    )
                                    .orElse(null)
                    )
            );
        }
    }

    @RestController
    @RequestMapping("/api/reports")
    public static class LabReportRestController {

        @Autowired
        private LabReportMongoRepo reportRepo;

        @Autowired
        private LabResultMongoRepo resultRepo;

        @Autowired
        private RestTemplate restTemplate;

        @Value("${ocr.service.url:http://localhost:8000/api/ocr/parse}")
        private String ocrServiceUrl;

        @PostMapping(path = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<LabReportUploadResponse>> parseReport(
                Authentication auth,
                @RequestParam("file") MultipartFile file) throws IOException {

            String userId = requireUserId(auth);

            if (file == null || file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a non-empty report file");
            }

            if (file.getSize() > 10 * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Report file must be 10 MB or smaller");
            }

            String originalName = file.getOriginalFilename();
            String reportName = originalName == null ? "" : originalName.replace('\\', '/');
            reportName = reportName.substring(reportName.lastIndexOf('/') + 1);
            final String uploadedReportName = reportName;
            int extensionSeparator = reportName.lastIndexOf('.');
            String extension = extensionSeparator < 0
                    ? ""
                    : reportName.substring(extensionSeparator).toLowerCase(Locale.ROOT);

            if (!Set.of(".pdf", ".jpg", ".jpeg", ".png", ".webp").contains(extension)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported file type. Upload a PDF, JPG, JPEG, PNG, or WebP report"
                );
            }

            ByteArrayResource reportResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return uploadedReportName;
                }
            };
            MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("file", reportResource);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            List<OcrParameterDto> extractedParameters = Collections.emptyList();
            String ocrMessage = null;
            String resolvedReportName = reportName;
            Map<String, Object> aiSummary = null;

            try {
                ResponseEntity<OcrServiceResponse> response = restTemplate.exchange(
                        ocrServiceUrl,
                        HttpMethod.POST,
                        new HttpEntity<>(requestBody, headers),
                        OcrServiceResponse.class
                );
                OcrServiceResponse ocrResponse = response.getBody();
                if (ocrResponse != null && ocrResponse.isSuccess() && ocrResponse.getData() != null) {
                    if (ocrResponse.getData().getParameters() != null && !ocrResponse.getData().getParameters().isEmpty()) {
                        extractedParameters = ocrResponse.getData().getParameters();
                    }
                    if (StringUtils.hasText(ocrResponse.getData().getReportName())) {
                        resolvedReportName = ocrResponse.getData().getReportName();
                    }
                    if (StringUtils.hasText(ocrResponse.getMessage())) {
                        ocrMessage = ocrResponse.getMessage();
                    }
                    aiSummary = ocrResponse.getData().getAiSummary();
                } else {
                    String failureMsg = (ocrResponse != null && StringUtils.hasText(ocrResponse.getMessage()))
                            ? ocrResponse.getMessage()
                            : "OCR could not recognize any valid clinical laboratory parameters from this document.";
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, failureMsg);
                }
            } catch (ResponseStatusException rse) {
                throw rse;
            } catch (ResourceAccessException rae) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Cannot reach Python OCR service at " + ocrServiceUrl + ". Ensure the Python OCR service is running on port 8000."
                );
            } catch (Exception ex) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Error communicating with OCR service: " + ex.getMessage()
                );
            }

            if (extractedParameters.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No laboratory parameters could be recognized from the uploaded document."
                );
            }

            Instant uploadedAt = Instant.now();
            LabReportEntity report = LabReportEntity.builder()
                    .reportId(UUID.randomUUID().toString())
                    .userId(userId)
                    .reportName(resolvedReportName)
                    .fileType(file.getContentType())
                    .fileSize(file.getSize())
                    .status("ANALYZED")
                    .reportDate(uploadedAt)
                    .uploadedAt(uploadedAt)
                    .fileContent(file.getBytes())
                    .fileAvailable(true)
                    .build();

            reportRepo.save(report);
            List<LabResultEntity> results = extractedParameters.stream()
                    .filter(parameter -> parameter != null && StringUtils.hasText(parameter.getParameter()))
                    .map(parameter -> LabResultEntity.builder()
                            .reportId(report.getReportId())
                            .userId(userId)
                            .parameter(parameter.getParameter())
                            .value(parameter.getValue() != null ? parameter.getValue() : 0.0)
                            .unit(parameter.getUnit())
                            .referenceRange(parameter.getReferenceRange())
                            .status(parameter.getStatus() != null ? parameter.getStatus() : "NORMAL")
                            .description(parameter.getDescription())
                            .interpretation(parameter.getInterpretation())
                            .wellnessTips(parameter.getWellnessTips())
                            .build())
                    .toList();
            List<LabResultEntity> savedResults = resultRepo.saveAll(results);

            String message = StringUtils.hasText(ocrMessage)
                    ? ocrMessage
                    : "OCR completed successfully";
            return ResponseEntity.ok(ApiResponse.success(
                    message,
                    new LabReportUploadResponse(report, savedResults, aiSummary)
            ));
        }

        @PostMapping
        public ResponseEntity<ApiResponse<LabReportEntity>>
        uploadReport(
                Authentication auth,
                @RequestBody
                LabReportEntity report) {

            String userId =
                    requireUserId(auth);

            report.setUserId(userId);

            report.setStatus("UPLOADED");

            report.setUploadedAt(
                    Instant.now()
            );

            return ResponseEntity

                    .status(
                            HttpStatus.CREATED
                    )

                    .body(
                            ApiResponse.success(
                                    "Report metadata saved",
                                    reportRepo.save(report)
                            )
                    );
        }

        @GetMapping
        public ResponseEntity<ApiResponse<List<LabReportEntity>>>
        getReports(
                Authentication auth) {

            String userId =
                    requireUserId(auth);

            return ResponseEntity.ok(

                    ApiResponse.success(

                            "Reports retrieved",

                            reportRepo
                                    .findByUserIdOrderByUploadedAtDesc(
                                            userId
                                    )
                    )
            );
        }

        @GetMapping("/history")
        public ResponseEntity<ApiResponse<List<LabReportUploadResponse>>>
        getReportHistory(Authentication auth) {

            String userId = requireUserId(auth);
            List<LabReportUploadResponse> history = reportRepo
                    .findByUserIdOrderByUploadedAtDesc(userId)
                    .stream()
                    .map(report -> new LabReportUploadResponse(
                            report,
                            resultRepo.findByReportIdAndUserId(report.getReportId(), userId)
                    ))
                    .toList();

            return ResponseEntity.ok(ApiResponse.success("Report history retrieved", history));
        }

        @GetMapping("/{reportId}")
        public ResponseEntity<ApiResponse<LabReportEntity>>
        getReport(
                Authentication auth,
                @PathVariable
                String reportId) {

            String userId =
                    requireUserId(auth);

            LabReportEntity report =
                    reportRepo
                            .findById(reportId)
                            .orElse(null);

            if (report == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
            }

            if (!userId.equals(report.getUserId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not authorized to access this report"
                );
            }

            return ResponseEntity.ok(ApiResponse.success("Report retrieved", report));
        }

        @GetMapping("/{reportId}/results")
        public ResponseEntity<ApiResponse<List<LabResultEntity>>> getReportResults(
                Authentication auth,
                @PathVariable String reportId) {

            String userId = requireUserId(auth);
            LabReportEntity report = reportRepo.findById(reportId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Report not found"
                    ));
            if (!userId.equals(report.getUserId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not authorized to access this report"
                );
            }

            return ResponseEntity.ok(ApiResponse.success(
                    "Laboratory results retrieved",
                    resultRepo.findByReportIdAndUserId(reportId, userId)
            ));
        }

        @GetMapping("/{reportId}/file")
        public ResponseEntity<byte[]> getReportFile(
                Authentication auth,
                @PathVariable String reportId) {

            String userId = requireUserId(auth);
            LabReportEntity report = reportRepo.findById(reportId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Report not found"
                    ));
            if (!userId.equals(report.getUserId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not authorized to access this report"
                );
            }
            if (report.getFileContent() == null || report.getFileContent().length == 0) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "The original report file is not available"
                );
            }

            MediaType contentType;
            try {
                contentType = MediaType.parseMediaType(report.getFileType());
            } catch (IllegalArgumentException | NullPointerException ex) {
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentLength(report.getFileContent().length);
            headers.setContentDisposition(
                    org.springframework.http.ContentDisposition.inline()
                            .filename(report.getReportName())
                            .build()
            );
            return new ResponseEntity<>(report.getFileContent(), headers, HttpStatus.OK);
        }

        @DeleteMapping("/{reportId}")
        public ResponseEntity<ApiResponse<Void>>
        deleteReport(
                Authentication auth,
                @PathVariable
                String reportId) {

            String userId =
                    requireUserId(auth);

            LabReportEntity report =
                    reportRepo
                            .findById(reportId)
                            .orElse(null);

            if (report == null) {

                return ResponseEntity

                        .status(
                                HttpStatus.NOT_FOUND
                        )

                        .body(
                                ApiResponse.error(
                                        "Report not found"
                                )
                        );
            }

            if (!userId.equals(
                    report.getUserId())) {

                return ResponseEntity

                        .status(
                                HttpStatus.FORBIDDEN
                        )

                        .body(
                                ApiResponse.error(
                                        "You are not authorized to delete this report"
                                )
                        );
            }

            reportRepo.deleteById(
                    reportId
            );

            List<LabResultEntity> results =
                    resultRepo.findByReportIdAndUserId(
                            reportId,
                            userId
                    );

            if (!results.isEmpty()) {

                resultRepo.deleteAll(
                        results
                );
            }

            return ResponseEntity.ok(

                    ApiResponse.success(
                            "Report deleted",
                            null
                    )
            );
        }

        @PostMapping("/{reportId}/results")
        public ResponseEntity<ApiResponse<List<LabResultEntity>>>
        saveResults(
                Authentication auth,
                @PathVariable
                String reportId,

                @RequestBody
                List<LabResultEntity> results) {

            String userId =
                    requireUserId(auth);

            LabReportEntity report =
                    reportRepo
                            .findById(reportId)
                            .orElse(null);

            if (report == null) {

                return ResponseEntity

                        .status(
                                HttpStatus.NOT_FOUND
                        )

                        .body(
                                ApiResponse.error(
                                        "Report not found"
                                )
                        );
            }

            if (!userId.equals(
                    report.getUserId())) {

                return ResponseEntity

                        .status(
                                HttpStatus.FORBIDDEN
                        )

                        .body(
                                ApiResponse.error(
                                        "You are not authorized to modify this report"
                                )
                        );
            }

            results.forEach(result -> {

                result.setReportId(
                        reportId
                );

                result.setUserId(
                        userId
                );
            });

            return ResponseEntity

                    .status(
                            HttpStatus.CREATED
                    )

                    .body(
                            ApiResponse.success(
                                    "Lab results stored",
                                    resultRepo.saveAll(
                                            results
                                    )
                            )
                    );
        }
    }

    @RestController
    @RequestMapping("/api/recommendations")
    public static class RecommendationRestController {

        @Autowired
        private RecommendationMongoRepo repo;
        @GetMapping
        public ResponseEntity<ApiResponse<List<RecommendationEntity>>>
        getRecommendations(
                Authentication auth,

                @RequestParam(
                        required = false
                )
                String category) {

            String userId =
                    requireUserId(auth);

            List<RecommendationEntity> list;

            if (category != null
                    && !category.isBlank()) {

                list =
                        repo.findByUserIdAndCategory(
                                userId,
                                category
                        );

            } else {

                list =
                        repo.findByUserIdOrderByCreatedAtDesc(
                                userId
                        );
            }

            return ResponseEntity.ok(

                    ApiResponse.success(
                            "Recommendations retrieved",
                            list
                    )
            );
        }
        @PostMapping
        public ResponseEntity<ApiResponse<RecommendationEntity>>
        createRecommendation(
                Authentication auth,

                @RequestBody
                RecommendationEntity rec) {

            String userId =
                    requireUserId(auth);

            rec.setUserId(userId);

            rec.setCreatedAt(
                    Instant.now()
            );

            return ResponseEntity

                    .status(
                            HttpStatus.CREATED
                    )

                    .body(
                            ApiResponse.success(
                                    "Recommendation saved",
                                    repo.save(rec)
                            )
                    );
        }
    }

    @RestController
    @RequestMapping("/api/progress")
    public static class ProgressRestController {

        @Autowired
        private BodyAssessmentMongoRepo bodyRepo;

        @Autowired
        private LabReportMongoRepo labRepo;

        @Autowired
        private LifestyleMongoRepo lifestyleRepo;

        @Autowired
        private ProgressLogMongoRepo progressLogRepo;

        @GetMapping
        public ResponseEntity<ApiResponse<Map<String, Object>>>
        getProgress(
                Authentication auth,
                @RequestParam(defaultValue = "all") String range) {

            String userId =
                    requireUserId(auth);

            Instant now = Instant.now();
            Instant from = switch (range.toLowerCase(Locale.ROOT)) {
                case "7d" -> now.minus(Duration.ofDays(7));
                case "30d" -> now.minus(Duration.ofDays(30));
                case "90d" -> now.minus(Duration.ofDays(90));
                case "all" -> Instant.MIN;
                default -> throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Range must be 7d, 30d, 90d, or all"
                );
            };

            List<BodyAssessmentEntity> bodyAssessments = bodyRepo
                    .findByUserIdOrderByCreatedAtDesc(userId)
                    .stream()
                    .filter(item -> isInRange(item.getCreatedAt(), from, now))
                    .toList();
            List<LabReportEntity> reports = labRepo
                    .findByUserIdOrderByUploadedAtDesc(userId)
                    .stream()
                    .filter(item -> isInRange(item.getUploadedAt(), from, now))
                    .toList();
            List<LifestyleEntity> lifestyleRecords = lifestyleRepo
                    .findByUserIdOrderByCreatedAtDesc(userId)
                    .stream()
                    .filter(item -> isInRange(item.getCreatedAt(), from, now))
                    .toList();
            List<RecommendationEntity> recommendations = recommendationRepo
                    .findByUserIdOrderByCreatedAtDesc(userId)
                    .stream()
                    .filter(item -> isInRange(item.getCreatedAt(), from, now))
                    .toList();
            List<ProgressLogEntity> progressLogs = progressLogRepo
                    .findByUserIdOrderByLoggedAtDesc(userId)
                    .stream()
                    .filter(item -> isInRange(item.getLoggedAt(), from, now))
                    .toList();

            List<LabResultEntity> labResults = labResultRepo.findByUserId(userId);
            Map<String, LabReportEntity> reportsById = new HashMap<>();
            reports.forEach(report -> reportsById.put(report.getReportId(), report));

            List<Map<String, Object>> hydrationHistory = new ArrayList<>();
            List<Map<String, Object>> sleepHistory = new ArrayList<>();
            List<Map<String, Object>> energyHistory = new ArrayList<>();
            List<Map<String, Object>> activityHistory = new ArrayList<>();
            List<Map<String, Object>> symptomHistory = new ArrayList<>();
            List<Map<String, Object>> timeline = new ArrayList<>();

            for (LifestyleEntity lifestyle : lifestyleRecords) {
                Instant date = lifestyle.getCreatedAt();
                if (lifestyle.getWaterIntakeLiters() > 0) {
                    hydrationHistory.add(progressPoint(date, lifestyle.getWaterIntakeLiters(), "L"));
                }
                if (lifestyle.getSleepDurationHours() > 0) {
                    sleepHistory.add(progressPoint(date, lifestyle.getSleepDurationHours(), "hours"));
                }
                if (StringUtils.hasText(lifestyle.getPhysicalActivity())) {
                    activityHistory.add(progressPoint(date, lifestyle.getPhysicalActivity(), null));
                }
                timeline.add(timelineItem(
                        date,
                        "lifestyle",
                        "Lifestyle record saved",
                        lifestyleDescription(lifestyle)
                ));
            }

            for (BodyAssessmentEntity assessment : bodyAssessments) {
                Double energy = parseNumericValue(assessment.getEnergyLevel());
                if (energy != null) {
                    energyHistory.add(progressPoint(assessment.getCreatedAt(), energy, "/10"));
                }

                Map<String, Object> symptoms = new LinkedHashMap<>();
                symptoms.put("date", assessment.getCreatedAt());
                symptoms.put("acidity", assessment.getAcidity());
                symptoms.put("gas", assessment.getGas());
                symptoms.put("bloating", assessment.getBloating());
                symptoms.put("constipation", assessment.getConstipation());
                symptoms.put("fatigue", assessment.getFatigue());
                symptoms.put("jointDiscomfort", assessment.getJointDiscomfort());
                symptoms.put("energyLevel", energy);
                symptoms.put("digestion", assessment.getDigestion());
                symptoms.put("hydration", assessment.getHydration());
                symptoms.put("sleepQuality", assessment.getSleepQuality());
                symptoms.put("stressLevel", assessment.getStressLevel());
                symptomHistory.add(symptoms);
                timeline.add(timelineItem(
                        assessment.getCreatedAt(),
                        "assessment",
                        "Body assessment completed",
                        "Body wellness assessment recorded"
                ));
            }

            List<Map<String, Object>> labParameterHistory = new ArrayList<>();
            for (LabReportEntity report : reports) {
                List<Map<String, Object>> parameters = labResults.stream()
                        .filter(result -> report.getReportId().equals(result.getReportId()))
                        .map(this::labParameter)
                        .toList();
                Map<String, Object> reportHistory = new LinkedHashMap<>();
                reportHistory.put("reportId", report.getReportId());
                reportHistory.put("reportName", report.getReportName());
                reportHistory.put("date", report.getReportDate() != null
                        ? report.getReportDate()
                        : report.getUploadedAt());
                reportHistory.put("status", report.getStatus());
                reportHistory.put("fileAvailable",
                        report.getFileContent() != null && report.getFileContent().length > 0);
                reportHistory.put("parameters", parameters);
                labParameterHistory.add(reportHistory);
                timeline.add(timelineItem(
                        report.getUploadedAt(),
                        "lab-report",
                        "Laboratory report uploaded",
                        report.getReportName()
                ));
                if ("ANALYZED".equalsIgnoreCase(report.getStatus())) {
                    timeline.add(timelineItem(
                            report.getUploadedAt(),
                            "ocr",
                            "OCR processing completed",
                            parameters.size() + " recognized laboratory parameters"
                    ));
                }
            }

            for (RecommendationEntity recommendation : recommendations) {
                timeline.add(timelineItem(
                        recommendation.getCreatedAt(),
                        "recommendation",
                        "Wellness recommendation generated",
                        StringUtils.hasText(recommendation.getTitle())
                                ? recommendation.getTitle()
                                : recommendation.getRecommendationText()
                ));
            }
            for (ProgressLogEntity progressLog : progressLogs) {
                timeline.add(timelineItem(
                        progressLog.getLoggedAt(),
                        "progress",
                        StringUtils.hasText(progressLog.getLogType())
                                ? progressLog.getLogType()
                                : "Wellness activity",
                        progressLog.getSummary()
                ));
            }

            hydrationHistory.sort(Comparator.comparing(item -> (Instant) item.get("date")));
            sleepHistory.sort(Comparator.comparing(item -> (Instant) item.get("date")));
            energyHistory.sort(Comparator.comparing(item -> (Instant) item.get("date")));
            activityHistory.sort(Comparator.comparing(item -> (Instant) item.get("date")));
            symptomHistory.sort(Comparator.comparing(item -> (Instant) item.get("date")));
            labParameterHistory.sort(Comparator.comparing(item -> (Instant) item.get("date")));
            timeline.sort(Comparator.comparing(
                    item -> (Instant) item.get("date"),
                    Comparator.nullsLast(Comparator.naturalOrder())
            ));

            Map<String, Object> overview =
                    new LinkedHashMap<>();
            overview.put("totalBodyAssessments", bodyAssessments.size());
            overview.put("totalLabReportsAnalyzed", reports.stream()
                    .filter(report -> "ANALYZED".equalsIgnoreCase(report.getStatus()))
                    .count());
            overview.put("totalLifestyleRecords", lifestyleRecords.size());
            overview.put("averageDailyWaterIntakeLiters", average(
                    lifestyleRecords.stream()
                            .map(LifestyleEntity::getWaterIntakeLiters)
                            .filter(value -> value > 0)
                            .toList()
            ));
            overview.put("averageSleepDurationHours", average(
                    lifestyleRecords.stream()
                            .map(LifestyleEntity::getSleepDurationHours)
                            .filter(value -> value > 0)
                            .toList()
            ));
            overview.put("averageEnergyLevel", average(
                    bodyAssessments.stream()
                            .map(item -> parseNumericValue(item.getEnergyLevel()))
                            .filter(Objects::nonNull)
                            .toList()
            ));

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("overview", overview);
            map.put("hydrationHistory", hydrationHistory);
            map.put("sleepHistory", sleepHistory);
            map.put("energyHistory", energyHistory);
            map.put("activityHistory", activityHistory);
            map.put("symptomHistory", symptomHistory);
            map.put("labParameterHistory", labParameterHistory);
            map.put("wellnessTimeline", timeline);
            map.put("bodyAssessments", bodyAssessments);
            map.put("labReports", reports);
            map.put("lifestyle", lifestyleRecords);
            map.put("progressLogs", progressLogs);

            return ResponseEntity.ok(

                    ApiResponse.success(
                            "Progress history retrieved",
                            map
                    )
            );
        }

        @Autowired
        private LabResultMongoRepo labResultRepo;

        @Autowired
        private RecommendationMongoRepo recommendationRepo;

        private static boolean isInRange(Instant date, Instant from, Instant to) {
            return date != null && !date.isBefore(from) && !date.isAfter(to);
        }

        private static Double parseNumericValue(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static Map<String, Object> progressPoint(Instant date, Object value, String unit) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date);
            point.put("value", value);
            if (unit != null) {
                point.put("unit", unit);
            }
            return point;
        }

        private static Map<String, Object> timelineItem(
                Instant date,
                String type,
                String title,
                String description) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("type", type);
            item.put("title", title);
            item.put("description", description);
            return item;
        }

        private static String lifestyleDescription(LifestyleEntity lifestyle) {
            List<String> details = new ArrayList<>();
            if (lifestyle.getWaterIntakeLiters() > 0) {
                details.add(lifestyle.getWaterIntakeLiters() + " L water");
            }
            if (lifestyle.getSleepDurationHours() > 0) {
                details.add(lifestyle.getSleepDurationHours() + " hours sleep");
            }
            if (StringUtils.hasText(lifestyle.getPhysicalActivity())) {
                details.add(lifestyle.getPhysicalActivity());
            }
            return details.isEmpty() ? "Lifestyle record saved" : String.join(", ", details);
        }

        private static Double average(List<Double> values) {
            return values.isEmpty()
                    ? null
                    : values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        }

        private Map<String, Object> labParameter(LabResultEntity result) {
            Map<String, Object> parameter = new LinkedHashMap<>();
            parameter.put("parameter", result.getParameter());
            parameter.put("value", result.getValue());
            parameter.put("unit", result.getUnit());
            parameter.put("referenceRange", result.getReferenceRange());
            parameter.put("status", result.getStatus());
            return parameter;
        }
    }

    @Service
    public static class DashboardService {

        @Autowired
        private UserMongoRepo userRepo;

        @Autowired
        private ProfileMongoRepo profileRepo;

        @Autowired
        private BodyAssessmentMongoRepo bodyAssessmentRepo;

        @Autowired
        private LifestyleMongoRepo lifestyleRepo;

        @Autowired
        private LabReportMongoRepo labReportRepo;

        @Autowired
        private LabResultMongoRepo labResultRepo;

        @Autowired
        private RecommendationMongoRepo recommendationRepo;

        @Autowired
        private ProgressLogMongoRepo progressLogRepo;

        public DashboardResponseDto
        getDashboard(
                String userId) {

            UserEntity user =
                    userRepo
                            .findByUserId(userId)
                            .orElse(null);

            ProfileEntity profile =
                    profileRepo
                            .findByUserId(userId)
                            .orElse(null);

            ProfileResponseDto profileDto =
                    null;

            if (profile != null) {

                profileDto =
                        ProfileResponseDto.builder()

                                .userId(userId)

                                .name(
                                        user != null
                                                ? user.getName()
                                                : null
                                )

                                .email(
                                        user != null
                                                ? user.getEmail()
                                                : null
                                )

                                .age(
                                        profile.getAge()
                                )

                                .heightCm(
                                        profile.getHeightCm()
                                )

                                .weightKg(
                                        profile.getWeightKg()
                                )

                                .bmi(
                                        profile.getBmi()
                                )

                                .fitnessGoal(
                                        profile.getFitnessGoal()
                                )

                                .bloodType(
                                        profile.getBloodType()
                                )

                                .updatedAt(
                                        profile.getUpdatedAt()
                                )

                                .build();
            }

            BodyAssessmentEntity bodyWellness =
                    bodyAssessmentRepo

                            .findFirstByUserIdOrderByCreatedAtDesc(
                                    userId
                            )

                            .orElse(null);

            LabReportEntity latestReport =
                    labReportRepo

                            .findFirstByUserIdOrderByUploadedAtDesc(
                                    userId
                            )

                            .orElse(null);

            DashboardResponseDto
                    .LatestLabReportSummaryDto
                    latestReportDto = null;

            if (latestReport != null) {

                List<LabResultEntity> results =
                        labResultRepo
                                .findByReportIdAndUserId(
                                        latestReport.getReportId(),
                                        userId
                                );

                long analyzedCount =
                        results.size();

                long abnormalCount =
                        results

                                .stream()

                                .filter(result ->

                                        result.getStatus() != null

                                                && result
                                                .getStatus()
                                                .equalsIgnoreCase(
                                                        "ABNORMAL"
                                                )
                                )

                                .count();

                long normalCount =
                        results

                                .stream()

                                .filter(result ->

                                        result.getStatus() != null

                                                && result
                                                .getStatus()
                                                .equalsIgnoreCase(
                                                        "NORMAL"
                                                )
                                )

                                .count();

                latestReportDto =
                        DashboardResponseDto
                                .LatestLabReportSummaryDto
                                .builder()

                                .reportId(
                                        latestReport.getReportId()
                                )

                                .reportName(
                                        latestReport.getReportName()
                                )

                                .reportDate(
                                        latestReport.getReportDate()
                                )

                                .uploadedAt(
                                        latestReport.getUploadedAt()
                                )

                                .status(
                                        latestReport.getStatus()
                                )

                                .analyzedCount(
                                        analyzedCount
                                )

                                .abnormalCount(
                                        abnormalCount
                                )

                                .normalCount(
                                        normalCount
                                )

                                .build();
            }

            LifestyleEntity lifestyle =
                    lifestyleRepo

                            .findFirstByUserIdOrderByCreatedAtDesc(
                                    userId
                            )

                            .orElse(null);

            List<RecommendationEntity>
                    recommendations =
                    recommendationRepo

                            .findByUserIdOrderByCreatedAtDesc(
                                    userId
                            );

            if (recommendations == null) {

                recommendations =
                        Collections.emptyList();
            }

            long totalBodyAssessments =
                    bodyAssessmentRepo
                            .countByUserId(userId);

            long totalLabReports =
                    labReportRepo
                            .countByUserId(userId);

            long totalLifestyleRecords =
                    lifestyleRepo
                            .countByUserId(userId);

            Instant lastActivityDate = null;

            if (bodyWellness != null
                    && bodyWellness.getCreatedAt() != null) {

                lastActivityDate =
                        bodyWellness.getCreatedAt();
            }

            if (lifestyle != null
                    && lifestyle.getCreatedAt() != null) {

                if (lastActivityDate == null
                        || lifestyle
                        .getCreatedAt()
                        .isAfter(lastActivityDate)) {

                    lastActivityDate =
                            lifestyle.getCreatedAt();
                }
            }

            if (latestReport != null
                    && latestReport.getUploadedAt() != null) {

                if (lastActivityDate == null
                        || latestReport
                        .getUploadedAt()
                        .isAfter(lastActivityDate)) {

                    lastActivityDate =
                            latestReport.getUploadedAt();
                }
            }

            ProgressLogEntity latestProgress =
                    progressLogRepo

                            .findFirstByUserIdOrderByLoggedAtDesc(
                                    userId
                            )

                            .orElse(null);

            if (latestProgress != null
                    && latestProgress.getLoggedAt() != null) {

                if (lastActivityDate == null
                        || latestProgress
                        .getLoggedAt()
                        .isAfter(lastActivityDate)) {

                    lastActivityDate =
                            latestProgress.getLoggedAt();
                }
            }

            DashboardResponseDto
                    .ProgressSummaryDto
                    progressDto =

                    DashboardResponseDto
                            .ProgressSummaryDto
                            .builder()

                            .totalBodyAssessments(
                                    totalBodyAssessments
                            )

                            .totalLabReports(
                                    totalLabReports
                            )

                            .totalLifestyleRecords(
                                    totalLifestyleRecords
                            )

                            .lastActivityDate(
                                    lastActivityDate
                            )

                            .build();

            return DashboardResponseDto
                    .builder()

                    .profile(
                            profileDto
                    )

                    .bodyWellness(
                            bodyWellness
                    )

                    .latestLabReport(
                            latestReportDto
                    )

                    .lifestyle(
                            lifestyle
                    )

                    .recommendations(
                            recommendations
                    )

                    .progress(
                            progressDto
                    )

                    .build();
        }
    }

    @RestController
    @RequestMapping("/api/dashboard")
    public static class DashboardRestController {

        @Autowired
        private DashboardService dashboardService;

        @GetMapping
        public ResponseEntity<ApiResponse<DashboardResponseDto>>
        getDashboard(
                Authentication auth) {

            String userId =
                    requireUserId(auth);

            DashboardResponseDto dashboard =
                    dashboardService
                            .getDashboard(userId);

            return ResponseEntity.ok(

                    ApiResponse.success(

                            "Dashboard retrieved successfully",

                            dashboard
                    )
            );
        }
    }

    @RestController
    @RequestMapping("/api/assistant")
    public static class AiAssistantRestController {

        @Autowired
        private LabResultMongoRepo resultRepo;

        @Autowired
        private RestTemplate restTemplate;

        @Value("${ai.service.url:http://localhost:8000/api/ai/chat}")
        private String aiServiceUrl;

        @PostMapping("/chat")
        public ResponseEntity<ApiResponse<Map<String, Object>>> chat(
                @NonNull Authentication auth,
                @Nullable @RequestBody(required = false) Map<String, String> body) {

            String userId = requireUserId(auth);
            Map<String, String> payload = body == null ? new HashMap<>() : body;
            String question = payload.getOrDefault("question", "What should I focus on?");

            List<LabResultEntity> userResults = resultRepo.findByUserId(userId);
            List<Map<String, Object>> parametersPayload = new ArrayList<>();
            if (userResults != null) {
                for (LabResultEntity res : userResults) {
                    Map<String, Object> p = new HashMap<>();
                    p.put("parameter", res.getParameter());
                    p.put("value", res.getValue());
                    p.put("unit", res.getUnit());
                    p.put("referenceRange", res.getReferenceRange());
                    p.put("status", res.getStatus());
                    parametersPayload.add(p);
                }
            }

            Map<String, Object> aiRequest = new HashMap<>();
            aiRequest.put("question", question);
            aiRequest.put("parameters", parametersPayload);
            aiRequest.put("userId", userId);

            String answer = "Personalized wellness guidance based on your laboratory records.";
            String disclaimer = "Disclaimer: My Wellness AI provides general educational and wellness insights only. This is not medical diagnosis, treatment, or prescription.";

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(aiRequest, headers);

                ResponseEntity<Map> aiResponse = restTemplate.exchange(
                        aiServiceUrl,
                        HttpMethod.POST,
                        requestEntity,
                        Map.class
                );

                if (aiResponse.getBody() != null && aiResponse.getBody().get("data") instanceof Map<?, ?> dataMap) {
                    Object ans = dataMap.get("answer");
                    if (ans != null) {
                        answer = ans.toString();
                    }
                    Object disc = dataMap.get("disclaimer");
                    if (disc != null) {
                        disclaimer = disc.toString();
                    }
                }
            } catch (ResourceAccessException rae) {
                answer = "The AI wellness assistant microservice is offline or unreachable at " + aiServiceUrl
                        + ". Please start the Python service on port 8000.";
            } catch (Exception ex) {
                answer = "Unable to process wellness inquiry with AI service: " + ex.getMessage();
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("userId", userId);
            response.put("question", question);
            response.put("answer", answer);
            response.put("disclaimer", disclaimer);

            return ResponseEntity.ok(ApiResponse.success("AI response received", response));
        }
    }
}
