package com.springlab.lab.jpa;

import com.springlab.lab.jpa.LabJpaDtos.AttributeInfo;
import com.springlab.lab.jpa.LabJpaDtos.ColumnInfo;
import com.springlab.lab.jpa.LabJpaDtos.EdgeInfo;
import com.springlab.lab.jpa.LabJpaDtos.EntityInfo;
import com.springlab.lab.jpa.LabJpaDtos.Facts;
import com.springlab.lab.jpa.LabJpaDtos.ForeignKeyInfo;
import com.springlab.lab.jpa.LabJpaDtos.JoinTableInfo;
import com.springlab.lab.jpa.LabJpaDtos.Mapping;
import com.springlab.lab.jpa.LabJpaDtos.TableInfo;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Describes the lab's object model and relational schema from the two real sources, never from a hand-written table:
 * the Java side comes from the JPA metamodel and the annotations on the actual entity classes, the database side from
 * MySQL's information_schema (columns, primary keys, unique keys and foreign key constraints that really exist).
 */
@Service
public class JpaMappingService {

    static final String ENTITY_PACKAGE = "com.springlab.lab.jpa.entity";

    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbc;
    private final boolean openInView;
    private volatile Mapping cached;

    public JpaMappingService(EntityManagerFactory entityManagerFactory, JdbcTemplate jdbc,
                             @Value("${spring.jpa.open-in-view:true}") boolean openInView) {
        this.entityManagerFactory = entityManagerFactory;
        this.jdbc = jdbc;
        this.openInView = openInView;
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
        List<Class<?>> classes = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .filter(type -> type.getPackageName().equals(ENTITY_PACKAGE))
                .sorted(Comparator.comparing(Class::getSimpleName))
                .collect(Collectors.toList());

        List<EntityInfo> entities = new ArrayList<>();
        Set<String> joinTables = new java.util.LinkedHashSet<>();
        for (Class<?> type : classes) {
            List<AttributeInfo> attributes = attributesOf(type);
            attributes.stream().filter(a -> a.joinTable() != null).forEach(a -> joinTables.add(a.joinTable().name()));
            entities.add(new EntityInfo(type.getSimpleName(), type.getName(), type.getAnnotation(Table.class).name(), attributes));
        }

        Map<String, List<ColumnInfo>> columns = new LinkedHashMap<>();
        jdbc.query("""
                select table_name as tname, column_name as cname, column_type as ctype, is_nullable as nullable,
                       column_key as ckey
                from information_schema.columns
                where table_schema = database() and table_name like 'jpa\\_lab\\_%'
                order by table_name, ordinal_position""", rs -> {
            columns.computeIfAbsent(rs.getString("tname"), k -> new ArrayList<>()).add(new ColumnInfo(rs.getString("cname"),
                    rs.getString("ctype"), "YES".equals(rs.getString("nullable")), "PRI".equals(rs.getString("ckey")),
                    "UNI".equals(rs.getString("ckey"))));
        });
        Map<String, List<ForeignKeyInfo>> foreignKeys = new LinkedHashMap<>();
        jdbc.query("""
                select table_name as tname, constraint_name as cname, column_name as colname,
                       referenced_table_name as rtable, referenced_column_name as rcolumn
                from information_schema.key_column_usage
                where table_schema = database() and referenced_table_name is not null and table_name like 'jpa\\_lab\\_%'
                order by table_name, constraint_name""", rs -> {
            foreignKeys.computeIfAbsent(rs.getString("tname"), k -> new ArrayList<>()).add(new ForeignKeyInfo(rs.getString("cname"),
                    rs.getString("colname"), rs.getString("rtable"), rs.getString("rcolumn")));
        });

        List<TableInfo> tables = columns.entrySet().stream()
                .map(e -> new TableInfo(e.getKey(), joinTables.contains(e.getKey()), e.getValue(),
                        foreignKeys.getOrDefault(e.getKey(), List.of())))
                .toList();

        List<EdgeInfo> edges = new ArrayList<>();
        for (EntityInfo entity : entities) {
            edges.add(new EdgeInfo(entity.id() + "->" + entity.table(), entity.id(), entity.table(), "MAPS_TO", "mapped to table"));
            for (AttributeInfo a : entity.attributes()) {
                if (a.association() != null) {
                    edges.add(new EdgeInfo(entity.id() + "->" + a.target(), entity.id(), a.target(), "ASSOCIATION", a.annotation()));
                }
            }
        }
        for (TableInfo table : tables) {
            for (ForeignKeyInfo fk : table.foreignKeys()) {
                edges.add(new EdgeInfo(table.id() + "->" + fk.referencedTable(), table.id(), fk.referencedTable(), "FOREIGN_KEY",
                        fk.column() + " references " + fk.referencedTable() + "." + fk.referencedColumn()));
            }
        }
        String database = jdbc.queryForObject("select version()", String.class);
        return new Mapping(List.copyOf(entities), tables, List.copyOf(edges),
                new Facts(org.hibernate.Version.getVersionString(), openInView, "MySQL " + database));
    }

