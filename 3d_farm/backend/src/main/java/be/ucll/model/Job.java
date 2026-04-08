package be.ucll.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;              // e.g. "Chess Set V2"
    private String status;            // pending, processing, printing, done
    private LocalDateTime createdAt;
    
    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<STLFile> stlFiles;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GcodeFile> gcodeFiles;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public Job() {
        this.createdAt = LocalDateTime.now();
        this.status = "pending";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<STLFile> getStlFiles() {
        return stlFiles;
    }

    public void setStlFiles(List<STLFile> stlFiles) {
        this.stlFiles = stlFiles;
    }

    public List<GcodeFile> getGcodeFiles() {
        return gcodeFiles;
    }

    public void setGcodeFiles(List<GcodeFile> gcodeFiles) {
        this.gcodeFiles = gcodeFiles;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

}