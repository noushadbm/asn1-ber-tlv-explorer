package com.example.berexplorer.definition;
import com.example.berexplorer.model.TlvNode;
public final class DefinitionGenerator {
 private DefinitionGenerator(){}
 public static String generate(String rootName,TlvNode root){StringBuilder s=new StringBuilder();s.append(rootName).append(" ::= ");emitType(s,root,0);s.append("\n");return s.toString();}
 private static void emitType(StringBuilder s,TlvNode n,int depth){String type=n.universalTypeName();if(n.isConstructed()&&(n.getTagNumber()==16||n.getTagNumber()==17)){s.append(type).append(" {\n");int idx=1;for(TlvNode c:n.getChildren()){indent(s,depth+1);s.append("field").append(idx++).append(' ');emitType(s,c,depth+1);s.append(",\n");}indent(s,depth);s.append('}');}else{s.append(type);}}
 private static void indent(StringBuilder s,int d){s.append("    ".repeat(Math.max(0,d)));}
}
