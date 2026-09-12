package com.example.berexplorer.model;

import java.util.ArrayList;
import java.util.List;

public class TlvNode {
    public enum TagClass { UNIVERSAL, APPLICATION, CONTEXT_SPECIFIC, PRIVATE }
    private final TagClass tagClass;
    private final int tagNumber;
    private final boolean constructed;
    private final int offset;
    private final int headerLength;
    private final int valueOffset;
    private final int length;
    private final byte[] value;
    private final List<TlvNode> children = new ArrayList<>();
    private boolean indefiniteLength;
    private int totalLength;

    public TlvNode(TagClass tagClass, int tagNumber, boolean constructed, int offset,
                   int headerLength, int valueOffset, int length, byte[] value) {
        this.tagClass = tagClass;
        this.tagNumber = tagNumber;
        this.constructed = constructed;
        this.offset = offset;
        this.headerLength = headerLength;
        this.valueOffset = valueOffset;
        this.length = length;
        this.value = value;
        this.totalLength = headerLength + Math.max(length, 0);
    }
    public TagClass getTagClass() { return tagClass; }
    public int getTagNumber() { return tagNumber; }
    public boolean isConstructed() { return constructed; }
    public int getOffset() { return offset; }
    public int getHeaderLength() { return headerLength; }
    public int getValueOffset() { return valueOffset; }
    public int getLength() { return length; }
    public byte[] getValue() { return value; }
    public List<TlvNode> getChildren() { return children; }
    public boolean isIndefiniteLength() { return indefiniteLength; }
    public void setIndefiniteLength(boolean v) { indefiniteLength = v; }
    public int getTotalLength() { return totalLength; }
    public void setTotalLength(int v) { totalLength = v; }
    public String tagHex() { return String.format("%02X", tagNumber); }

    public String universalTypeName() {
        if (tagClass != TagClass.UNIVERSAL) return "[" + tagClass + "]";
        return switch (tagNumber) {
            case 1 -> "BOOLEAN"; case 2 -> "INTEGER"; case 3 -> "BIT STRING";
            case 4 -> "OCTET STRING"; case 5 -> "NULL"; case 6 -> "OBJECT IDENTIFIER";
            case 10 -> "ENUMERATED"; case 12 -> "UTF8String"; case 16 -> "SEQUENCE";
            case 17 -> "SET"; case 18 -> "NumericString"; case 19 -> "PrintableString";
            case 20 -> "T61String"; case 22 -> "IA5String"; case 23 -> "UTCTime";
            case 24 -> "GeneralizedTime"; case 26 -> "VisibleString"; case 27 -> "GeneralString";
            case 28 -> "UniversalString"; case 30 -> "BMPString"; default -> "UNIVERSAL(" + tagNumber + ")";
        };
    }
    @Override public String toString() {
        String len = indefiniteLength ? "indef" : Integer.toString(length);
        return universalTypeName() + " [" + tagHex() + "] len=" + len + " @" + offset;
    }
}
