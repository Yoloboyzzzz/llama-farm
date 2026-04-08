package be.ucll.controller;

import be.ucll.service.MaterialService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import be.ucll.dto.FilamentUsageDTO;

import java.util.Map;
import java.util.List;

@RestController
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping("/api/material-colors")
    public Map<String, List<String>> getMaterialColors() {
        return materialService.getMaterialColorMap();
    }

    @GetMapping("/api/filament-usage")
    public List<FilamentUsageDTO> getFilamentUsage() {
        return materialService.getCompletedUsage();
    }

}
