package be.ucll.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity
@Table(name = "gcode_files")
public class GcodeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String path;
    private String model;      // printer model (e.g. MINI, MK3)
    private String color;
    private int instances;
    private int duration;
    private int weight;
    private String material;
    private LocalDateTime startedAt;
    private Integer queuePosition;
    @ManyToOne
    @JoinColumn(name = "printer_id")
    @JsonBackReference
    private Printer printer;

    private String status;     // waiting, printing, done, failed

    @ManyToMany
    @JoinTable(
        name = "gcode_stl_files",
        joinColumns = @JoinColumn(name = "gcode_file_id"),
        inverseJoinColumns = @JoinColumn(name = "stl_file_id")
    )
    private List<STLFile> stlFiles = new ArrayList<>();

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    public GcodeFile() { this.status = "waiting"; }

    @Column(name = "label_printed", nullable = false)
    private Boolean labelPrinted = false;  // Track if label has been printed

    @Column(name = "remaining_time_seconds")
    private Integer remainingTimeSeconds;  // Live remaining time polled from PrusaLink

    public GcodeFile(String name, String path, String model, String color, int instances, Job job) {
        this.name = name;
        this.path = path;
        this.model = model;
        this.color = color;
        this.instances = instances;
        this.status = "waiting";
        this.job = job;
    }

    // getters/setters ...

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getModel() {
        return model;
    }

    public String getColor() {
        return color;
    }

    public int getInstances() {
        return instances;
    }

    public String getStatus() {
        return status;
    }

    public Job getJob() {
        return job;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public void setInstances(int instances) {
        this.instances = instances;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public int getQueuePostion() {
        return queuePosition;
    }

    public void setQueuePostion(int queuePostion) {
        this.queuePosition = queuePostion;
    }

    public Integer getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(Integer queuePosition) {
        this.queuePosition = queuePosition;
    }

    public Printer getPrinter() {
        return printer;
    }

    public void setPrinter(Printer printer) {
        this.printer = printer;
    }
    public Boolean getLabelPrinted() { return labelPrinted; }
    public void setLabelPrinted(Boolean labelPrinted) { this.labelPrinted = labelPrinted; }

    public List<STLFile> getStlFiles() { return stlFiles; }
    public void setStlFiles(List<STLFile> stlFiles) { this.stlFiles = stlFiles; }

    public Integer getRemainingTimeSeconds() { return remainingTimeSeconds; }
    public void setRemainingTimeSeconds(Integer remainingTimeSeconds) { this.remainingTimeSeconds = remainingTimeSeconds; }
}

