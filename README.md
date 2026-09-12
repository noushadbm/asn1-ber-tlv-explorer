# ASN.1 BER TLV Explorer — V1

A Java 21 + JavaFX desktop application for exploring BER/DER-style ASN.1 TLV data.

## V1 features

- Open `.ber`, `.der`, or `.bin` files.
- Paste hexadecimal BER/DER data.
- Paste Base64 input.
- Parse nested TLVs recursively.
- Display tag class, tag number, constructed flag, offset, header length, value offset, length, and child count.
- Decode common universal ASN.1 values: BOOLEAN, INTEGER, OCTET STRING, ENUMERATED, UTF8String, common character strings, UTCTime, GeneralizedTime, BIT STRING and NULL.
- Supports BER definite-length and indefinite-length constructed values, including end-of-contents markers.
- Select any node to inspect raw bytes and decoded value.

## Requirements

- JDK 21+
- Maven 3.9+

## Run

```bash
mvn clean javafx:run
```

On Apple Silicon macOS, use an ARM64 JDK 21 (for example, a current Temurin 21 build).

## Build

```bash
mvn clean package
```

## Try the supplied sample

`examples/sample_volte_iri_begin.ber` is the BER sample used while developing V1.

## Architecture

The UI is intentionally separate from the BER parser. The next version can add a schema/ASN.1 definition engine without replacing the TLV parser.
