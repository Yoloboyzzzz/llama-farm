package be.ucll.util;

import be.ucll.model.GcodeFile;
import be.ucll.model.Printer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Component
public class PrusaLinkClient {

    private final ObjectMapper mapper = new ObjectMapper();

    // Timeouts in milliseconds
    private static final int CONNECTION_TIMEOUT = 10000;  // 10 seconds to connect
    private static final int SOCKET_TIMEOUT = 120000;     // 120 seconds to upload (2 minutes)

    // --------------------------------------------------------------------
    // PUBLIC ENTRYPOINT: Clear USB + Upload + Start Print
    // --------------------------------------------------------------------
    public boolean uploadToPrusaLinkAndPrint(Printer printer, GcodeFile gcodeFile) {
        System.out.println("━".repeat(60));
        System.out.println("PRUSALINK UPLOAD & PRINT");
        System.out.println("━".repeat(60));
        
        try {
            String ip = normalizeIp(printer.getIp());
            File file = new File(gcodeFile.getPath());

            System.out.println("Printer: " + printer.getName() + " (" + ip + ")");
            System.out.println("File: " + gcodeFile.getName());
            System.out.println("Path: " + file.getAbsolutePath());
            
            if (!file.exists()) {
                System.out.println("❌ File does not exist!");
                return false;
            }
            System.out.println("✓ File exists (" + String.format("%,d", file.length()) + " bytes)");
            System.out.println();

            // 1️⃣ Clear USB storage first
            System.out.println("🗑️  Step 1: Clearing USB storage...");
            int deleted = clearUsbStorage(printer, ip);
            System.out.println("   Deleted: " + deleted + " files");
            System.out.println();

            // Generate safe filename (no spaces)
            String safeFilename = gcodeFile.getName().replace(" ", "_");
            System.out.println("📝 Safe filename: " + safeFilename);
            System.out.println();

            // 2️⃣ Upload file to USB
            System.out.println("📤 Step 2: Uploading to USB (timeout: " + (SOCKET_TIMEOUT/1000) + "s)...");
            if (!uploadFile(printer, ip, file, safeFilename)) {
                System.out.println("❌ Upload failed - STOPPING");
                System.out.println("━".repeat(60));
                return false;
            }
            System.out.println("✅ Upload successful");
            System.out.println();

            // 3️⃣ Wait for file to be ready
            System.out.println("⏳ Waiting 3 seconds for file to be ready...");
            Thread.sleep(3000);
            System.out.println();

            // 4️⃣ Start print
            System.out.println("▶️  Step 3: Starting print...");
            if (!startPrint(printer, ip, safeFilename)) {
                System.out.println("❌ Could not start print");
                System.out.println("━".repeat(60));
                return false;
            }

            System.out.println("✅ Print started successfully!");
            System.out.println("━".repeat(60));
            return true;

        } catch (Exception e) {
            System.out.println("❌ Exception: " + e.getMessage());
            e.printStackTrace();
            System.out.println("━".repeat(60));
            return false;
        }
    }

    // --------------------------------------------------------------------
    // ABORT PRINT
    // --------------------------------------------------------------------
    public boolean abortPrint(Printer printer) {
        try (CloseableHttpClient client = createHttpClient(10000)) {

            String ip = normalizeIp(printer.getIp());
            String url = ip + "/api/v1/job";

            HttpDelete delete = new HttpDelete(url);
            delete.addHeader("X-Api-Key", printer.getApiKey());

            CloseableHttpResponse response = client.execute(delete);
            int code = response.getStatusLine().getStatusCode();

            if (code == 204 || code == 200) {
                System.out.println("✅ Print aborted on " + printer.getName());
                return true;
            } else {
                System.out.println("❌ Abort failed: HTTP " + code);
                return false;
            }

        } catch (Exception e) {
            System.out.println("❌ Abort error: " + e.getMessage());
            return false;
        }
    }

