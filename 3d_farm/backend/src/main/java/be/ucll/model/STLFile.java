package be.ucll.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stl_files")
public class STLFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;       // e.g. "part1.stl"
    private String path;       // saved file path on disk
    private String color;      // requested print color
    private String material;   // PLA, PETG, etc.
    private int infill;        // infill percentage
    private Boolean brim;      // add brim or not
    private String support;    // "on", "off", "touching_buildplate"
    private int instances;     // number of copies requested
    private String status;     // pending, slicing, sliced, error
    
    private double widthMm;
    private double depthMm;
    private double heightMm;   // required for plate planner
    private Integer printTimeSeconds; // estimated print time in seconds
    
    @Column(name = "min_x")
    private Double minX;

    @Column(name = "min_y")
    private Double minY;      // minimum Y coordinate (bottom-left corner)

    @Column(name = "offset_x")
    private Double offsetX;   // X offset from support to object (typically 2.0mm)

    @Column(name = "offset_y")
    private Double offsetY;   // Y offset from support to object (typically 2.0mm)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @JsonIgnore
    @ManyToMany(mappedBy = "stlFiles")
    private List<GcodeFile> gcodeFiles = new ArrayList<>();

    public STLFile() {
        this.status = "pending";
    }

    public STLFile(
            String name,
            String path,
            String color,
            String material,
            int infill,
            Boolean brim,
            String support,
            int instances,
            Job job
    ) {
        this.name = name;
        this.path = path;
        this.color = color;
        this.material = material;
        this.infill = infill;
        this.brim = brim;
        this.support = support;
        this.instances = instances;
        this.status = "pending";
        this.job = job;
    }

    // Getters / Setters

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public int getInfill() { return infill; }
    public void setInfill(int infill) { this.infill = infill; }

    public Boolean getBrim() { return brim; }
    public void setBrim(Boolean brim) { this.brim = brim; }

    public String getSupport() { return support; }
    public void setSupport(String support) { this.support = support; }

    public int getInstances() { return instances; }
    public void setInstances(int instances) { this.instances = instances; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }

    public double getWidthMm() { return widthMm; }
    public void setWidthMm(double widthMm) { this.widthMm = widthMm; }

    public double getDepthMm() { return depthMm; }
    public void setDepthMm(double depthMm) { this.depthMm = depthMm; }

    public double getHeightMm() { return heightMm; }
    public void setHeightMm(double heightMm) { this.heightMm = heightMm; }

    public Integer getPrintTimeSeconds() { return printTimeSeconds; }
    public void setPrintTimeSeconds(Integer printTimeSeconds) { this.printTimeSeconds = printTimeSeconds; }

    public Double getMinX() { return minX; }
    public void setMinX(Double minX) { this.minX = minX; }

    public Double getMinY() { return minY; }
    public void setMinY(Double minY) { this.minY = minY; }

    public Double getOffsetX() { return offsetX; }
    public void setOffsetX(Double offsetX) { this.offsetX = offsetX; }

    public Double getOffsetY() { return offsetY; }
    public void setOffsetY(Double offsetY) { this.offsetY = offsetY; }
}