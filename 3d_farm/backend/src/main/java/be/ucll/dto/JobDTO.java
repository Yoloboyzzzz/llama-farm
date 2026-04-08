package be.ucll.dto;

import java.time.LocalDateTime;
import java.util.List;

public class JobDTO {
    private Long id;
    private String name;
    private String status;
    private LocalDateTime createdAt;
    private String userName;
    private String userEmail;
    private List<GcodeFileDTO> gcodeFiles;
    private List<String> failedFiles = new java.util.ArrayList<>();

    public JobDTO(Long id, String name, String status, LocalDateTime createdAt,
              String userName, String userEmail, List<GcodeFileDTO> gcodeFiles) {
    this.id = id;
    this.name = name;
    this.status = status;
    this.createdAt = createdAt;
    this.userName = userName;
    this.userEmail = userEmail;
    this.gcodeFiles = gcodeFiles;
}
    public JobDTO() {}
    // getters and setters

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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public List<GcodeFileDTO> getGcodeFiles() {
        return gcodeFiles;
    }

    public void setGcodeFiles(List<GcodeFileDTO> gcodeFiles) {
        this.gcodeFiles = gcodeFiles;
    }

    public List<String> getFailedFiles() {
        return failedFiles;
    }

    public void setFailedFiles(List<String> failedFiles) {
        this.failedFiles = failedFiles;
    }

}
