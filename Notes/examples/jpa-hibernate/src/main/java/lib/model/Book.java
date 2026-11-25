package lib.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
public class Book {

    public enum Status { DRAFT, PUBLISHED, OUT_OF_PRINT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private int pages;

    @Enumerated(EnumType.STRING)                    // store the name, never the ordinal
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)     // LAZY: do not load the author until it is used
    @JoinColumn(name = "author_id")
    private Author author;

    @ManyToMany
    @JoinTable(name = "book_tag", joinColumns = @JoinColumn(name = "book_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    @Version                                        // optimistic locking: incremented on every update
    private long version;

    protected Book() { }

    public Book(String title, int pages) { this.title = title; this.pages = pages; }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getPages() { return pages; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Author getAuthor() { return author; }
    void setAuthor(Author author) { this.author = author; }
    public Set<Tag> getTags() { return tags; }
    public long getVersion() { return version; }

    // Entity equality: based on the database id once it exists; a constant hash code keeps it stable when the id is assigned.
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book other)) return false;
        return id != null && Objects.equals(id, other.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
