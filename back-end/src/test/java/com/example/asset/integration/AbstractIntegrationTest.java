package com.example.asset.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类：加载完整 Spring 上下文 + 真实 MySQL。
 *
 * <p><b>为什么必须是 MySQL 而不是 H2</b>：迁移脚本里有 {@code ENGINE=InnoDB}、
 * {@code COLLATE utf8mb4_general_ci}、{@code ALTER TABLE ... ADD UNIQUE KEY}，
 * H2 的 MySQL 兼容模式跑不了；而"迁移脚本能否真的跑通"正是本批测试要守住的第一条线。</p>
 *
 * <p><b>容器共享</b>：容器以 {@code static final} 声明并在静态块中手动 {@link MySQLContainer#start()}，
 * <b>刻意不用</b> {@code @Testcontainers}/{@code @Container} —— 那会让每个测试类各起一个容器。
 * 5 个测试类共用同一个 MySQL 实例；数据隔离由各测试类自己负责
 * （可回滚的用 {@code @Transactional}，多线程/异步的自己清理）。</p>
 *
 * <p><b>属性注入</b>：{@code @DynamicPropertySource} 的优先级高于 application.yml 与 .env，
 * 因此即使开发者本机 .env 里配了真实库和真实 JWT 密钥，也不会被测试用到。
 * {@code jwt.secret} 必须给足 32 字节，否则 {@code JwtUtils.validateSecret()} 会让上下文启动失败。</p>
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    /** HS256 要求 >= 32 字节；这里固定一个仅供测试使用的值。 */
    private static final String TEST_JWT_SECRET = "fams-integration-test-secret-0123456789abcdef";

    /**
     * 全测试共享的 MySQL 容器。
     * <p>手动 start（静态块）而不是依赖 {@code @Container} 的生命周期，避免"一类一容器"。
     * {@code @ServiceConnection} 由 Spring Boot 3.2 自带，自动把数据源指向该容器，
     * 不手拼 jdbc url，也就不会误连本机 3306 上的真实库。</p>
     */
    @ServiceConnection
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("fams_it")
                    .withUsername("fams")
                    .withPassword("fams");

    static {
        // Testcontainers 1.19.3 内置的 Docker 客户端默认按 API v1.32 发请求，
        // 而 Docker Engine 29 把最低可接受版本提到了 1.40，直接拒绝（HTTP 400），
        // 表现为误导性的 "Could not find a valid Docker environment"。
        // 1.41 是兼容区间最宽的选择：Docker 20.10（最高 1.41）到 29（最低 1.40）都接受。
        // docker-java 的优先级是「系统属性 api.version > 环境变量 DOCKER_API_VERSION」，
        // 因此两者任一显式配置都必须尊重，不能静默覆盖 CI 上设好的值。
        if (System.getProperty("api.version") == null
                && System.getenv("DOCKER_API_VERSION") == null) {
            System.setProperty("api.version", "1.41");
        }
        MYSQL.start();
    }

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("jwt.expiration", () -> "86400000");
        // 分析服务关闭：让 analyzeDiff 走"调用失败 → 模板降级"分支，测试不依赖任何外部 HTTP 服务。
        // DEEPSEEK_API_KEY 是 llm-service（Python）读取的变量，Java 侧不用；这里一并置空，
        // 保证即便有人把 analysis.enabled 改回 true，也不会真的带去真实密钥。
        registry.add("analysis.enabled", () -> "false");
        registry.add("DEEPSEEK_API_KEY", () -> "");
        // 关机阶段 Flowable 引擎关闭（ProcessEngineImpl.close）会借数据库连接；
        // 若此时连接池已无可用连接，会等满 connection-timeout（默认 30s），
        // 导致 Surefire fork 在 System.exit 后空等并被强杀（日志：kill self fork JVM）。
        // 测试机不需要 30s 的容错，压到 2s，让关机阶段快速失败。
        registry.add("spring.datasource.hikari.connection-timeout", () -> "2000");
    }
}
