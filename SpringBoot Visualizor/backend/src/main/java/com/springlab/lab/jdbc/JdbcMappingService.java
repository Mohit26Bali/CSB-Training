package com.springlab.lab.jdbc;

import com.springlab.lab.jdbc.JdbcLabDtos.ColumnInfo;
import com.springlab.lab.jdbc.JdbcLabDtos.ForeignKeyInfo;
import com.springlab.lab.jdbc.JdbcLabDtos.Mapping;
import com.springlab.lab.jdbc.JdbcLabDtos.TableInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The lab's relational schema, read from MySQL information_schema (columns, keys, foreign keys). No URL or credential is read. */
@Service
public class JdbcMappingService {

    private final JdbcTemplate jdbc;
    private final JdbcConnections connections;
    private volatile Mapping cached;

    public JdbcMappingService(JdbcConnections connections) {
        this.connections = connections;
        this.jdbc = new JdbcTemplate(connections.applicationDataSource());
    }

    public Mapping mapping() {
        Mapping result = cached;
        if (result == null) {
            result = build();
            cached = result;
        }
        return result;
    }

    private Mapping build() {
        Map<String, List<ColumnInfo>> columns = new LinkedHashMap<>();
        jdbc.query("""
                select table_name as tname, column_name as cname, column_type as ctype, is_nullable as nullable, column_key as ckey
                from information_schema.columns
                where table_schema = database() and table_name like 'jdbc\\_lab\\_%'
                order by table_name, ordinal_position""", rs -> {
            columns.computeIfAbsent(rs.getString("tname"), k -> new ArrayList<>()).add(new ColumnInfo(rs.getString("cname"), rs.getString("ctype"),
                    "YES".equals(rs.getString("nullable")), "PRI".equals(rs.getString("ckey")), "UNI".equals(rs.getString("ckey"))));
        });
        Map<String, List<ForeignKeyInfo>> foreignKeys = new LinkedHashMap<>();
        jdbc.query("""
                select table_name as tname, constraint_name as cname, column_name as colname, referenced_table_name as rtable,
                       referenced_column_name as rcolumn
                from information_schema.key_column_usage
                where table_schema = database() and referenced_table_name is not null and table_name like 'jdbc\\_lab\\_%'
                order by table_name, constraint_name""", rs -> {
            foreignKeys.computeIfAbsent(rs.getString("tname"), k -> new ArrayList<>()).add(new ForeignKeyInfo(rs.getString("cname"),
                    rs.getString("colname"), rs.getString("rtable"), rs.getString("rcolumn")));
        });
        List<TableInfo> tables = columns.entrySet().stream()
                .map(e -> new TableInfo(e.getKey(), e.getValue(), foreignKeys.getOrDefault(e.getKey(), List.of()))).toList();
        try (Connection connection = connections.applicationDataSource().getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String isolation = jdbc.queryForObject("select @@transaction_isolation", String.class);
            return new Mapping(tables, meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion(),
                    meta.getDriverName() + " " + meta.getDriverVersion().split(" ")[0], isolation);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read database metadata", e);
        }
    }
}
