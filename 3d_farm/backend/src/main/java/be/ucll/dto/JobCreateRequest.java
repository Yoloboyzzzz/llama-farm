package be.ucll.dto;

import java.util.List;
import java.util.Map;

public class JobCreateRequest {
    private String name;
    private List<Map<String, Object>> files; // combined STL + Gcode list

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Map<String, Object>> getFiles() { return files; }
    public void setFiles(List<Map<String, Object>> files) { this.files = files; }
}
