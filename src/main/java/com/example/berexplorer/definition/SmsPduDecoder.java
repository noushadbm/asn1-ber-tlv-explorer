package com.example.berexplorer.definition;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SmsPduDecoder {
    private static final Pattern CONTENT_LENGTH = Pattern.compile("(?im)^Content-Length\\s*:\\s*(\\d+)\\s*$");
    private static final char[] GSM7 = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà".toCharArray();

    private SmsPduDecoder() {}

    static boolean hasPositiveContentLength(byte[] sipMessage) {
        Matcher matcher = CONTENT_LENGTH.matcher(new String(sipMessage, StandardCharsets.ISO_8859_1));
        return matcher.find() && Integer.parseInt(matcher.group(1)) > 0;
    }

    static String decode(byte[] sipMessage) {
        String headers = new String(sipMessage, StandardCharsets.ISO_8859_1);
        Matcher lengthMatcher = CONTENT_LENGTH.matcher(headers);
        if (!lengthMatcher.find()) return "Content-Length: not present";
        int contentLength = Integer.parseInt(lengthMatcher.group(1));
        int bodyOffset = headers.indexOf("\r\n\r\n");
        if (bodyOffset < 0) bodyOffset = headers.indexOf("\n\n");
        if (bodyOffset < 0) return "Content-Length: " + contentLength + "\nSMS PDU: SIP body separator not found";
        bodyOffset += sipMessage[bodyOffset] == '\r' ? 4 : 2;
        int available = sipMessage.length - bodyOffset;
        int actualLength = Math.min(contentLength, Math.max(available, 0));
        byte[] pdu = new byte[actualLength];
        System.arraycopy(sipMessage, bodyOffset, pdu, 0, actualLength);

        StringBuilder out = new StringBuilder();
        out.append("Content-Length: ").append(contentLength).append(" bytes\n");
        out.append("PDU: ").append(hex(pdu)).append('\n');
        if (actualLength != contentLength) {
            out.append("SMS PDU: truncated (available ").append(actualLength).append(" bytes)" );
            return out.toString();
        }
        try {
            parseRpData(pdu, out);
        } catch (IllegalArgumentException ex) {
            out.append("SMS PDU: ").append(ex.getMessage());
        }
        return out.toString();
    }

    private static void parseRpData(byte[] pdu, StringBuilder out) {
        if (pdu.length < 4) throw new IllegalArgumentException("too short for RP-DATA");
        int messageType = u8(pdu[0]);
        if (messageType != 0) throw new IllegalArgumentException("unsupported RP message type 0x" + hexByte(messageType));
        int pos = 1;
        int messageReference = u8(pdu[pos++]);
        int destinationLength = u8(pdu[pos++]);
        pos = require(pdu, pos, destinationLength, "RP destination address");
        int originatorLength = u8(pdu[pos++]);
        pos = require(pdu, pos, originatorLength, "RP originator address");
        if (pos >= pdu.length) throw new IllegalArgumentException("missing RP user-data length");
        int userDataLength = u8(pdu[pos++]);
        if (userDataLength > pdu.length - pos) throw new IllegalArgumentException("RP user-data length exceeds payload");
        out.append("RP-DATA: message reference ").append(messageReference)
                .append(", user data ").append(userDataLength).append(" bytes\n");
        parseTpdu(pdu, pos, userDataLength, out);
    }

    private static void parseTpdu(byte[] pdu, int start, int length, StringBuilder out) {
        int end = start + length;
        if (end - start < 2) throw new IllegalArgumentException("too short for SMS TPDU");
        int pos = start;
        int first = u8(pdu[pos++]);
        int mti = first & 3;
        out.append("TPDU: ").append(switch (mti) { case 0 -> "SMS-DELIVER"; case 1 -> "SMS-SUBMIT"; case 2 -> "SMS-STATUS-REPORT"; default -> "UNKNOWN"; })
                .append(" (first octet 0x").append(hexByte(first)).append(")\n");
        if (mti == 1) {
            if (pos >= end) throw new IllegalArgumentException("truncated SMS-SUBMIT message reference");
            out.append("TPDU message reference: ").append(u8(pdu[pos++])).append('\n');
            pos = parseAddress(pdu, pos, end, "destination", out);
            pos = require(pdu, pos, 2, "SMS-SUBMIT PID/DCS");
            int dcs = u8(pdu[pos - 1]);
            out.append("Data coding scheme: 0x").append(hexByte(dcs)).append('\n');
            int vpf = first & 0x18;
            int validityBytes = vpf == 0 ? 0 : vpf == 0x10 ? 1 : vpf == 0x08 ? 7 : 1;
            pos = require(pdu, pos, validityBytes, "SMS-SUBMIT validity period");
            if (pos >= end) throw new IllegalArgumentException("missing SMS user-data length");
            int userDataLength = u8(pdu[pos++]);
            appendUserData(pdu, pos, end, userDataLength, dcs, (first & 0x40) != 0, out);
        } else {
            out.append("TPDU decoding: only SMS-SUBMIT user data is currently supported");
        }
    }

    private static int parseAddress(byte[] pdu, int pos, int end, String label, StringBuilder out) {
        if (pos >= end) throw new IllegalArgumentException("missing SMS " + label + " address length");
        int digits = u8(pdu[pos++]);
        if (pos >= end) throw new IllegalArgumentException("missing SMS " + label + " type-of-address");
        int toa = u8(pdu[pos++]);
        int octets = (digits + 1) / 2;
        pos = require(pdu, pos, octets, "SMS " + label + " address");
        out.append("TPDU ").append(label).append(": ").append(decodeAddress(pdu, pos - octets, octets, digits, toa)).append('\n');
        return pos;
    }

    private static void appendUserData(byte[] pdu, int pos, int end, int length, int dcs, boolean hasUdh, StringBuilder out) {
        int alphabet = (dcs & 0x0c) >> 2;
        int bytes = alphabet == 0 ? (length * 7 + 7) / 8 : length;
        if (bytes > end - pos) bytes = end - pos;
        if (hasUdh && bytes > 0) {
            int udhl = u8(pdu[pos]);
            int headerBytes = Math.min(bytes, udhl + 1);
            pos += headerBytes; bytes -= headerBytes;
        }
        if (alphabet == 0) out.append("SMS text: ").append(decodeGsm7(pdu, pos, Math.min(length, bytes * 8 / 7))).append('\n');
        else if (alphabet == 2) out.append("SMS text: ").append(new String(pdu, pos, bytes, StandardCharsets.UTF_16BE)).append('\n');
        else out.append("SMS user data (8-bit): ").append(hex(pdu, pos, bytes)).append('\n');
    }

    private static String decodeGsm7(byte[] data, int offset, int septets) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < septets; i++) {
            int bit = i * 7;
            int value = ((u8(data[offset + bit / 8]) >> (bit % 8)) | (bit % 8 > 1 ? u8(data[offset + bit / 8 + 1]) << (8 - bit % 8) : 0)) & 0x7f;
            if (value == 0x1b) { text.append('^'); continue; }
            text.append(value < GSM7.length ? GSM7[value] : '?');
        }
        return text.toString();
    }

    private static String decodeAddress(byte[] data, int offset, int octets, int digits, int toa) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < octets; i++) { int b = u8(data[offset + i]); value.append(b & 0x0f); if (value.length() < digits) value.append((b >> 4) & 0x0f); }
        return (toa & 0x70) == 0x10 ? "+" + value : value.toString();
    }

    private static int require(byte[] data, int pos, int count, String what) { if(count < 0 || count > data.length - pos) throw new IllegalArgumentException("truncated " + what); return pos + count; }
    private static int u8(byte b) { return b & 0xff; }
    private static String hex(byte[] data) { return hex(data, 0, data.length); }
    private static String hex(byte[] data, int offset, int length) { StringBuilder s=new StringBuilder(); for(int i=0;i<length;i++){if(i>0)s.append(' ');s.append(hexByte(u8(data[offset+i])));} return s.toString(); }
    private static String hexByte(int value) { return String.format(Locale.ROOT,"%02X",value); }
}
