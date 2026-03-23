# Authentication Microservice

A production-grade Spring Boot authentication microservice that provides secure user registration and login functionality using JWT tokens with RSA key signing.

Spring Security provides comprehensive security features for Java applications, and this microservice leverages its capabilities to implement robust authentication and authorization mechanisms.

## RSA JWT Signing

This service uses RSA asymmetric encryption to sign JWT tokens with a private key and verify them with a public key. The private key signs tokens server-side while the public key can be distributed to services for verification, making this a production-grade approach that enhances security by separating signing and verification concerns.

## Project Setup

### 1. Required Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-devtools</artifactId>
        <scope>runtime</scope>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.13.0</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.13.0</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.13.0</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

### 2. RSA Key Pair Creation

Generate PKCS8 format keys using OpenSSL commands:

```bash
# Generate private key
openssl genrsa -out private.pem 2048

# Convert to PKCS8 format
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in private.pem -out private_pkcs8.pem

# Extract public key
openssl rsa -in private_pkcs8.pem -pubout -out public.pem
```

*You can also find online RSA key generators for convenience.*

## Architecture Flow

### 1. Security Flow

```
Security Filter Chain
    ↓ (1. Disabling CSRF, 2. Allowing requests with matchers, 3. Stateless session)
UserDetailsService
    ↓ (Responsible for loading user entity instead of relying on Spring Security user)
UserPrincipal
    ↓ (Spring Security works with UserDetails not our custom User (DB). 
        So this is responsible for linking [User (DB Entity) → UserPrincipal (Security Adapter)])
```

**Why not `public class User implements UserDetails {}`?**
This approach creates tight coupling between your database entity and Spring Security, making it difficult to change security implementations without affecting your domain model. Using `public class UserPrincipal implements UserDetails {}` provides a clean separation of concerns.

### 2. Auth Service Flow

```
Controller
    ↓ (PostMapping, RequestMapping, ResponseEntity)
Service Layer
    ↓ (Abstract methods: login and signup)
Repository (JPA)
    ↓ (Abstract method: findByUsername)
Model (User)
    ↓ (Database: id, email, name, password, createdAt)
Database
```

## Key Management

### Key Storage Strategy

→ **Recommended**: Environment Variables / Vault / Secret Manager (Production Grade)

→ **For Development**: Placed in resources
```
src/main/resources/keys/
private.pem
public.pem
```

### Key Loader Service

This service loads RSA keys from the classpath, handling file reading, Base64 decoding, and key specification conversion.

```java
@Service
@Slf4j
public class KeyLoader {

    private String readKey(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);

        if (!resource.exists()) {
            log.error("Key file NOT FOUND at path: {}", path);
            throw new RuntimeException("Key file not found: " + path);
        }
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

### Loading Private and Public Keys

These methods read PEM files, remove headers, decode Base64 content, and create proper key specifications for RSA key generation.

- **PKCS8EncodedKeySpec**: Standard format for private keys in PKCS#8
- **X509EncodedKeySpec**: Standard format for public keys in X.509

```java
@Service
@Slf4j
public class KeyLoader {

    private PublicKey loadPublicKey(String path) throws Exception {
        String key = readKey(path)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(key);
        log.info("Public key Base64 decoded successfully");

        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        return kf.generatePublic(spec);
    }

    private PrivateKey loadPrivateKey(String path) throws Exception {
        String key = readKey(path)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(key);
        log.info("Private key Base64 decoded successfully");

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        return kf.generatePrivate(spec);
    }

    private String readKey(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);

        if (!resource.exists()) {
            log.error("Key file NOT FOUND at path: {}", path);
            throw new RuntimeException("Key file not found: " + path);
        }
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

### Optimized Key Loading with @PostConstruct

Using @PostConstruct loads keys during Spring bean initialization, preventing repeated file I/O operations and improving performance.

```java
@Getter
@Service
@Slf4j
public class KeyLoader {

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            this.privateKey = loadPrivateKey("keys/private.pem");
            log.info("Private Key loaded successfully");

            this.publicKey = loadPublicKey("keys/public.pem");
            log.info("Public Key loaded successfully");
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to load RSA keys", e);
        }
    }

    private PublicKey loadPublicKey(String path) throws Exception {
        String key = readKey(path)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(key);
        log.info("Public key Base64 decoded successfully");

        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        return kf.generatePublic(spec);
    }

    private PrivateKey loadPrivateKey(String path) throws Exception {
        String key = readKey(path)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(key);
        log.info("Private key Base64 decoded successfully");

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        return kf.generatePrivate(spec);
    }

    private String readKey(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);

        if (!resource.exists()) {
            log.error("Key file NOT FOUND at path: {}", path);
            throw new RuntimeException("Key file not found: " + path);
        }
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

### JWT Token Generation

This service creates JWT tokens with user claims, sets expiration, and signs them with the private key using RS256 algorithm.

```java
@Service
@RequiredArgsConstructor
public class JwtService {

