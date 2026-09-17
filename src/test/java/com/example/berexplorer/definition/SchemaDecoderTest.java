package com.example.berexplorer.definition;

import com.example.berexplorer.ber.BerDecoder;
import com.example.berexplorer.util.Hex;
import org.junit.Test;
import static org.junit.Assert.*;

public class SchemaDecoderTest {
    @Test public void decodesSampleSchema() throws Exception {
        Schema schema = DefinitionParser.parse(java.nio.file.Files.readString(java.nio.file.Path.of("examples/sample_01.asn1")));
        var raw = BerDecoder.decodeSingle(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("examples/sample_01.ber")));
        var decoded = SchemaDecoder.decode(schema, "Message", raw);
        assertEquals(2, decoded.children.size());
        assertEquals("header", decoded.children.get(0).name);
        assertEquals("payload", decoded.children.get(1).name);
        assertMapped(decoded);
        var header = decoded.children.get(0);
        assertTrue(header.children.get(0).value.matches("[0-9]+(\\.[0-9]+)+"));
        assertTrue(header.children.get(3).value.matches("-?[0-9]+ \\(0x[0-9A-F ]+\\)"));
        assertEquals("IA5String", find(decoded,"sipMessage").type);
        assertFalse(find(decoded,"sipMessage").value.isBlank());
    }

    @Test public void decodesExplicitTag() {
        var decoded = decode("Message ::= [1] EXPLICIT INTEGER", "A1 03 02 01 2A");
        assertEquals("42 (0x2A)", decoded.value);
        assertEquals(1, decoded.raw.getTagNumber());
    }

    @Test public void respectsModuleTagDefaults() {
        assertEquals("42 (0x2A)", decode("Test DEFINITIONS ::= BEGIN\nMessage ::= [1] INTEGER\nEND", "A1 03 02 01 2A").value);
        assertEquals("42 (0x2A)", decode("Test DEFINITIONS IMPLICIT TAGS ::= BEGIN\nMessage ::= [1] INTEGER\nEND", "81 01 2A").value);
        assertEquals("42 (0x2A)", decode("Test DEFINITIONS EXPLICIT TAGS ::= BEGIN\nMessage ::= [1] IMPLICIT INTEGER\nEND", "81 01 2A").value);
    }

    @Test public void skipsAbsentOptionalTaggedField() {
        var decoded = decode("Message ::= SEQUENCE { absent [0] INTEGER OPTIONAL, value [1] INTEGER }", "30 03 81 01 2A");
        assertEquals(1, decoded.children.size());
        assertEquals("value", decoded.children.get(0).name);
        assertEquals("42 (0x2A)", decoded.children.get(0).value);
    }

    @Test public void rejectsIncorrectTagAndExplicitWrapper() {
        assertEquals("<tag/type mismatch>", decode("Message ::= [1] INTEGER", "82 01 2A").value);
        assertEquals("<tag/type mismatch>", decode("Message ::= [1] EXPLICIT INTEGER", "A1 00").value);
        assertEquals("<tag/type mismatch>", decode("Message ::= [1] EXPLICIT INTEGER", "A1 03 04 01 2A").value);
        assertEquals("<tag/type mismatch>", decode("Message ::= [1] SEQUENCE {}", "81 00").value);
    }

    @Test public void preservesUntaggedDecoding() {
        var decoded = decode("Message ::= SEQUENCE { value INTEGER }", "30 03 02 01 2A");
        assertEquals("42 (0x2A)", decoded.children.get(0).value);
    }

    @Test public void rejectsMalformedTags() {
        for(String text : new String[]{"Message ::= [-1] INTEGER", "Message ::= [1 INTEGER", "Message ::= [] INTEGER", "Message ::= [1]", "Message ::= SEQUENCE { value [1] INTEGER"}) {
            assertThrows(IllegalArgumentException.class, () -> DefinitionParser.parse(text));
        }
    }

    private static SchemaDecoder.DecodedNode decode(String schema, String hex) {
        return SchemaDecoder.decode(DefinitionParser.parse(schema),"Message",BerDecoder.decodeSingle(Hex.parse(hex)));
    }

    private static void assertMapped(SchemaDecoder.DecodedNode node) {
        assertNotNull(node.raw);
        assertNotEquals("<missing>", node.value);
        assertNotEquals("<tag/type mismatch>", node.value);
        assertFalse(node.name.startsWith("[unmapped-"));
        for(var child : node.children) assertMapped(child);
    }

    private static SchemaDecoder.DecodedNode find(SchemaDecoder.DecodedNode node, String name) {
        if(node.name.equals(name)) return node;
        for(var child : node.children) {
            var match = find(child,name);
            if(match != null) return match;
        }
        return null;
    }

    @Test public void decodesImplicitContextString() {
        Schema schema = DefinitionParser.parse("Message ::= SEQUENCE { value [1] IA5String }");
        var raw = BerDecoder.decodeSingle(Hex.parse("30 05 81 03 61 62 63"));
        var decoded = SchemaDecoder.decode(schema, "Message", raw);
        assertEquals(1, decoded.children.size());
        assertEquals("value", decoded.children.get(0).name);
        assertEquals("IA5String", decoded.children.get(0).type);
        assertEquals("abc", decoded.children.get(0).value);
        assertSame(raw.getChildren().get(0), decoded.children.get(0).raw);
    }
}
