package com.example.berexplorer.definition;

import com.example.berexplorer.model.TlvNode;
import java.util.*;
import java.util.regex.*;

/** Small, intentionally readable ASN.1 subset parser for V2 definitions. */
public final class DefinitionParser {
    private DefinitionParser() {}
    private static final Pattern ASSIGN = Pattern.compile("^([A-Za-z][A-Za-z0-9-]*)\\s*::=\\s*(.*)$", Pattern.DOTALL);

    public static Schema parse(String text) {
        String cleaned = text.replaceAll("(?s)--.*?(?:\\R|$)", " ").trim();
        int begin = cleaned.indexOf("BEGIN"); int end = cleaned.lastIndexOf("END");
        boolean explicitTags = false;
        if(begin>=0 && end>begin) {
            String header = cleaned.substring(0, begin);
            if(header.contains("AUTOMATIC TAGS")) throw new IllegalArgumentException("AUTOMATIC TAGS is not supported");
            explicitTags = !header.contains("IMPLICIT TAGS");
            cleaned=cleaned.substring(begin+5,end);
        }
        Schema s=new Schema();
        for(String stmt: splitAssignments(cleaned)) {
            Matcher m=ASSIGN.matcher(stmt.trim()); if(!m.matches()) continue;
            String name=m.group(1); Parser p=new Parser(m.group(2).trim(), explicitTags); Schema.Type t=p.type();
            if(p.peek()!=null) throw new IllegalArgumentException("Unexpected token: " + p.peek());
            s.types.add(new Schema.TypeDef(name,t));
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
        final boolean explicitTags;
        Parser(String x, boolean explicitTags) {
            this.explicitTags = explicitTags;
            Matcher m = Pattern.compile("::=|\\[|\\]|\\{|\\}|,|\\(|\\)|[A-Za-z][A-Za-z0-9-]*|[0-9]+|\\.\\.").matcher(x);
            int end = 0;
            while (m.find()) {
                if(!x.substring(end,m.start()).isBlank()) throw new IllegalArgumentException("Invalid schema near: " + x.substring(end,m.start()));
                tok.add(m.group());
                end = m.end();
            }
            if(!x.substring(end).isBlank()) throw new IllegalArgumentException("Invalid schema near: " + x.substring(end));
        }
        String peek(){ return i<tok.size()?tok.get(i):null; }
        String next(){ return i<tok.size()?tok.get(i++):null; }
        boolean eat(String x){ if(x.equals(peek())){i++;return true;} return false; }

        Schema.Type type(){
            if(eat("[")) {
                Schema.Type t = new Schema.Type("TAGGED");
                t.tagClass = TlvNode.TagClass.CONTEXT_SPECIFIC;
                if("APPLICATION".equals(peek()) || "PRIVATE".equals(peek()) || "UNIVERSAL".equals(peek())) {
                    t.tagClass = TlvNode.TagClass.valueOf(next());
                }
                String number = next();
                if(number == null || !number.matches("[0-9]+")) throw new IllegalArgumentException("Expected tag number");
                try { t.tagNumber = Integer.parseInt(number); }
                catch(NumberFormatException ex) { throw new IllegalArgumentException("Tag number too large: " + number); }
                if(!eat("]")) throw new IllegalArgumentException("Expected ] after tag number");
                t.explicit = explicitTags;
                if(eat("IMPLICIT")) t.explicit = false;
                else if(eat("EXPLICIT")) t.explicit = true;
                t.innerType = type();
                return t;
            }
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
                while(!eat("}")) {
                    if(peek()==null) throw new IllegalArgumentException("Expected } after " + k + " fields");
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
