package com.spde.sclauncher.net.pojo;

import java.io.Serializable;

public abstract class RegionShape implements Serializable {
    private String name;

    public RegionShape() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    protected String cropBrackets(String in){
        String out = in;
        if(out.startsWith("(")){
            out = out.substring(1);
        }
        if(out.endsWith(")")){
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    public abstract boolean build(String[] field);
}
