package com.macro.mall.searchpg.reader;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

@Component
public class MysqlProductReader {

    private final JdbcTemplate jdbcTemplate;

    public MysqlProductReader(@Qualifier("mysqlDataSource") DataSource mysqlDataSource) {
        this.jdbcTemplate = new JdbcTemplate(mysqlDataSource);
    }

    public record ProductRow(Long id, String name, String subTitle, String keywords,
                              String brandName, String categoryName) {}

    public List<ProductRow> readAllProducts() {
        return jdbcTemplate.query("""
                SELECT id, name, sub_title, keywords, brand_name, product_category_name
                FROM pms_product
                WHERE delete_status = 0 AND publish_status = 1
                """,
                (rs, rowNum) -> new ProductRow(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("sub_title"),
                        rs.getString("keywords"),
                        rs.getString("brand_name"),
                        rs.getString("product_category_name")));
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pms_product WHERE delete_status = 0 AND publish_status = 1",
                Integer.class);
        return count != null ? count : 0;
    }
}
