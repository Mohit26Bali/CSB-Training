package lib.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.Objects;

@Entity
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String name;

    protected Tag() { }

    public Tag(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }

    // a natural key (the unique name) makes a good equals/hashCode for a lookup-style entity
    @Override public boolean equals(Object o) { return o instanceof Tag t && Objects.equals(name, t.name); }
    @Override public int hashCode() { return Objects.hash(name); }
}
