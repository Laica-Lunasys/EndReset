package de.V10lator.EndReset;

import java.io.Serializable;

class EndResetWorld implements Serializable {
    private static final long serialVersionUID = -3797024945889993918L;

    final long hours;
    long lastReset;

    /**
     * コンストラクタ
     * 
     * @param hours
     */
    public EndResetWorld(long hours) {
        this.hours = hours * 60 * 60;
        lastReset = System.currentTimeMillis() * 1000;
    }
}
