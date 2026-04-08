package be.ucll.slicer.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class StlDimensionsExtractor {

    public static class Dimensions {
        public double widthMm;
        public double depthMm;
        public double heightMm;

        @Override
        public String toString() {
            return String.format(
                "W=%.3f mm, D=%.3f mm, H=%.3f mm",
                widthMm, depthMm, heightMm
            );
        }
    }

    public static Dimensions readDimensions(String filePath) throws IOException {
        File file = new File(filePath);

        if (isAsciiStl(file)) {
            return readAscii(file);
        } else {
            return readBinary(file);
        }
    }

    /**
     * Robust STL detection:
     * - Binary STLs can legally start with "solid"
     * - We detect ASCII by actually finding vertex lines
     */
    private static boolean isAsciiStl(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int vertexLines = 0;

            while ((line = br.readLine()) != null && vertexLines < 5) {
                line = line.trim();
                if (line.startsWith("vertex")) {
                    vertexLines++;
                }
            }
            return vertexLines > 0;
        }
    }

    private static Dimensions readAscii(File file) throws IOException {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("vertex")) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 4) {
                        double x = Double.parseDouble(p[1].replace(",", "."));
                        double y = Double.parseDouble(p[2].replace(",", "."));
                        double z = Double.parseDouble(p[3].replace(",", "."));

                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);
                    }
                }
            }
        }

        return buildDimensions(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private static Dimensions readBinary(File file) throws IOException {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {

            // 80-byte header
            dis.skipBytes(80);

            // Triangle count (little endian uint32)
            int triCount = Integer.reverseBytes(dis.readInt());

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

            byte[] triangle = new byte[50];

            for (int i = 0; i < triCount; i++) {
                dis.readFully(triangle);
                ByteBuffer b = ByteBuffer.wrap(triangle).order(ByteOrder.LITTLE_ENDIAN);

                // Skip normal vector (3 floats)
                b.position(12);

                for (int v = 0; v < 3; v++) {
                    float x = b.getFloat();
                    float y = b.getFloat();
                    float z = b.getFloat();

                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
            }

            return buildDimensions(minX, maxX, minY, maxY, minZ, maxZ);
        }
    }

    private static Dimensions buildDimensions(
            double minX, double maxX,
            double minY, double maxY,
            double minZ, double maxZ) {

        Dimensions d = new Dimensions();
        d.widthMm  = maxX - minX;
        d.depthMm  = maxY - minY;
        d.heightMm = maxZ - minZ;

        return d;
    }
}
