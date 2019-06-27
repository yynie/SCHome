package com.spde.sclauncher.net.pojo;

public class CrossFenceInfo {
    int id;
    int type;
    boolean inFence;

    public CrossFenceInfo(int id, int type) {
        this.id = id;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isInFence() {
        return inFence;
    }

    public void setInFence(boolean inFence) {
        this.inFence = inFence;
    }
}
