package be.ucll.tasks;

import be.ucll.model.GcodeFile;
import be.ucll.model.Printer;
import be.ucll.repository.GcodeFileRepository;
import be.ucll.repository.PrinterRepository;
import be.ucll.service.NotificationService;
import be.ucll.service.PrinterErrorService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Service
public class PrinterStatusChecker {

    @Autowired
    private PrinterRepository printerRepository;

    @Autowired
    private GcodeFileRepository gcodeFileRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PrinterErrorService printerErrorService;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${label.printer.url:http://192.168.133.210:8765/api/print/namebadge}")
    private String labelPrinterUrl;

    @Value("${label.printer.enabled:true}")
    private boolean labelPrinterEnabled;

    @Transactional
    @Scheduled(fixedRate = 10000)
    public void updatePrinterStatuses() {

        List<Printer> printers = printerRepository.findAll();

        for (Printer printer : printers) {
            if (!printer.isInUse()) continue;

            // Get previous status to detect state changes
            String previousStatus = printer.getStatus();

            // ===============================
            // OctoPrint
            // ===============================
            if ("octoprint".equalsIgnoreCase(printer.getConnectionType())) {
                try {
                    String url = printer.getIp() + "/api/job";

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-Api-Key", printer.getApiKey());
                    HttpEntity<Void> entity = new HttpEntity<>(headers);

                    ResponseEntity<String> response = restTemplate.exchange(
                            url, HttpMethod.GET, entity, String.class
                    );

                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        JSONObject data = new JSONObject(response.getBody());
                        String state = data.optString("state", "Unknown");

                        String newStatus;
                        if ("Printing".equalsIgnoreCase(state)) {
                            newStatus = "printing";
                        } else if ("Operational".equalsIgnoreCase(state)) {
                            newStatus = "idle";
                        } else if ("Paused".equalsIgnoreCase(state)) {
                            newStatus = "paused";
                        } else {
                            newStatus = "offline";
                        }

                        printerRepository.updatePrinterStatus(printer.getId(), newStatus);
                        //System.out.println("✅ " + printer.getName() + " (OctoPrint) → " + newStatus);

                        // ✅ NOTIFICATION: Nozzle cleaning when paused
                        handleNozzleCleaningNotification(printer.getName(), printer.getIp(), previousStatus, newStatus);
                        // ✅ NOTIFICATION: Auto-dismiss error if printer recovered
                        handleErrorNotification(printer, previousStatus, newStatus);
                    }

                } catch (HttpStatusCodeException e) {
                    printerRepository.markOffline(printer.getId());
                    String detail = "HTTP " + e.getStatusCode().value() + " — " + e.getResponseBodyAsString().strip();
                    System.out.println("⚠️ " + printer.getName() + " (OctoPrint) error: " + detail);
                    if (!"offline".equalsIgnoreCase(previousStatus)) {
                        printerErrorService.logError(printer, "Unreachable: " + detail);
                        notificationService.createPrinterErrorNotification(printer.getName(), printer.getIp(), "Printer unreachable");
                    }
                } catch (Exception e) {
                    printerRepository.markOffline(printer.getId());
                    System.out.println("⚠️ " + printer.getName() + " (OctoPrint) offline or unreachable: " + e.getMessage());
                    if (!"offline".equalsIgnoreCase(previousStatus)) {
                        printerErrorService.logError(printer, "Unreachable: " + e.getMessage());
                        notificationService.createPrinterErrorNotification(printer.getName(), printer.getIp(), "Printer unreachable");
                    }
                }

            // ===============================
            // PrusaLink
            // ===============================
            } else if ("prusalink".equalsIgnoreCase(printer.getConnectionType())) {
                try {
                    String url = printer.getIp() + "/api/v1/status";

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-Api-Key", printer.getApiKey());
                    HttpEntity<Void> entity = new HttpEntity<>(headers);

                    ResponseEntity<String> response = restTemplate.exchange(
                            url, HttpMethod.GET, entity, String.class
                    );

                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        JSONObject data = new JSONObject(response.getBody());
                        
                        JSONObject printerData = data.optJSONObject("printer");
                        if (printerData != null) {
                            String state = printerData.optString("state", "Unknown");

                            String newStatus;
                            if ("PRINTING".equalsIgnoreCase(state)) {
                                newStatus = "printing";
                            } else if ("IDLE".equalsIgnoreCase(state) || 
                                       "READY".equalsIgnoreCase(state) || 
                                       "FINISHED".equalsIgnoreCase(state)) {
                                newStatus = "idle";
                            } else if ("PAUSED".equalsIgnoreCase(state)) {
                                newStatus = "paused";
                            } else if ("BUSY".equalsIgnoreCase(state)) {
                                newStatus = "paused";
                            } else if ("ERROR".equalsIgnoreCase(state) ||
                                       "ATTENTION".equalsIgnoreCase(state)) {
                                newStatus = "error";
                                StringBuilder errorMsg = new StringBuilder("State: ").append(state);
                                JSONObject errorObj = printerData.optJSONObject("error");
                                if (errorObj != null) {
                                    int code = errorObj.optInt("code", -1);
                                    String text = errorObj.optString("text", "");
                                    String title = errorObj.optString("title", "");
                                    if (code != -1) errorMsg.append(" | Code: ").append(code);
                                    if (!title.isEmpty()) errorMsg.append(" | ").append(title);
                                    if (!text.isEmpty()) errorMsg.append(" — ").append(text);
                                }
                                JSONObject jobObj2 = data.optJSONObject("job");
                                if (jobObj2 != null) {
                                    String printingFile = jobObj2.optString("display_name", "");
                                    if (!printingFile.isEmpty()) errorMsg.append(" | File: ").append(printingFile);
                                }
                                // Always update the notification so a prior "unreachable" message gets replaced
                                notificationService.createOrUpdatePrinterErrorNotification(printer.getName(), printer.getIp(), errorMsg.toString());
                                System.out.println("   🚨 PrusaLink error notification updated: " + errorMsg);
                                // Only log the error to DB on first transition
                                if (!"error".equalsIgnoreCase(previousStatus)) {
                                    printerErrorService.logError(printer, errorMsg.toString());
                                }
                            } else {
                                newStatus = "offline";
                                System.out.println("⚠️ [" + printer.getName() + "] Unknown PrusaLink state: '" + state + "' — RAW: " + data.toString(2));
                            }

                            printerRepository.updatePrinterStatus(printer.getId(), newStatus);
                            //System.out.println("✅ " + printer.getName() + " (PrusaLink) → " + newStatus);

                            // ✅ Poll remaining time from PrusaLink job object
                            JSONObject jobData = data.optJSONObject("job");
                            if (jobData != null && "printing".equalsIgnoreCase(newStatus)) {
                                int timeRemaining = jobData.optInt("time_remaining", -1);
                                GcodeFile currentFile = printer.getCurrentFile();
                                if (currentFile != null && timeRemaining >= 0) {
                                    currentFile.setRemainingTimeSeconds(timeRemaining);
                                    gcodeFileRepository.save(currentFile);
                                }
                            }

                            // ✅ NOTIFICATION: Nozzle cleaning when paused (not when BUSY)
                            if (!"BUSY".equalsIgnoreCase(state)) {
                                handleNozzleCleaningNotification(printer.getName(), printer.getIp(), previousStatus, newStatus);
                            }
                            // ✅ NOTIFICATION: Filament operation (BUSY state)
                            handleBusyNotification(printer, state, previousStatus, newStatus);
                            // ✅ NOTIFICATION: Error create/dismiss
                            handleErrorNotification(printer, previousStatus, newStatus);

                            // ===============================
                            // LABEL PRINTING LOGIC
                            // ===============================
                            if ("printing".equalsIgnoreCase(previousStatus) && "idle".equalsIgnoreCase(newStatus)) {
                                
                                GcodeFile currentFile = printer.getCurrentFile();
                                
                                if (currentFile != null && !currentFile.getLabelPrinted()) {
                                    System.out.println("🏷️  Print finished! Triggering label for: " + currentFile.getName());
                                    
                                    if (printLabel(printer.getName(), currentFile.getName())) {
                                        currentFile.setLabelPrinted(true);
                                        gcodeFileRepository.save(currentFile);
                                        System.out.println("✅ Label printed successfully");
                                    } else {
                                        System.out.println("⚠️  Label printing failed");
                                    }
                                }
                            }
                            
                        } else {
                            printerRepository.markOffline(printer.getId());
                            System.out.println("⚠️ " + printer.getName() + " (PrusaLink) - invalid response format");
                        }
                    }

                } catch (HttpStatusCodeException e) {
                    printerRepository.markOffline(printer.getId());
                    String detail = "HTTP " + e.getStatusCode().value() + " — " + e.getResponseBodyAsString().strip();
                    System.out.println("⚠️ " + printer.getName() + " (PrusaLink) error: " + detail);
                    if (!"offline".equalsIgnoreCase(previousStatus)) {
                        printerErrorService.logError(printer, "Unreachable: " + detail);
                        notificationService.createPrinterErrorNotification(printer.getName(), printer.getIp(), "Printer unreachable");
                    }
                } catch (Exception e) {
                    printerRepository.markOffline(printer.getId());
                    System.out.println("⚠️ " + printer.getName() + " (PrusaLink) offline or unreachable: " + e.getMessage());
                    if (!"offline".equalsIgnoreCase(previousStatus)) {
                        printerErrorService.logError(printer, "Unreachable: " + e.getMessage());
                        notificationService.createPrinterErrorNotification(printer.getName(), printer.getIp(), "Printer unreachable");
                    }
                }

            } else {
                System.out.println("❓ Unknown connection type for printer " + printer.getName() + ": " + printer.getConnectionType());
            }
        }
    }

    /**
     * Handle printer error notifications: create on first error/offline, auto-dismiss on recovery
     */
    private void handleErrorNotification(Printer printer, String previousStatus, String newStatus) {
        boolean wasError = "error".equalsIgnoreCase(previousStatus) || "offline".equalsIgnoreCase(previousStatus);
        boolean isError  = "error".equalsIgnoreCase(newStatus)      || "offline".equalsIgnoreCase(newStatus);

        if (isError && !wasError) {
            notificationService.createPrinterErrorNotification(
                    printer.getName(), printer.getIp(), "Printer is " + newStatus);
            System.out.println("   🚨 Error notification created for " + printer.getName());
        } else if (!isError && wasError) {
            notificationService.autoDismissPrinterErrorNotification(printer.getName());
            System.out.println("   ✅ Error notification auto-dismissed for " + printer.getName());
        }
    }

    /**
     * Handle busy (filament operation) notifications for PrusaLink
     */
    private void handleBusyNotification(Printer printer, String state, String previousStatus, String newStatus) {
        boolean isBusy = "BUSY".equalsIgnoreCase(state);
        boolean wasPaused = "paused".equalsIgnoreCase(previousStatus);
        boolean isPaused = "paused".equalsIgnoreCase(newStatus);

        if (isBusy && isPaused && !wasPaused) {
            notificationService.createPrinterBusyNotification(printer.getName(), printer.getIp());
            System.out.println("   🔔 Filament operation notification created for " + printer.getName());
        } else if (!isPaused && wasPaused) {
            notificationService.autoDismissPrinterBusyNotification(printer.getName());
        }
    }

    /**
     * ✅ Handle nozzle cleaning notifications based on status changes
     */
    private void handleNozzleCleaningNotification(String printerName, String printerIp, String previousStatus, String newStatus) {
        if ("paused".equalsIgnoreCase(newStatus) && !"paused".equalsIgnoreCase(previousStatus)) {
            // Printer just became paused → create notification
            notificationService.createNozzleCleaningNotification(printerName, printerIp);
            System.out.println("   🔔 Nozzle cleaning notification created for " + printerName);
        } else if ("paused".equalsIgnoreCase(previousStatus) && !"paused".equalsIgnoreCase(newStatus)) {
            // Printer is no longer paused → auto-dismiss notification
            notificationService.autoDismissNozzleCleaningNotification(printerName);
            System.out.println("   ✅ Nozzle cleaning notification auto-dismissed for " + printerName);
        }
    }

    private boolean printLabel(String printerName, String filename) {
        if (!labelPrinterEnabled) {
            System.out.println("ℹ️  Label printing is disabled");
            return false;
        }

        try {
            String urlWithParams = UriComponentsBuilder.fromHttpUrl(labelPrinterUrl)
                    .queryParam("first", printerName)
                    .queryParam("last", filename)
                    .queryParam("company", " ")
                    .build()
                    .toUriString();

            System.out.println("📤 Label request → " + urlWithParams);

            ResponseEntity<String> response = restTemplate.getForEntity(urlWithParams, String.class);

            int statusCode = response.getStatusCode().value();
            System.out.println("   Response: HTTP " + statusCode);

            String body = response.getBody();
            if (body != null && !body.isEmpty()) {
                System.out.println("   Body: " + body.substring(0, Math.min(200, body.length())));
            }

            return statusCode >= 200 && statusCode < 300;

        } catch (Exception e) {
            System.out.println("❌ Label error: " + e.getMessage());
            return false;
        }
    }
}