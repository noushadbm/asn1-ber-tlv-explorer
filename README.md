# ASN.1 BER/DER Explorer — V2

Java 21 + JavaFX desktop application for BER/DER TLV exploration and a lightweight ASN.1 definition workflow.

## V2 features

- Everything from V1: BER/DER TLV parsing, HEX/Base64 input, recursive tree, offsets, lengths and raw bytes.
- **Generate Definition**: creates an editable ASN.1-like schema from the currently parsed TLV tree.
- **ASN.1 Definition editor** in the left pane.
- **Apply Definition**: decodes the BER tree using field names/types from the definition and displays a named decoded tree.
- MESSAGE records with a non-zero SIP `Content-Length` expose decoded SMS PDU details when the body is RP-DATA containing an SMS-SUBMIT TPDU.
- Root type field (default `Message`).
- Save `.asn1` definitions.
- Raw TLV details and decoded-node details.
- Hex viewer for the complete input.
- BER definite and indefinite lengths.
- High-tag-number support.

## Definition subset

V2 intentionally starts with a small, readable ASN.1 subset: type assignments, `SEQUENCE`, `SET`, and common primitive types. Example:

```asn1
Message ::= SEQUENCE {
    liid OCTET STRING,
    timestamp GeneralizedTime,
    communication Communication
}

Communication ::= SEQUENCE {
    network NetworkInfo,
    call CallInfo
}
```

The generated definition uses `field1`, `field2`, etc. Rename those fields to the real protocol names, then press **Apply Definition**.

## Run

Requirements: JDK 21+ and Maven 3.9+.

```bash
mvn clean javafx:run
```

On Apple Silicon, use an ARM64 JDK 21.

The platform profile must be selected explicitly. This also allows cross-building a target JAR from another operating system.

## Build Executable JARs

JavaFX includes platform-native libraries, so build a JAR for the target operating system. Each command creates an executable fat JAR in `target/` with the JavaFX runtime for that platform:

```bash
# Windows x64
mvn -Pwindows clean package

# Linux x64
mvn -Plinux clean package

# Linux ARM64
mvn -Plinux-aarch64 clean package

# macOS Intel
mvn -Pmac clean package

# macOS Apple Silicon
mvn -Pmac-aarch64 clean package
```

Run the resulting JAR with Java 21 or newer:

```bash
java -jar target/asn1-ber-tlv-explorer-0.2.0-<platform>.jar
```

The build machine does not need to match the target platform, but Maven must be able to download that platform's JavaFX artifacts. The JAR includes application and JavaFX dependencies; it does not include a Java runtime. For an installer that bundles Java and avoids requiring Java on the target machine, use `jpackage` on each target operating system after building its platform JAR.

## Next step

V3 can extend the schema engine with explicit context-specific tags (`[0]`, `[1]`...), `OPTIONAL`, `DEFAULT`, `CHOICE`, `SEQUENCE OF`, `SET OF`, named ENUMERATED values, and automatic schema validation against the BER tag tree.
