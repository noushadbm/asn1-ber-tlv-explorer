package com.example.berexplorer.definition;

import java.util.ArrayList;
import java.util.List;

public class Schema {
    public final List<TypeDef> types = new ArrayList<>();
    public TypeDef find(String name) { return types.stream().filter(t -> t.name.equals(name)).findFirst().orElse(null); }
    public static class TypeDef { public String name; public Type type; public TypeDef(String n, Type t){name=n;type=t;} }
    public static class Type {
        public String kind; public List<Field> fields = new ArrayList<>(); public String ref; public boolean optional;
        public Type(String k){kind=k;}
    }
    public static class Field { public String name; public Type type; public boolean optional; public Field(String n,Type t,boolean o){name=n;type=t;optional=o;} }
}