    // --------------------------------------------------------------------
    // Create HTTP client with custom timeouts
    // --------------------------------------------------------------------
    private CloseableHttpClient createHttpClient(int socketTimeout) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT)
                .setSocketTimeout(socketTimeout)
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    // --------------------------------------------------------------------
    // Normalize IP
    // --------------------------------------------------------------------
    private String normalizeIp(String ip) {
        ip = ip.trim();
        ip = ip.replace("http://", "")
                .replace("https://", "")
                .replace("http/", "");
        return "http://" + ip;
    }

    // --------------------------------------------------------------------
    // CLEAR USB STORAGE
    // --------------------------------------------------------------------
    private int clearUsbStorage(Printer printer, String ip) {
        try (CloseableHttpClient client = createHttpClient(10000)) {

            String listUrl = ip + "/api/files/usb";
            HttpGet get = new HttpGet(listUrl);
            get.addHeader("X-Api-Key", printer.getApiKey());

            System.out.println("   Listing: " + listUrl);
            CloseableHttpResponse response = client.execute(get);
            String json = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            
            System.out.println("   HTTP " + statusCode);

            if (statusCode != 200) {
                return 0;
            }

            JsonNode root = mapper.readTree(json);
            JsonNode filesArray = root.path("files");

            if (!filesArray.isArray() || filesArray.size() == 0) {
                System.out.println("   No files");
                return 0;
            }

            int deletedCount = 0;

            for (JsonNode item : filesArray) {
                if ("folder".equals(item.path("type").asText())) {
                    JsonNode children = item.path("children");
                    if (children.isArray()) {
                        for (JsonNode child : children) {
                            String filename = child.path("name").asText(null);
                            if (filename != null && deleteFile(printer, ip, filename)) {
                                deletedCount++;
                            }
                        }
                    }
                } else {
                    String filename = item.path("name").asText(null);
                    if (filename != null && deleteFile(printer, ip, filename)) {
                        deletedCount++;
                    }
                }
            }

            return deletedCount;

        } catch (Exception e) {
            System.out.println("   Error: " + e.getMessage());
            return 0;
        }
    }

    // --------------------------------------------------------------------
    // DELETE SINGLE FILE
    // --------------------------------------------------------------------
    private boolean deleteFile(Printer printer, String ip, String filename) {
        try (CloseableHttpClient client = createHttpClient(10000)) {

            String url = ip + "/api/files/usb/" + filename;
            HttpDelete delete = new HttpDelete(url);
            delete.addHeader("X-Api-Key", printer.getApiKey());

            CloseableHttpResponse response = client.execute(delete);
            int code = response.getStatusLine().getStatusCode();

            System.out.println("   Delete " + filename + ": HTTP " + code);
            return code == 200 || code == 204;

        } catch (Exception e) {
            return false;
        }
    }

    // --------------------------------------------------------------------
    // UPLOAD FILE TO USB
    // --------------------------------------------------------------------
    private boolean uploadFile(Printer printer, String ip, File file, String filename) {
        // Use LONGER timeout for upload (2 minutes)
        try (CloseableHttpClient client = createHttpClient(SOCKET_TIMEOUT)) {

            String url = ip + "/api/v1/files/usb/" + filename;

            HttpPut put = new HttpPut(url);
            put.addHeader("X-Api-Key", printer.getApiKey());
            put.addHeader("Content-Type", "text/x.gcode");
            put.addHeader("Print-After-Upload", "0");
            put.addHeader("Overwrite", "true");

            byte[] fileContent = Files.readAllBytes(file.toPath());
            
            System.out.println("   URL: " + url);
            System.out.println("   Size: " + String.format("%,d", fileContent.length) + " bytes");

            ByteArrayEntity entity = new ByteArrayEntity(fileContent, ContentType.create("text/x.gcode"));
            put.setEntity(entity);

            System.out.println("   Sending... (this may take a while)");
            CloseableHttpResponse response = client.execute(put);
            int code = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            System.out.println("   Response: HTTP " + code);

            if (code == 200 || code == 201 || code == 204) {
                System.out.println("   ✓ Success");
                return true;
            }

            if (code == 409) {
                System.out.println("   Retrying with overwrite...");
                
                HttpPut put2 = new HttpPut(url + "?overwrite=true");
                put2.addHeader("X-Api-Key", printer.getApiKey());
                put2.addHeader("Content-Type", "text/x.gcode");
                put2.addHeader("Print-After-Upload", "false");
                put2.addHeader("overwrite", "true");
                put2.setEntity(new ByteArrayEntity(fileContent, ContentType.create("text/x.gcode")));

                CloseableHttpResponse response2 = client.execute(put2);
                int code2 = response2.getStatusLine().getStatusCode();
                
                System.out.println("   Retry: HTTP " + code2);
                
                if (code2 == 200 || code2 == 201 || code2 == 204) {
                    System.out.println("   ✓ Success on retry");
                    return true;
                }
            }

            System.out.println("   ❌ Failed");
            System.out.println("   Body: " + responseBody.substring(0, Math.min(300, responseBody.length())));
            return false;

        } catch (IOException e) {
            System.out.println("   ❌ Exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // --------------------------------------------------------------------
    // START PRINT
    // --------------------------------------------------------------------
    private boolean startPrint(Printer printer, String ip, String filename) {
        
        System.out.println("   File: " + filename);
        
        try (CloseableHttpClient client = createHttpClient(10000)) {

            String url = ip + "/api/v1/files/usb/" + filename;

            HttpPost post = new HttpPost(url);
            post.addHeader("X-Api-Key", printer.getApiKey());
            
            // Add empty entity to prevent "Entity may not be null" error
            post.setEntity(new ByteArrayEntity(new byte[0], ContentType.APPLICATION_JSON));

            System.out.println("   POST: " + url);
            
            CloseableHttpResponse response = client.execute(post);
            int code = response.getStatusLine().getStatusCode();
            
            // EntityUtils.toString can throw if entity is null, so check first
            String body = "";
            if (response.getEntity() != null) {
                body = EntityUtils.toString(response.getEntity());
            }

            System.out.println("   Response: HTTP " + code);
            
            if (!body.isEmpty()) {
                System.out.println("   Body: " + body.substring(0, Math.min(200, body.length())));
            }

            if (code == 200 || code == 201 || code == 204) {
                System.out.println("   ✓ Started");
                return true;
            }
            
            System.out.println("   ❌ Failed to start (unexpected code)");
            return false;

        } catch (Exception e) {
            System.out.println("   ❌ Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // --------------------------------------------------------------------
    // GET JOB STATUS
    // --------------------------------------------------------------------
    public JobStatus getJobStatus(Printer printer) {
        try (CloseableHttpClient client = createHttpClient(10000)) {

            String ip = normalizeIp(printer.getIp());
            String url = ip + "/api/v1/status";

            HttpGet get = new HttpGet(url);
            get.addHeader("X-Api-Key", printer.getApiKey());

            CloseableHttpResponse response = client.execute(get);

            if (response.getStatusLine().getStatusCode() == 200) {
                String json = EntityUtils.toString(response.getEntity());
                JsonNode root = mapper.readTree(json);

                JsonNode printerInfo = root.path("printer");
                JsonNode jobInfo = root.path("job");

                return new JobStatus(
                    printerInfo.path("state").asText("Unknown"),
                    jobInfo.path("progress").asDouble(0.0),
                    jobInfo.path("time_printing").asInt(0),
                    jobInfo.path("time_remaining").asInt(0)
                );
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    // --------------------------------------------------------------------
    // JobStatus inner class
    // --------------------------------------------------------------------
    public static class JobStatus {
        public String state;
        public double progress;
        public int timePrinting;
        public int timeRemaining;

        public JobStatus(String state, double progress, int timePrinting, int timeRemaining) {
            this.state = state;
            this.progress = progress;
            this.timePrinting = timePrinting;
            this.timeRemaining = timeRemaining;
        }

        public boolean isComplete() {
            String stateLower = state.toLowerCase();
            return (stateLower.equals("finished") || stateLower.equals("idle") || stateLower.equals("ready")) 
                && (progress >= 99.0 || timeRemaining == 0);
        }

        public boolean needsAttention() {
            String stateLower = state.toLowerCase();
            return stateLower.equals("error") || stateLower.equals("stopped") 
                || stateLower.equals("cancelled") || stateLower.equals("attention");
        }

        @Override
        public String toString() {
            return String.format("State: %s | Progress: %.1f%% | Elapsed: %ds | Remaining: %ds",
                state, progress, timePrinting, timeRemaining);
        }
    }
}