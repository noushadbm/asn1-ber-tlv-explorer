package com.example.berexplorer;

public final class HexUtils {
    private HexUtils() {}

    public static byte[] parseHex(String text) {
        String normalized = text.replaceAll("(?i)0x", "").replaceAll("\\s+", "").replaceAll("[-:,]", "");
        if (normalized.isEmpty()) return new byte[0];
        if ((normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex input contains an odd number of digits.");
        }
        byte[] result = new byte[normalized.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int hi = Character.digit(normalized.charAt(i * 2), 16);
            int lo = Character.digit(normalized.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hexadecimal character near position " + (i * 2) + ".");
            }
            result[i] = (byte) ((hi << 4) | lo);
        }
        return result;
    }

    public static String hex(byte[] bytes, int maxBytes) {
        int n = Math.min(bytes.length, maxBytes);
        StringBuilder sb = new StringBuilder(n * 3);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes[i] & 0xff));
        }
        if (n < bytes.length) sb.append(" …");
        return sb.toString();
    }

    public static String asciiPreview(byte[] bytes, int maxBytes) {
        int n = Math.min(bytes.length, maxBytes);
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            int b = bytes[i] & 0xff;
            sb.append(b >= 32 && b <= 126 ? (char) b : '.');
        }
        if (n < bytes.length) sb.append(" …");
        return sb.toString();
    }
}
