package org.bouncycastle.crypto.ec;

import org.bouncycastle.math.ec.ECPoint;

public class ECPair {
    private final ECPoint x;
    private final ECPoint y;

    public ECPair(ECPoint eCPoint, ECPoint eCPoint2) {
        this.x = eCPoint;
        this.y = eCPoint2;
    }

    public boolean equals(Object obj) {
        return obj instanceof ECPair ? equals((ECPair) obj) : false;
    }

    public boolean equals(ECPair eCPair) {
        return eCPair.getX().equals(getX()) && eCPair.getY().equals(getY());
    }

    public ECPoint getX() {
        return this.x;
    }

    public ECPoint getY() {
        return this.y;
    }

    public int hashCode() {
        return this.x.hashCode() + (37 * this.y.hashCode());
    }
}
