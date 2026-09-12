package com.example.berexplorer;

import java.util.ArrayList;
import java.util.List;

public final class TlvNode {
    public enum TagClass { UNIVERSAL, APPLICATION, CONTEXT_SPECIFIC, PRIVATE }

    private final int offset;
    private final TagClass tagClass;
    private final boolean constructed;
    private final int tagNumber;
    private final int headerLength;
    private final long length;
    private final boolean indefiniteLength;
    private final int valueOffset;
    private final int totalLength;
    private final byte[] value;
    private final List<TlvNode> children = new ArrayList<>();

    public TlvNode(int offset, TagClass tagClass, boolean constructed, int tagNumber,
                   int headerLength, long length, boolean indefiniteLength,
                   int valueOffset, int totalLength, byte[] value) {
        this.offset = offset;
        this.tagClass = tagClass;
        this.constructed = constructed;
        this.tagNumber = tagNumber;
        this.headerLength = headerLength;
        this.length = length;
        this.indefiniteLength = indefiniteLength;
        this.valueOffset = valueOffset;
        this.totalLength = totalLength;
        this.value = value;
    }

    public int offset() { return offset; }
    public TagClass tagClass() { return tagClass; }
    public boolean constructed() { return constructed; }
    public int tagNumber() { return tagNumber; }
    public int headerLength() { return headerLength; }
    public long length() { return length; }
    public boolean indefiniteLength() { return indefiniteLength; }
    public int valueOffset() { return valueOffset; }
    public int totalLength() { return totalLength; }
    public byte[] value() { return value; }
    public List<TlvNode> children() { return children; }

    public String tagHex() {
        StringBuilder b = new StringBuilder();
        if (tagClass == TagClass.UNIVERSAL) b.append(String.format("%02X", universalTagByte()));
        else b.append("class-specific");
        return b.toString();
    }

    public int universalTagByte() {
        int first = (tagClass.ordinal() << 6) | (constructed ? 0x20 : 0);
        if (tagNumber < 31) return first | tagNumber;
        return first | 0x1F;
    }

    public String typeName() {
        if (tagClass != TagClass.UNIVERSAL) return tagClass.name().replace('_', ' ') + " TAG " + tagNumber;
        return switch (tagNumber) {
            case 0 -> "EOC";
            case 1 -> "BOOLEAN";
            case 2 -> "INTEGER";
            case 3 -> "BIT STRING";
            case 4 -> "OCTET STRING";
            case 5 -> "NULL";
            case 6 -> "OBJECT IDENTIFIER";
            case 7 -> "ObjectDescriptor";
            case 8 -> "EXTERNAL";
            case 9 -> "REAL";
            case 10 -> "ENUMERATED";
            case 11 -> "EMBEDDED PDV";
            case 12 -> "UTF8String";
            case 16 -> "SEQUENCE";
            case 17 -> "SET";
            case 18 -> "NumericString";
            case 19 -> "PrintableString";
            case 20 -> "T61String";
            case 21 -> "VideotexString";
            case 22 -> "IA5String";
            case 23 -> "UTCTime";
            case 24 -> "GeneralizedTime";
            case 25 -> "GraphicString";
            case 26 -> "VisibleString";
            case 27 -> "GeneralString";
            case 28 -> "UniversalString";
            case 30 -> "BMPString";
            default -> "UNIVERSAL TAG " + tagNumber;
        };
    }
}
