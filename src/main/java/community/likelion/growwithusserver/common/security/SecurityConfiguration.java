package community.likelion.growwithusserver.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static java.util.Arrays.asList;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfiguration {
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;
    private final ObjectMapper objectMapper;

    private static final String USER = "USER";
    private static final String OWNER = "OWNER";
    private static final String CARETAKER = "CARETAKER";
    // 명세의 "USER, OWNER, CARETAKER": 로그인한 모든 일반 사용자
    private static final String[] ANY_ROLE = {USER, OWNER, CARETAKER};

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${internal.service-key}")
    private String internalServiceKey;

    @Value("${demo.enabled}")
    private boolean demoEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {

        httpSecurity
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        httpSecurity.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        httpSecurity.exceptionHandling(exceptionHandling ->
                        exceptionHandling
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler)
        );

        // 권한은 docs/api-spec.md 4장의 권한 열을 그대로 옮긴 것이다. 더 구체적인 경로를 먼저 둔다.
        httpSecurity.authorizeHttpRequests(authorize ->
                authorize.requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        // 배포 헬스체크 (docker-compose.yml, deploy.yml)
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // 로컬 저장소에 올린 파일 (file.storage=local)
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/images/caretaker-default.svg").permitAll()
                        .requestMatchers(HttpMethod.GET, "/local-demo", "/local-demo/", "/local-demo/app.js").permitAll()
                        // 내부 배치: 헤더 검증은 InternalServiceKeyFilter가 한다
                        .requestMatchers("/internal/**").hasRole("SYSTEM")
                        // 시연: ADMIN 토큰 + X-Demo-Mode 헤더 (헤더 검증은 DemoModeFilter)
                        .requestMatchers("/demo/**").hasRole("ADMIN")
                        // User
                        .requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/login", "/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/username-check").permitAll()
                        .requestMatchers("/auth/logout").hasAnyRole(ANY_ROLE)
                        .requestMatchers("/users/me/badges").hasRole(OWNER)
                        .requestMatchers("/users/me/**").hasAnyRole(ANY_ROLE)
                        // Farm, Caretaker
                        .requestMatchers("/farms/*/plots/**").hasAnyRole(USER, OWNER)
                        .requestMatchers("/farms/**", "/crops/**").hasAnyRole(ANY_ROLE)
                        .requestMatchers(HttpMethod.POST, "/owners/me/profile").hasAnyRole(USER, OWNER)
                        .requestMatchers("/owners/**").hasRole(OWNER)
                        .requestMatchers(HttpMethod.POST, "/caretakers/me/profile").hasAnyRole(USER, CARETAKER)
                        .requestMatchers("/caretakers/**").hasRole(CARETAKER)
                        .requestMatchers("/tts").hasAnyRole(USER, CARETAKER)
                        // Task
                        .requestMatchers("/caretaker/**").hasRole(CARETAKER)
                        .requestMatchers("/gardens/*/stage-checks").hasRole(CARETAKER)
                        // Home, Diary, Game, Harvest
                        .requestMatchers(
                                "/gardens/**",
                                "/care-logs/**",
                                "/events/**",
                                "/leagues/**",
                                "/quests/**",
                                "/quizzes/**",
                                "/visits/**"
                        ).hasRole(OWNER)
                        // Notification
                        .requestMatchers("/notifications/**").hasAnyRole(OWNER, CARETAKER)
                        .anyRequest().authenticated()
        );

        httpSecurity.addFilterBefore(
                new JwtAuthenticationFilter(tokenProvider),
                UsernamePasswordAuthenticationFilter.class
        );
        httpSecurity.addFilterBefore(
                new InternalServiceKeyFilter(internalServiceKey, objectMapper),
                UsernamePasswordAuthenticationFilter.class
        );
        httpSecurity.addFilterBefore(
                new DemoModeFilter(demoEnabled, objectMapper),
                UsernamePasswordAuthenticationFilter.class
        );


        return httpSecurity.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // ADMIN은 USER, OWNER, CARETAKER 권한을 모두 포함한다. USER, OWNER, CARETAKER는 서로 포함하지 않는다.
    @Bean
    public static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_OWNER
                ROLE_ADMIN > ROLE_CARETAKER
                ROLE_ADMIN > ROLE_USER
                """);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
