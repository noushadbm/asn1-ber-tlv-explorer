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

## Next step

V3 can extend the schema engine with explicit context-specific tags (`[0]`, `[1]`...), `OPTIONAL`, `DEFAULT`, `CHOICE`, `SEQUENCE OF`, `SET OF`, named ENUMERATED values, and automatic schema validation against the BER tag tree.