    private static List<AttributeInfo> attributesOf(Class<?> type) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            chain.add(0, c);
        }
        List<AttributeInfo> attributes = new ArrayList<>();
        for (Class<?> c : chain) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                attributes.add(attribute(field));
            }
        }
        return attributes;
    }

    private static AttributeInfo attribute(Field field) {
        String name = field.getName();
        String javaType = field.getGenericType().getTypeName().replace("java.lang.", "").replace("java.util.", "")
                .replace("com.springlab.lab.jpa.entity.", "");
        boolean id = field.isAnnotationPresent(Id.class);
        Annotation relation = firstPresent(field, OneToOne.class, OneToMany.class, ManyToOne.class, ManyToMany.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        Column column = field.getAnnotation(Column.class);
        if (relation == null) {
            String columnName = column != null && !column.name().isEmpty() ? column.name() : id ? "id" : name;
            return new AttributeInfo(name, javaType, columnName, id, null, null, false, null, null, List.of(), false, null, null, null);
        }
        String kind = relation.annotationType().getSimpleName();
        String mappedBy = (String) call(relation, "mappedBy");
        boolean hasMappedBy = mappedBy != null && !mappedBy.isEmpty();
        boolean owning = kind.equals("ManyToOne") || !hasMappedBy;
        String fetch = String.valueOf(call(relation, "fetch"));
        Object[] cascade = (Object[]) call(relation, "cascade");
        boolean orphan = Boolean.TRUE.equals(call(relation, "orphanRemoval"));
        JoinTable joinTable = field.getAnnotation(JoinTable.class);
        JoinTableInfo joinTableInfo = joinTable == null ? null : new JoinTableInfo(joinTable.name(),
                joinTable.joinColumns()[0].name(), joinTable.inverseJoinColumns()[0].name());
        return new AttributeInfo(name, javaType, null, false, kind, targetOf(field), owning, hasMappedBy ? mappedBy : null, fetch,
                Arrays.stream(cascade).map(Object::toString).toList(), orphan,
                joinColumn == null ? null : joinColumn.name(), joinTableInfo, render(relation));
    }

    private static String targetOf(Field field) {
        Type generic = field.getGenericType();
        Class<?> target = generic instanceof ParameterizedType p ? (Class<?>) p.getActualTypeArguments()[0] : field.getType();
        return target.getSimpleName();
    }

    @SafeVarargs
    private static Annotation firstPresent(Field field, Class<? extends Annotation>... types) {
        for (Class<? extends Annotation> type : types) {
            Annotation a = field.getAnnotation(type);
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    private static Object call(Annotation annotation, String member) {
        try {
            return annotation.annotationType().getMethod(member).invoke(annotation);
        } catch (ReflectiveOperationException e) {
            return null; // member does not exist on this annotation (for example orphanRemoval on @ManyToOne)
        }
    }

    /** Renders the annotation with only the members whose value differs from the annotation default, like source code. */
    static String render(Annotation annotation) {
        List<String> parts = new ArrayList<>();
        for (Method method : annotation.annotationType().getDeclaredMethods()) {
            Object value = call(annotation, method.getName());
            if (Objects.equals(value, method.getDefaultValue()) || (value instanceof Object[] array && array.length == 0)) {
                continue;
            }
            String text = value instanceof Object[] array
                    ? "{" + Arrays.stream(array).map(Object::toString).collect(Collectors.joining(", ")) + "}"
                    : value instanceof String s ? "\"" + s + "\"" : String.valueOf(value);
            parts.add(method.getName() + " = " + text);
        }
        parts.sort(Comparator.naturalOrder());
        return "@" + annotation.annotationType().getSimpleName() + (parts.isEmpty() ? "" : "(" + String.join(", ", parts) + ")");
    }
}
