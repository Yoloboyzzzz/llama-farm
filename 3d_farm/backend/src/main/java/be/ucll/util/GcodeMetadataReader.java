package be.ucll.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GcodeMetadataReader {

    public static class GcodeInfo {
        public int durationSeconds;
        public int filamentUsedGrams;
        public String printerModel;
        public String filamentType;
    }

    public static GcodeInfo extractInfo(String filePath) {
        GcodeInfo info = new GcodeInfo();

        // ⏱ Estimated print time (e.g. "; estimated printing time (normal mode) = 19h 43m 12s")
        Pattern timePattern = Pattern.compile(";\\s*estimated printing time.*?=\\s*([0-9dhms\\s]+)", Pattern.CASE_INSENSITIVE);

        // 🧵 Filament used (grams)
        Pattern filamentUsedPattern = Pattern.compile(";\\s*total filament used \\[g\\]\\s*=\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);

        // 🖨️ Printer model (e.g. M862.3 P "MK3S")
        Pattern modelPattern = Pattern.compile("M862\\.3\\s+P\\s+\"?([^\"]+)\"?", Pattern.CASE_INSENSITIVE);

        // 🧶 Filament type (e.g. "; filament_type = PLA" or "M862.4 P \"PETG\"")
        Pattern filamentTypePattern = Pattern.compile("(?:;\\s*filament_type|M862\\.4\\s+P)\\s*=?\\s*\"?([A-Za-z0-9_\\-\\+]+)\"?", Pattern.CASE_INSENSITIVE);

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                // ✅ Clean the line: remove BOM + control characters
                line = line.trim()
                           .replace("\uFEFF", "")
                           .replaceAll("\\p{C}", "");

                // ⏱ Estimated time (only first match)
                Matcher timeMatcher = timePattern.matcher(line);
                if (timeMatcher.find() && info.durationSeconds == 0) {
                    info.durationSeconds = parseDurationToSeconds(timeMatcher.group(1).trim()) + 300;
                }

                // 🧵 Filament used (only first match)
                Matcher filamentUsedMatcher = filamentUsedPattern.matcher(line);
                if (filamentUsedMatcher.find() && info.filamentUsedGrams == 0) {
                    double filament = Double.parseDouble(filamentUsedMatcher.group(1));
                    info.filamentUsedGrams = (int) Math.ceil(filament);
                    System.out.println(info.filamentUsedGrams);
                }

                // 🖨️ Printer model — only first occurrence
                if (info.printerModel == null) {
                    Matcher modelMatcher = modelPattern.matcher(line);
                    if (modelMatcher.find()) {
                        info.printerModel = modelMatcher.group(1).trim();
                    }
                }

                // 🧶 Filament type — only first occurrence
                if (info.filamentType == null) {
                    Matcher filamentTypeMatcher = filamentTypePattern.matcher(line);
                    if (filamentTypeMatcher.find()) {
                        info.filamentType = filamentTypeMatcher.group(1).trim();
                    }
                }

                // ✅ Optional: stop early once all info is found
                if (info.printerModel != null &&
                    info.filamentType != null &&
                    info.filamentUsedGrams > 0 &&
                    info.durationSeconds > 0) {
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("⚠️ Error reading G-code metadata from " + filePath + ": " + e.getMessage());
        }

        return info;
    }

    // ⏳ Convert "1d 2h 3m 4s" → total seconds
    private static int parseDurationToSeconds(String timeText) {
        int totalSeconds = 0;
        Pattern numberUnit = Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = numberUnit.matcher(timeText);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            switch (matcher.group(2).toLowerCase()) {
                case "d" -> totalSeconds += value * 86400;
                case "h" -> totalSeconds += value * 3600;
                case "m" -> totalSeconds += value * 60;
                case "s" -> totalSeconds += value;
            }
        }
        return totalSeconds;
    }
}
