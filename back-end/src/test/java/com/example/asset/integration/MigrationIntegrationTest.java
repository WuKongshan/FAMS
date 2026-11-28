package com.example.asset.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 迁移在真实 MySQL 上的落地验证。
 *
 * <p>这批断言存在的理由：迁移脚本写错（文件名不合规被静默跳过、SQL 只在一部分环境执行、
 * 索引列顺序反了）时，纯 Mockito 单测完全无感。这里直接问 information_schema，
 * 输出的是"数据库里实际长什么样"，而不是"代码希望它长什么样"。</p>
 */
@DisplayName("迁移脚本：真实 MySQL 结构")
class MigrationIntegrationTest extends AbstractIntegrationTest {

    private static final int EXPECTED_MIGRATION_COUNT = 12;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("flyway_schema_history 恰好 12 条记录，全部成功，版本连续覆盖 1..12")
    void flywayHistory_hasTwelveSuccessfulMigrations() {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        Integer succeeded = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);

        assertThat(total).isEqualTo(EXPECTED_MIGRATION_COUNT);
        assertThat(succeeded).isEqualTo(EXPECTED_MIGRATION_COUNT);

        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(versions).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
    }

    @Test
    @DisplayName("check_record 具备 V12 新增的 4 个差异处置列")
    void checkRecord_hasDiffHandleColumns() {
        Set<String> columns = columnsOf("check_record");

        assertThat(columns).contains(
                "diff_handle_status", "diff_handle_type", "diff_handle_time", "diff_handle_by");
    }

    @Test
    @DisplayName("asset_info.pending_order_id 与 analysis_job.engine 均已存在")
    void pendingOrderIdAndEngineColumns_exist() {
        assertThat(columnsOf("asset_info")).contains("pending_order_id");
        assertThat(columnsOf("analysis_job")).contains("engine");
    }

    @Test
    @DisplayName("check_record 有唯一索引 uk_check_record_task_asset，列顺序为 (task_id, asset_id)")
    void checkRecord_hasUniqueIndexOnTaskAndAsset() {
        List<Map<String, Object>> indexColumns = jdbc.queryForList(
                "SELECT column_name, seq_in_index, non_unique FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() "
                        + "  AND table_name = 'check_record' "
                        + "  AND index_name = 'uk_check_record_task_asset' "
                        + "ORDER BY seq_in_index");

        assertThat(indexColumns)
                .as("uk_check_record_task_asset 必须由两列组成，否则并发去重会失效")
                .hasSize(2);
        assertThat(indexColumns)
                .extracting(row -> row.get("column_name"))
                .containsExactly("task_id", "asset_id");
        assertThat(indexColumns)
                .allSatisfy(row -> assertThat(((Number) row.get("non_unique")).intValue())
                        .as("索引必须是 UNIQUE")
                        .isZero());
    }

    private Set<String> columnsOf(String tableName) {
        return Set.copyOf(jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                String.class, tableName));
    }
}
