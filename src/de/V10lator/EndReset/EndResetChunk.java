package de.V10lator.EndReset;

import java.io.Serializable;

class EndResetChunk implements Serializable {
    private static final long serialVersionUID = -7775856958559739382L;

    final String world;
    final int x;
    final int z;
    long v;

    /**
     * コンストラクタ
     * 
     * @param world
     * @param x
     * @param z
     */
    public EndResetChunk(String world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((world == null) ? 0 : world.hashCode());
        result = prime * result + x;
        result = prime * result + z;
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (!(obj instanceof EndResetChunk)) return false;

        EndResetChunk other = (EndResetChunk) obj;
        if (world.equals(other.world) && x == other.x && z == other.z) { return true; }

        return false;
    }
}
