package com.example.berexplorer.ber;

import com.example.berexplorer.model.TlvNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class BerDecoder {
    private BerDecoder() {}

    public static TlvNode decodeSingle(byte[] data) {
        Cursor c = new Cursor(data);
        TlvNode n = parse(c, data.length);
        if (c.pos != data.length) throw new IllegalArgumentException("Trailing bytes after top-level TLV: " + (data.length-c.pos));
        return n;
    }

    public static TlvNode parse(Cursor c, int limit) {
        int start = c.pos;
        if (c.pos >= limit) throw new IllegalArgumentException("Unexpected end while reading tag at offset " + c.pos);
        int first = u8(c.read());
        int clsBits = (first >>> 6) & 3;
        TlvNode.TagClass cls = switch (clsBits) {
            case 0 -> TlvNode.TagClass.UNIVERSAL; case 1 -> TlvNode.TagClass.APPLICATION;
            case 2 -> TlvNode.TagClass.CONTEXT_SPECIFIC; default -> TlvNode.TagClass.PRIVATE;
        };
        boolean constructed = (first & 0x20) != 0;
        int tag = first & 0x1f;
        if (tag == 0x1f) {
            tag = 0;
            int b;
            do { if (c.pos >= limit) throw new IllegalArgumentException("Truncated high-tag-number at " + start); b=u8(c.read());
                if (tag > 0x1fffffff) throw new IllegalArgumentException("Tag number too large at " + start);
                tag = (tag << 7) | (b & 0x7f);
            } while ((b & 0x80) != 0);
        }
        int lengthFirst = u8(c.read());
        boolean indefinite = lengthFirst == 0x80;
        int length;
        if (indefinite) length = -1;
        else if ((lengthFirst & 0x80) == 0) length = lengthFirst;
        else {
            int count = lengthFirst & 0x7f;
            if (count == 0) throw new IllegalArgumentException("Invalid reserved length form at " + (c.pos-1));
            if (count > 4) throw new IllegalArgumentException("Length > 4 bytes is not supported at " + (c.pos-1));
            length = 0;
            for (int i=0;i<count;i++) length = (length << 8) | u8(c.read());
        }
        int header = c.pos - start;
        int valueOffset = c.pos;
        TlvNode node;
        if (indefinite) {
            if (!constructed) throw new IllegalArgumentException("Indefinite length used on primitive TLV at " + start);
            node = new TlvNode(cls, tag, true, start, header, valueOffset, -1, new byte[0]);
            while (true) {
                if (c.pos + 2 > limit) throw new IllegalArgumentException("Missing end-of-contents for TLV at " + start);
                if (u8(c.data[c.pos]) == 0 && u8(c.data[c.pos+1]) == 0) { c.pos += 2; break; }
                node.getChildren().add(parse(c, limit));
            }
            node.setIndefiniteLength(true); node.setTotalLength(c.pos-start);
        } else {
            if (length > limit - c.pos) throw new IllegalArgumentException("TLV length " + length + " exceeds input at " + start);
            byte[] value = Arrays.copyOfRange(c.data, c.pos, c.pos+length);
            node = new TlvNode(cls, tag, constructed, start, header, valueOffset, length, value);
            if (constructed) {
                int end = c.pos + length;
                while (c.pos < end) node.getChildren().add(parse(c, end));
                if (c.pos != end) throw new IllegalArgumentException("Constructed TLV ended incorrectly at " + start);
            } else {
                c.pos += length;
            }
            node.setTotalLength(c.pos-start);
        }
        return node;
    }

    private static int u8(byte b) { return b & 0xff; }
    public static String decodeValue(TlvNode n) {
        byte[] v = n.getValue();
        if (n.getTagClass() != TlvNode.TagClass.UNIVERSAL) return hex(v);
        return switch (n.getTagNumber()) {
            case 1 -> v.length == 1 ? (v[0] != 0 ? "TRUE" : "FALSE") : "Invalid BOOLEAN";
            case 2, 10 -> integer(v);
            case 3 -> bitString(v);
            case 4 -> printableOrHex(v);
            case 5 -> "NULL";
            case 12,18,19,20,22,26,27,28,30 -> stringValue(n.getTagNumber(), v);
            case 23,24 -> new String(v, StandardCharsets.US_ASCII);
            case 6 -> oid(v);
            default -> n.isConstructed() ? "" : printableOrHex(v);
        };
    }
    private static String integer(byte[] v) { if(v.length==0)return "<empty>"; java.math.BigInteger x=new java.math.BigInteger(v); return x.toString()+" (0x"+hex(v)+")"; }
    private static String bitString(byte[] v) { if(v.length==0)return "<empty>"; return "unusedBits="+(v[0]&255)+", " + hex(Arrays.copyOfRange(v,1,v.length)); }
    private static String stringValue(int tag, byte[] v) { try { if(tag==30)return new String(v, StandardCharsets.UTF_16BE); if(tag==28)return new String(v, java.nio.charset.Charset.forName("UTF-32BE")); return new String(v, StandardCharsets.UTF_8); } catch(Exception e){return printableOrHex(v);} }
    private static String printableOrHex(byte[] v) { boolean printable=true; for(byte b:v){int x=b&255; if(x<32||x>126){printable=false;break;}} return printable ? new String(v,StandardCharsets.UTF_8) : hex(v); }
    private static String hex(byte[] v) { StringBuilder s=new StringBuilder(); for(int i=0;i<v.length;i++){if(i>0)s.append(' ');s.append(String.format("%02X",v[i]));} return s.toString(); }
    private static String oid(byte[] v) { if(v.length==0)return ""; StringBuilder s=new StringBuilder(); int first=v[0]&255; s.append(Math.min(2,first/40)).append('.').append(first<80?first%40:first-80); long value=0; for(int i=1;i<v.length;i++){int b=v[i]&255; value=(value<<7)|(b&127); if((b&128)==0){s.append('.').append(value);value=0;}} return s.toString(); }
    public static final class Cursor { final byte[] data; int pos; public Cursor(byte[] data){this.data=data;} byte read(){if(pos>=data.length)throw new IllegalArgumentException("Unexpected end at offset "+pos);return data[pos++];} }
}
