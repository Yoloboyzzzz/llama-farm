package be.ucll.dto;

import java.time.LocalDateTime;

public class GcodeFileDTO {
    private Long id;
    private String filename;
    private String status;
    private LocalDateTime startedAt;
    private int durationSeconds;
    private Integer remainingTimeSeconds;
    private String downloadUrl;

    public GcodeFileDTO(Long id, String filename, String status, LocalDateTime startedAt,
                 int durationSeconds, String downloadUrl) {
    this.id = id;
    this.filename = filename;
    this.status = status;
    this.startedAt = startedAt;
    this.durationSeconds = durationSeconds;
    this.downloadUrl = downloadUrl;
}

    public GcodeFileDTO() {}
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Integer getRemainingTimeSeconds() {
        return remainingTimeSeconds;
    }

    public void setRemainingTimeSeconds(Integer remainingTimeSeconds) {
        this.remainingTimeSeconds = remainingTimeSeconds;
    }
}
