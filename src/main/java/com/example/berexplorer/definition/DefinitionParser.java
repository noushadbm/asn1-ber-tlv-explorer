package com.example.berexplorer.definition;

import java.util.*;
import java.util.regex.*;

/** Small, intentionally readable ASN.1 subset parser for V2 definitions. */
public final class DefinitionParser {
    private DefinitionParser() {}
    private static final Pattern ASSIGN = Pattern.compile("^([A-Za-z][A-Za-z0-9-]*)\\s*::=\\s*(.*)$", Pattern.DOTALL);

    public static Schema parse(String text) {
        String cleaned = text.replaceAll("(?s)--.*?(?:\\R|$)", " ").trim();
        int begin = cleaned.indexOf("BEGIN"); int end = cleaned.lastIndexOf("END");
        if(begin>=0 && end>begin) cleaned=cleaned.substring(begin+5,end);
        Schema s=new Schema();
        for(String stmt: splitAssignments(cleaned)) {
            Matcher m=ASSIGN.matcher(stmt.trim()); if(!m.matches()) continue;
            String name=m.group(1); Parser p=new Parser(m.group(2).trim()); Schema.Type t=p.type(); s.types.add(new Schema.TypeDef(name,t));
        }
        if(s.types.isEmpty()) throw new IllegalArgumentException("No ASN.1 type assignments found. Expected: Name ::= SEQUENCE { ... }");
        return s;
    }
    private static List<String> splitAssignments(String x){
        List<String> out=new ArrayList<>();
        Matcher m=Pattern.compile("(?m)(?:^|\\R)\\s*([A-Za-z][A-Za-z0-9-]*)\\s*::=\\s*").matcher(x);
        List<Integer> starts=new ArrayList<>();
        while(m.find()) starts.add(m.start());
        if(starts.isEmpty()) return List.of(x);
        for(int j=0;j<starts.size();j++){ int a=starts.get(j), b=(j+1<starts.size()?starts.get(j+1):x.length()); String part=x.substring(a,b).trim(); if(!part.isBlank())out.add(part); }
        return out;
    }
    private static final class Parser {
        final List<String> tok = new ArrayList<>();
        int i;
        Parser(String x) {
            Matcher m = Pattern.compile("::=|\\{|\\}|,|\\(|\\)|[A-Za-z][A-Za-z0-9-]*|[0-9]+|\\.\\.").matcher(x);
            while (m.find()) tok.add(m.group());
        }
        String peek(){ return i<tok.size()?tok.get(i):null; }
        String next(){ return i<tok.size()?tok.get(i++):null; }
        boolean eat(String x){ if(x.equals(peek())){i++;return true;} return false; }

        Schema.Type type(){
            String k=next();
            if(k==null) throw new IllegalArgumentException("Missing type");
            if(k.equals("SEQUENCE") || k.equals("SET")) {
                if(eat("OF")) {
                    Schema.Type t=new Schema.Type(k+" OF");
                    t.ref=next();
                    return t;
                }
                Schema.Type t=new Schema.Type(k);
                if(!eat("{")) throw new IllegalArgumentException("Expected { after "+k);
                while(peek()!=null && !eat("}")) {
                    String name=next();
                    if(name==null || name.equals(",")) continue;
                    Schema.Type ft=type();
                    boolean opt=eat("OPTIONAL");
                    t.fields.add(new Schema.Field(name,ft,opt));
                    eat(",");
                }
                return t;
            }
            if(k.equals("OCTET") && eat("STRING")) k="OCTET STRING";
            else if(k.equals("BIT") && eat("STRING")) k="BIT STRING";
            else if(k.equals("OBJECT") && eat("IDENTIFIER")) k="OBJECT IDENTIFIER";
            else if(k.equals("CHARACTER") && eat("STRING")) k="CHARACTER STRING";
            Schema.Type t=new Schema.Type(k);
            if(eat("OF")) { t.kind=k+" OF"; t.ref=next(); }
            return t;
        }
    }

}
