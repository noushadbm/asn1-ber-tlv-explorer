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
        if(isConstructedKind(t.kind)){
            DecodedNode d=new DecodedNode(label,t.kind,n,"<constructed>");
            if(t.kind.equals("SEQUENCE")||t.kind.equals("SET")){int idx=0; for(Schema.Field f:t.fields){if(idx>=n.getChildren().size()){if(!f.optional) d.children.add(new DecodedNode(f.name,f.type.kind,null,"<missing>")); continue;} TlvNode child=n.getChildren().get(idx); if(!matches(f.type,child,s)){if(f.optional)continue; d.children.add(new DecodedNode(f.name,f.type.kind,child,"<tag/type mismatch>")); idx++; continue;} d.children.add(decodeType(s,f.name,resolve(s,f.type),child,f.name)); idx++;} if(idx<n.getChildren().size()) for(;idx<n.getChildren().size();idx++)d.children.add(new DecodedNode("[unmapped-"+idx+"]",n.getChildren().get(idx).universalTypeName(),n.getChildren().get(idx),BerDecoder.decodeValue(n.getChildren().get(idx)))); }
            else if(t.ref!=null){Schema.Type rt=resolve(s,t); d.children.addAll(decodeType(s,label,rt,n,display).children);}
            return d;
        }
        return new DecodedNode(label,t.kind,n,n==null?"<missing>":BerDecoder.decodeValue(n,t.kind));
    }
    private static boolean isConstructedKind(String k){return k.equals("SEQUENCE")||k.equals("SET")||k.endsWith(" OF");}
    private static Schema.Type resolve(Schema s,Schema.Type t){if(t.ref!=null){Schema.TypeDef d=s.find(t.ref);if(d==null)throw new IllegalArgumentException("Unknown type: "+t.ref);return d.type;}return t;}
    private static boolean matches(Schema.Type t,TlvNode n,Schema s){Schema.Type x=resolve(s,t);String k=x.kind; if(x.tagNumber!=null){if(n.getTagClass()!=TlvNode.TagClass.CONTEXT_SPECIFIC||n.getTagNumber()!=x.tagNumber)return false;return !isConstructedKind(k)||n.isConstructed();} if(k.equals("ANY"))return true; return switch(k){case "SEQUENCE","SET"->n.isConstructed()&&n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&(k.equals("SEQUENCE")?n.getTagNumber()==16:n.getTagNumber()==17);case "OCTET STRING"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==4;case "INTEGER"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==2;case "ENUMERATED"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==10;case "BOOLEAN"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==1;case "BIT STRING"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==3;case "NULL"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==5;case "GeneralizedTime"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==24;case "UTCTime"->n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==23;case "UTF8String"->universal(n,12); case "NumericString"->universal(n,18); case "PrintableString"->universal(n,19); case "T61String"->universal(n,20); case "IA5String"->universal(n,22); case "VisibleString"->universal(n,26); case "GeneralString"->universal(n,27); case "UniversalString"->universal(n,28); case "BMPString"->universal(n,30); case "OBJECT IDENTIFIER"->universal(n,6); default->n.getTagClass()==TlvNode.TagClass.UNIVERSAL;};}
    private static boolean universal(TlvNode n,int tag){return n.getTagClass()==TlvNode.TagClass.UNIVERSAL&&n.getTagNumber()==tag;}
    public static class DecodedNode {public final String name,type,value; public final TlvNode raw; public final List<DecodedNode> children=new ArrayList<>(); public DecodedNode(String n,String t,TlvNode r,String v){name=n;type=t;raw=r;value=v;} @Override public String toString(){return name+" : "+type+" = "+value;}}
}
