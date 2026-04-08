package be.ucll.slicer.cli;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import java.util.*;

public class ThreeMFModifier {

    public Path modify3MF(Path threeMfPath,
                          Integer infill,
                          Integer brimWidth,
                          Boolean supports,
                          Boolean supportBuildplateOnly) {

        try {
            Path tempDir = Files.createTempDirectory("three_mf_edit_");
            unzip(threeMfPath, tempDir);

            // Find *.config under Metadata/
            Path metadataDir = tempDir.resolve("Metadata");

            if (!Files.exists(metadataDir)) {
                throw new RuntimeException("Metadata folder missing in 3MF: " + threeMfPath);
            }

            Path config = Files.list(metadataDir)
                    .filter(p -> p.toString().endsWith(".config"))
                    .findFirst()
                    .orElseThrow(() ->
                            new RuntimeException("No *.config found inside 3MF Metadata folder"));

            // Modify the XML inside that config
            applyMetadata(config, infill, brimWidth, supports, supportBuildplateOnly);

            // Repack ZIP
            Path finalFile = threeMfPath; // overwrite original

            zip(tempDir, finalFile);

            // Cleanup
            deleteDirectory(tempDir);

            return finalFile;

        } catch (Exception e) {
            throw new RuntimeException("Failed modifying 3MF metadata: " + e.getMessage(), e);
        }
    }

    private void applyMetadata(Path config,
                               Integer infill,
                               Integer brim,
                               Boolean supports,
                               Boolean supportsBuildplateOnly)
            throws Exception {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc;

        try (InputStream is = Files.newInputStream(config)) {
            doc = db.parse(is);
        }

        doc.getDocumentElement().normalize();

        // We'll write all object metadata inside <model> or <object> node.
        // Typical Prusa 3MF has model settings under "/config/object"
        // But safer to search:
        NodeList objects = doc.getElementsByTagName("object");
        Element objRoot;

        if (objects.getLength() > 0) {
            objRoot = (Element) objects.item(0);
        } else {
            NodeList metaNodes = doc.getElementsByTagName("metadata");
            if (metaNodes.getLength() > 0) {
                objRoot = (Element) metaNodes.item(0).getParentNode();
            } else {
                objRoot = doc.getDocumentElement();
            }
        }

        // Insert metadata
        insertOrUpdate(doc, objRoot, "fill_density", infill + "%");
        insertOrUpdate(doc, objRoot, "brim_width", String.valueOf(brim));

        // Support flags
        insertOrUpdate(doc, objRoot, "support_material", supports ? "1" : "0");
        insertOrUpdate(doc, objRoot, "support_material_buildplate_only",
                supportsBuildplateOnly ? "1" : "0");

        // Write back
        Transformer tf = TransformerFactory.newInstance().newTransformer();
        tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        tf.setOutputProperty(OutputKeys.INDENT, "yes");

        try (OutputStream os = Files.newOutputStream(config)) {
            tf.transform(new DOMSource(doc), new StreamResult(os));
        }
    }

    private void insertOrUpdate(Document doc, Element parent, String key, String value) {
        NodeList items = parent.getElementsByTagName("metadata");

        for (int i = 0; i < items.getLength(); i++) {
            Element m = (Element) items.item(i);

            if (m.getAttribute("key").equals(key)) {
                m.setAttribute("value", value);
                m.setTextContent(value);
                return;
            }
        }

        Element meta = doc.createElement("metadata");
        meta.setAttribute("type", "object");
        meta.setAttribute("key", key);
        meta.setAttribute("value", value);
        meta.setTextContent(value);

        parent.appendChild(meta);
    }

    private void unzip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = destDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void zip(Path sourceDir, Path outputZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZip.toFile()))) {
            Files.walk(sourceDir).forEach(path -> {
                if (Files.isRegularFile(path)) {
                    String zipEntry = sourceDir.relativize(path).toString().replace("\\", "/");
                    try (InputStream is = Files.newInputStream(path)) {
                        zos.putNextEntry(new ZipEntry(zipEntry));
                        is.transferTo(zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;

        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception ignored) {
                    }
                });
    }
}
