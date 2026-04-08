package be.ucll.dto;

public class QueueItemDTO {
    private Long id;
    private String filename;
    private String status;
    private String printerModel;
    private int position; // print order
    private int duration; // estimated print duration in seconds

    public QueueItemDTO(String filename, Long id, int position, String printerModel, String status) {
        this.filename = filename;
        this.id = id;
        this.position = position;
        this.printerModel = printerModel;
        this.status = status;
    }
    public QueueItemDTO(){}
    
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

    public String getPrinterModel() {
        return printerModel;
    }

    public void setPrinterModel(String printerModel) {
        this.printerModel = printerModel;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

}
