package com.example.berexplorer;

import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class BerDecoder {
    private byte[] data;

    public TlvNode decodeSingle(byte[] bytes) throws BerDecodeException {
        this.data = bytes;
        if (bytes.length == 0) throw new BerDecodeException("Input is empty.");
        Cursor c = new Cursor(0, this.data);
        TlvNode node = readNode(c, bytes.length);
        if (c.pos != bytes.length) {
            throw error(c.pos, "Trailing bytes after the top-level TLV (" + (bytes.length - c.pos) + " bytes).");
        }
        return node;
    }

    private TlvNode readNode(Cursor c, int limit) throws BerDecodeException {
        int start = c.pos;
        if (c.pos >= limit) throw error(c.pos, "Unexpected end of input while reading tag.");

        int first = u8(c.get());
        TlvNode.TagClass tagClass = switch (first >>> 6) {
            case 0 -> TlvNode.TagClass.UNIVERSAL;
            case 1 -> TlvNode.TagClass.APPLICATION;
            case 2 -> TlvNode.TagClass.CONTEXT_SPECIFIC;
            default -> TlvNode.TagClass.PRIVATE;
        };
        boolean constructed = (first & 0x20) != 0;
        int tag = first & 0x1F;
        if (tag == 0x1F) {
            tag = 0;
            boolean terminated = false;
            for (int count = 0; count < 5; count++) {
                if (c.pos >= limit) throw error(c.pos, "Truncated high-tag-number.");
                int b = u8(c.get());
                if ((tag & 0xFE000000) != 0) throw error(c.pos - 1, "Tag number is too large.");
                tag = (tag << 7) | (b & 0x7F);
                if ((b & 0x80) == 0) { terminated = true; break; }
            }
            if (!terminated) throw error(c.pos, "High-tag-number did not terminate.");
        }

        if (c.pos >= limit) throw error(c.pos, "Missing length octet.");
        int firstLength = u8(c.get());
        boolean indefinite = firstLength == 0x80;
        long length;
        int lengthOctets = 1;
        if (indefinite) {
            if (!constructed) throw error(c.pos - 1, "Indefinite length is only valid for constructed BER values.");
            length = -1;
        } else if ((firstLength & 0x80) == 0) {
            length = firstLength;
        } else {
            int count = firstLength & 0x7F;
            if (count == 0) throw error(c.pos - 1, "Invalid reserved length form.");
            if (count > 8) throw error(c.pos - 1, "Length uses more than 8 octets.");
            if (c.pos + count > limit) throw error(c.pos, "Truncated long-form length.");
            long valueLength = 0;
            for (int i = 0; i < count; i++) valueLength = (valueLength << 8) | u8(c.get());
            length = valueLength;
            lengthOctets += count;
        }

        int headerLength = c.pos - start;
        int valueOffset = c.pos;
        TlvNode node;
        if (indefinite) {
            int childrenStart = c.pos;
            while (true) {
                if (c.pos + 2 > limit) throw error(c.pos, "Missing end-of-contents marker for indefinite-length value.");
                if (u8(data[c.pos]) == 0 && u8(data[c.pos + 1]) == 0) {
                    int contentEnd = c.pos;
                    c.pos += 2;
                    byte[] value = Arrays.copyOfRange(data, childrenStart, contentEnd);
                    node = new TlvNode(start, tagClass, constructed, tag, headerLength, -1, true,
                            valueOffset, c.pos - start, value);
                    parseChildren(node, childrenStart, contentEnd);
                    return node;
                }
                readNode(c, limit);
            }
        }

        if (length > Integer.MAX_VALUE) throw error(start, "TLV length is too large for this application.");
        long endLong = (long) c.pos + length;
        if (endLong > limit) throw error(c.pos, "TLV value extends beyond the available input. Declared length=" + length + ".");
        int end = (int) endLong;
        byte[] value = Arrays.copyOfRange(data, valueOffset, end);
        c.pos = end;
        node = new TlvNode(start, tagClass, constructed, tag, headerLength, length, false,
                valueOffset, c.pos - start, value);
        if (constructed) parseChildren(node, valueOffset, end);
        return node;
    }

    private void parseChildren(TlvNode parent, int start, int end) throws BerDecodeException {
        Cursor c = new Cursor(start, this.data);
        while (c.pos < end) {
            int before = c.pos;
            TlvNode child = readNode(c, end);
            parent.children().add(child);
            if (c.pos <= before) throw error(c.pos, "Parser made no progress.");
        }
        if (c.pos != end) throw error(c.pos, "Constructed value does not contain complete child TLVs.");
    }

    public static String displayValue(TlvNode node) {
        byte[] v = node.value();
        if (node.constructed()) return "Constructed (" + node.children().size() + " children)";
        return switch (node.tagClass() == TlvNode.TagClass.UNIVERSAL ? node.tagNumber() : -1) {
            case 1 -> v.length == 1 ? String.valueOf(v[0] != 0) : "invalid BOOLEAN";
            case 2 -> integerValue(v);
            case 4 -> printableBytes(v);
            case 10 -> integerValue(v);
            case 12, 18, 19, 20, 21, 22, 25, 26, 27, 30 -> new String(v, node.tagNumber() == 30 ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_8);
            case 23, 24 -> new String(v, StandardCharsets.US_ASCII);
            case 5 -> v.length == 0 ? "NULL" : "invalid NULL";
            case 3 -> v.length == 0 ? "" : "unused-bits=" + (v[0] & 0xff) + ", " + HexUtils.hex(Arrays.copyOfRange(v, 1, v.length), 128);
            default -> HexUtils.hex(v, 128);
        };
    }

    private static String integerValue(byte[] v) {
        if (v.length == 0) return "invalid/empty integer";
        if (v.length <= 8) {
            long n = 0;
            for (byte b : v) n = (n << 8) | (b & 0xffL);
            if ((v[0] & 0x80) != 0 && v.length < 8) n -= 1L << (v.length * 8);
            return Long.toString(n);
        }
        return "0x" + HexUtils.hex(v, 128).replace(" ", "");
    }

    private static String printableBytes(byte[] v) {
        boolean printable = true;
        for (byte b : v) {
            int x = b & 0xff;
            if (x != 9 && x != 10 && x != 13 && (x < 32 || x > 126)) { printable = false; break; }
        }
        return printable ? "\"" + new String(v, StandardCharsets.UTF_8) + "\"" : HexUtils.hex(v, 128);
    }

    private static int u8(byte b) { return b & 0xff; }
    private static BerDecodeException error(int offset, String message) { return new BerDecodeException("Offset " + offset + ": " + message); }

    private static final class Cursor {
        int pos;
        private final byte[] data;
        Cursor(int pos, byte[] data) { this.pos = pos; this.data = data; }
        byte get() { return data[pos++]; }
    }

    public static final class BerDecodeException extends Exception {
        public BerDecodeException(String message) { super(message); }
    }
}