    private final KeyLoader keyLoader;

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(60 * 15);

        return Jwts.builder()
                .claims(prepareClaims(user))
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(keyLoader.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    private Map<String, Object> prepareClaims(User user) {
        return Map.of(
                "user", user.getUsername(),
                "roles", user.getRoles()
                        .stream()
                        .map(ROLE::getName)
                        .toList()
        );
    }
}
```

## Signup Flow

### Password Encoder Bean

BCryptPasswordEncoder provides secure password hashing for storage in the database.

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### AuthController - Signup Endpoint

Controller handles HTTP requests and delegates business logic to the service layer.

```java
@RestController
@RequestMapping("api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
        return authService.signup(request);
    }
}
```

### SignupRequest DTO

Data Transfer Object for receiving signup requests with validation.

```java
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class SignupRequest {
    private String name;
    private String email;
    private String password;
}
```

### User Roles Enum

Defines available roles with proper Spring Security naming convention.

```java
@Getter
public enum ROLE {
    USER("ROLE_USER"),
    ADMIN("ROLE_ADMIN"),
    MANAGER("ROLE_MANAGER");

    private final String name;

    ROLE(String name) {
        this.name = name;
    }
}
```

### AuthService Interface

Defines contract for authentication operations.

```java
public interface AuthService {
    ResponseEntity<AuthResponse> signup(SignupRequest request);
    ResponseEntity<AuthResponse> login(LoginRequest request);
}
```

### AuthServiceImpl - Signup Logic

Encodes password using BCrypt and saves user to database via JPA repository.

```java
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public ResponseEntity<AuthResponse> signup(SignupRequest request) {
        // 1. Saving User in DB
        var user = User.builder()
                        .username(request.getEmail())
                        .password(passwordEncoder.encode(request.getPassword()))
                        .roles(List.of(ROLE.USER))
                        .build();

        var response = userRepository.save(user);

        // 2. Token Generation
        if (response.getId() != null) {
            return ResponseEntity.ok(AuthResponse.builder()
                            .token(jwtService.generateToken(user))
                            .build());
        }
        return ResponseEntity.badRequest().body(null);
    }
}
```

## Login Flow

### AuthController - Login Endpoint

Adds login endpoint alongside signup for authentication.

```java
@RestController
@RequestMapping("api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
```

### AuthenticationManager Bean

Required for authenticating users with username/password credentials.

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }
}
```

### AuthenticationProvider Bean

Customizes Spring Security to use our UserDetailsService instead of default in-memory user store.

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}
```

### CustomUserDetailsService

Critical component that bridges database users with Spring Security's UserDetails interface.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(UserPrincipal::new)
                .orElseThrow(() -> {
                    log.warn("User NOT FOUND: {}", username);
                    return new UsernameNotFoundException("User not found");
                });
    }
}
```

### UserRepository - findByUsername

JPA repository method to find users by username for authentication.

```java
@EnableJpaRepositories
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);
}
```

### AuthServiceImpl - Login Logic

AuthenticationManager validates credentials using UsernamePasswordAuthenticationToken and returns authenticated UserPrincipal.

```java
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public ResponseEntity<AuthResponse> signup(SignupRequest request) {
        var user = User.builder()
                        .username(request.getEmail())
                        .password(passwordEncoder.encode(request.getPassword()))
                        .roles(List.of(ROLE.USER))
                        .build();

        var response = userRepository.save(user);

        if (response.getId() != null) {
            return ResponseEntity.ok(AuthResponse.builder()
                            .token(jwtService.generateToken(user))
                            .build());
        }
        return ResponseEntity.badRequest().body(null);
    }

    @Override
    public ResponseEntity<AuthResponse> login(LoginRequest request) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // Get Authenticated user
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        assert userPrincipal != null;
        User user = userPrincipal.getUser();

        // Generate JWT
        String token = jwtService.generateToken(user);

        return ResponseEntity.ok(
                AuthResponse.builder()
                        .token(token)
                        .build()
        );
    }
}
```

## Exception Handling

### ErrorResponse DTO

Standardized response format for API errors.

```java
@Setter
@Getter
@Builder
@AllArgsConstructor
public class ErrorResponse {
    private String message;
    private int status;
    private LocalDateTime timestamp;
}
```

### GlobalExceptionHandler

Centralized exception handling for consistent error responses across the API.

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Invalid username or Password. {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .message("Invalid username or password")
                        .status(HttpStatus.UNAUTHORIZED.value())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error occurred. {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .message("Something went wrong")
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}
```

### Security Filter Chain Configuration

Configures Spring Security with stateless session management, CSRF disabled, and public auth endpoints.

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        .anyRequest().denyAll()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}
```

## API Endpoints

- `POST /api/v1/auth/signup` - User registration
- `POST /api/v1/auth/login` - User authentication

## Security Features

- JWT token-based authentication
- RSA asymmetric key signing
- BCrypt password hashing
- Stateless session management
- Custom user details service
- Centralized exception handling
- Role-based access control ready
