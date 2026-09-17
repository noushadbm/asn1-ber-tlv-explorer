package com.example.berexplorer.definition;

import com.example.berexplorer.model.TlvNode;
import com.example.berexplorer.ber.BerDecoder;
import java.util.*;

public final class SchemaDecoder {
    private SchemaDecoder(){}
    public static DecodedNode decode(Schema schema, String rootType, TlvNode node){
        Schema.TypeDef def=schema.find(rootType); if(def==null)throw new IllegalArgumentException("Root type not found: "+rootType);
        return decodeType(schema, rootType, def.type, node, rootType);
    }
    private static DecodedNode decodeType(Schema s,String label,Schema.Type t,TlvNode n,String display){
        if(t.tagNumber != null) {
            if(!matches(t,n,s)) return new DecodedNode(label,t.innerType.kind,n,"<tag/type mismatch>");
            TlvNode valueNode;
            if(t.explicit) {
                valueNode = n.getChildren().get(0);
            } else {
                int tag = universalTag(t.innerType.kind);
                if(tag < 0) throw new IllegalArgumentException("Unsupported implicit type: " + t.innerType.kind);
                valueNode = new TlvNode(TlvNode.TagClass.UNIVERSAL,tag,n.isConstructed(),n.getOffset(),
                        n.getHeaderLength(),n.getValueOffset(),n.getLength(),n.getValue());
                valueNode.getChildren().addAll(n.getChildren());
                valueNode.setTotalLength(n.getTotalLength());
                valueNode.setIndefiniteLength(n.isIndefiniteLength());
            }
            DecodedNode value = decodeType(s,label,t.innerType,valueNode,display);
            DecodedNode d = new DecodedNode(label,value.type,n,value.value);
            d.children.addAll(value.children);
            return d;
        }
        if(isConstructedKind(t.kind)){
            DecodedNode d=new DecodedNode(label,t.kind,n,"<constructed>");
            if(t.kind.equals("SEQUENCE")||t.kind.equals("SET")){int idx=0; for(Schema.Field f:t.fields){if(idx>=n.getChildren().size()){if(!f.optional) d.children.add(new DecodedNode(f.name,f.type.kind,null,"<missing>")); continue;} TlvNode child=n.getChildren().get(idx); if(!matches(f.type,child,s)){if(f.optional)continue; d.children.add(new DecodedNode(f.name,f.type.kind,child,"<tag/type mismatch>")); idx++; continue;} d.children.add(decodeType(s,f.name,resolve(s,f.type),child,f.name)); idx++;} if(idx<n.getChildren().size()) for(;idx<n.getChildren().size();idx++)d.children.add(new DecodedNode("[unmapped-"+idx+"]",n.getChildren().get(idx).universalTypeName(),n.getChildren().get(idx),BerDecoder.decodeValue(n.getChildren().get(idx)))); }
            else if(t.ref!=null){Schema.Type rt=resolve(s,t); d.children.addAll(decodeType(s,label,rt,n,display).children);}
            return d;
        }
        return new DecodedNode(label,t.kind,n,n==null?"<missing>":BerDecoder.decodeValue(n));
    }
    private static boolean isConstructedKind(String k){return k.equals("SEQUENCE")||k.equals("SET")||k.endsWith(" OF");}
    private static Schema.Type resolve(Schema s,Schema.Type t){if(t.ref!=null){Schema.TypeDef d=s.find(t.ref);if(d==null)throw new IllegalArgumentException("Unknown type: "+t.ref);return d.type;}return t;}
    private static boolean matches(Schema.Type t,TlvNode n,Schema s){Schema.Type x=resolve(s,t);
        if(x.tagNumber != null) {
            if(n.getTagClass() != x.tagClass || n.getTagNumber() != x.tagNumber) return false;
            if(x.explicit) return n.isConstructed() && n.getChildren().size() == 1 && matches(x.innerType,n.getChildren().get(0),s);
            return isConstructedKind(x.innerType.kind) == n.isConstructed();
        }
        String k=x.kind; if(k.equals("ANY"))return true; return switch(k){case "SEQUENCE","SET"->n.isConstructed()&&n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&(k.equals("SEQUENCE")?n.getTagNumber()==16:n.getTagNumber()==17);case "OCTET STRING"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==4;case "INTEGER"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==2;case "ENUMERATED"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==10;case "BOOLEAN"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==1;case "BIT STRING"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==3;case "NULL"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==5;case "GeneralizedTime"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==24;case "UTCTime"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==23;case "UTF8String"->universal(n,12); case "NumericString"->universal(n,18); case "PrintableString"->universal(n,19); case "T61String"->universal(n,20); case "IA5String"->universal(n,22); case "VisibleString"->universal(n,26); case "GeneralString"->universal(n,27); case "UniversalString"->universal(n,28); case "BMPString"->universal(n,30); case "OBJECT IDENTIFIER"->universal(n,6); default->n.getTagClass()==TlvNode.TagClass.UNIVERSAL;};}
    private static int universalTag(String kind) {
        return switch(kind) {
            case "BOOLEAN" -> 1; case "INTEGER" -> 2; case "BIT STRING" -> 3;
            case "OCTET STRING" -> 4; case "NULL" -> 5; case "OBJECT IDENTIFIER" -> 6;
            case "ENUMERATED" -> 10; case "UTF8String" -> 12;
            case "SEQUENCE", "SEQUENCE OF" -> 16; case "SET", "SET OF" -> 17;
            case "NumericString" -> 18; case "PrintableString" -> 19; case "T61String" -> 20;
            case "IA5String" -> 22; case "UTCTime" -> 23; case "GeneralizedTime" -> 24;
            case "VisibleString" -> 26; case "GeneralString" -> 27;
            case "UniversalString" -> 28; case "BMPString" -> 30;
            default -> -1;
        };
    }
    private static boolean universal(TlvNode n,int tag){return n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==tag;}
    public static class DecodedNode {public final String name,type,value; public final TlvNode raw; public final List<DecodedNode> children=new ArrayList<>(); public DecodedNode(String n,String t,TlvNode r,String v){name=n;type=t;raw=r;value=v;} @Override public String toString(){return name+" : "+type+" = "+value;}}
}
