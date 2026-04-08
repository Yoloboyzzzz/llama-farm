package be.ucll.controller;

import be.ucll.repository.UserRepository;
import be.ucll.service.EstimateService;
import be.ucll.service.EstimateService.EstimateResult;
import be.ucll.model.User;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/estimate")
public class EstimateController {

    private final EstimateService service;
    private final UserRepository userRepository;

    public EstimateController(EstimateService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> estimate(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam Map<String, String> params,
            Authentication authentication
    ) throws Exception {

        // ✅ Check if user is allowed to submit estimates
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Admins always have access, regular users need canEstimate=true
        if (!user.isAdmin() && !user.getCanEstimate()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "Estimate access blocked",
                            "message", "Your account is blocked from submitting estimates. Contact an administrator."
                    ));
        }

        // ✅ Proceed with estimate logic
        List<EstimateResult> results = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            int infill = Integer.parseInt(params.getOrDefault("infill_" + i, "20"));
            EstimateResult res = service.estimate(files.get(i), infill);
            results.add(res);
        }

        return ResponseEntity.ok(results);
    }
}