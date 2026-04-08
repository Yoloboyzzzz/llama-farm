package be.ucll.util;

import be.ucll.model.GcodeFile;
import be.ucll.model.Printer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OctoPrintClient {

    private final ObjectMapper mapper = new ObjectMapper();


    public boolean abortPrint(Printer printer) {

    try (CloseableHttpClient client = HttpClients.createDefault()) {

        String ip = printer.getIp().trim();
        if (!ip.startsWith("http://")) ip = "http://" + ip;

        String url = ip + "/api/job";

        HttpPost post = new HttpPost(url);
        post.addHeader("X-Api-Key", printer.getApiKey());
        post.addHeader("Content-Type", "application/json");

        String json = """
        {
            "command": "cancel"
        }
        """;

        post.setEntity(new StringEntity(json));

        CloseableHttpResponse response = client.execute(post);

        int code = response.getStatusLine().getStatusCode();

        if (code == 204) {
            System.out.println("✅ Print aborted successfully on " + printer.getName());
            return true;
        } else {
            System.out.println("❌ Abort failed: " + code);
            System.out.println(EntityUtils.toString(response.getEntity()));
            return false;
        }

    } catch (Exception e) {
        System.out.println("❌ Abort exception: " + e.getMessage());
        return false;
    }
}


    // --------------------------------------------------------------------
    // PUBLIC ENTRYPOINT: Upload + Select + Start Print
    // --------------------------------------------------------------------
    public boolean uploadToOctoPrintAndPrint(Printer printer, GcodeFile gcodeFile) {

        try {
            String ip = normalizeIp(printer.getIp());
            File file = new File(gcodeFile.getPath());

            if (!file.exists()) {
                System.out.println("❌ File does not exist: " + file.getAbsolutePath());
                return false;
            }

            // Generate safe server filename (no spaces)
            String newServerName = gcodeFile.getName().replace(" ", "_");

            // 1️⃣ Upload file with renamed name
            String uploadedName = uploadFile(printer, ip, file, newServerName);
            if (uploadedName == null) {
                System.out.println("❌ Upload failed.");
                return false;
            }

            System.out.println("Uploaded as: " + uploadedName);

            // 2️⃣ Select file
            if (!selectFile(printer, ip, uploadedName)) {
                System.out.println("❌ Selecting file failed.");
                return false;
            }

            // 3️⃣ Start print
            if (!startPrint(printer, ip, uploadedName)) {
                System.out.println("❌ Starting print failed.");
                return false;
            }

            return true;

        } catch (Exception e) {
            System.out.println("❌ OctoPrint error: " + e.getMessage());
            return false;
        }
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
    // 1️⃣ UPLOAD FILE (with rename)
    // --------------------------------------------------------------------
    private String uploadFile(Printer printer, String ip, File file, String newName) {

        String url = ip + "/api/files/local";

        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpPost post = new HttpPost(url);
            post.addHeader("X-Api-Key", printer.getApiKey());

            // Safe server name
            String safeNewName = newName.replace(" ", "_");

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            // File body (binary)
            builder.addBinaryBody(
                    "file",
                    file,
                    ContentType.APPLICATION_OCTET_STREAM,
                    file.getName()
            );

            // Filename override
            builder.addTextBody(
                    "filename",
                    safeNewName,
                    ContentType.TEXT_PLAIN
            );

            post.setEntity(builder.build());

            CloseableHttpResponse response = client.execute(post);
            String json = EntityUtils.toString(response.getEntity());

            if (response.getStatusLine().getStatusCode() != 201) {
                System.out.println("❌ Upload failed: " + response.getStatusLine());
                System.out.println(json);
                return null;
            }

            JsonNode root = mapper.readTree(json);
            return root.path("files").path("local").path("name").asText(null);

        } catch (Exception e) {
            System.out.println("❌ Upload exception: " + e.getMessage());
            return null;
        }
    }

    // --------------------------------------------------------------------
    // 2️⃣ SELECT FILE
    // --------------------------------------------------------------------
    private boolean selectFile(Printer printer, String ip, String fileName) {

        try (CloseableHttpClient client = HttpClients.createDefault()) {

            // MUST URL-encode the filename
            String safeName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

            String url = ip + "/api/files/local/" + safeName;

            HttpPost post = new HttpPost(url);
            post.addHeader("X-Api-Key", printer.getApiKey());
            post.addHeader("Content-Type", "application/json");

            String json = """
            {
              "command": "select",
              "print": false
            }
            """;

            post.setEntity(new StringEntity(json));

            CloseableHttpResponse response = client.execute(post);

            return response.getStatusLine().getStatusCode() == 204;

        } catch (Exception e) {
            System.out.println("❌ Select exception: " + e.getMessage());
            return false;
        }
    }

    // --------------------------------------------------------------------
    // 3️⃣ START PRINT
    // --------------------------------------------------------------------
    private boolean startPrint(Printer printer, String ip, String fileName) {

        try (CloseableHttpClient client = HttpClients.createDefault()) {

            // MUST URL-encode
            String safeName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

            String url = ip + "/api/job";

            HttpPost post = new HttpPost(url);
            post.addHeader("X-Api-Key", printer.getApiKey());
            post.addHeader("Content-Type", "application/json");

            String json = """
            {
              "command": "start",
              "file": "local/%s"
            }
            """.formatted(safeName);

            post.setEntity(new StringEntity(json));

            CloseableHttpResponse response = client.execute(post);

            return response.getStatusLine().getStatusCode() == 204;

        } catch (Exception e) {
            System.out.println("❌ Start print exception: " + e.getMessage());
            return false;
        }
    }
}
