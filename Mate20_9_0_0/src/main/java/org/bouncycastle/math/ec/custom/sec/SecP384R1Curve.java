package org.bouncycastle.math.ec.custom.sec;

import java.math.BigInteger;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECCurve.AbstractFp;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECLookupTable;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.raw.Nat;
import org.bouncycastle.util.encoders.Hex;

public class SecP384R1Curve extends AbstractFp {
    private static final int SecP384R1_DEFAULT_COORDS = 2;
    public static final BigInteger q = new BigInteger(1, Hex.decode("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFF"));
    protected SecP384R1Point infinity;

    public SecP384R1Curve() {
        super(q);
        this.infinity = new SecP384R1Point(this, null, null);
        this.a = fromBigInteger(new BigInteger(1, Hex.decode("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFC")));
        this.b = fromBigInteger(new BigInteger(1, Hex.decode("B3312FA7E23EE7E4988E056BE3F82D19181D9C6EFE8141120314088F5013875AC656398D8A2ED19D2A85C8EDD3EC2AEF")));
        this.order = new BigInteger(1, Hex.decode("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC7634D81F4372DDF581A0DB248B0A77AECEC196ACCC52973"));
        this.cofactor = BigInteger.valueOf(1);
        this.coord = 2;
    }

    protected ECCurve cloneCurve() {
        return new SecP384R1Curve();
    }

    public ECLookupTable createCacheSafeLookupTable(ECPoint[] eCPointArr, int i, final int i2) {
        final int[] iArr = new int[((i2 * 12) * 2)];
        int i3 = 0;
        int i4 = i3;
        while (i3 < i2) {
            ECPoint eCPoint = eCPointArr[i + i3];
            Nat.copy(12, ((SecP384R1FieldElement) eCPoint.getRawXCoord()).x, 0, iArr, i4);
            i4 += 12;
            Nat.copy(12, ((SecP384R1FieldElement) eCPoint.getRawYCoord()).x, 0, iArr, i4);
            i4 += 12;
            i3++;
        }
        return new ECLookupTable() {
            public int getSize() {
                return i2;
            }

            public ECPoint lookup(int i) {
                int[] create = Nat.create(12);
                int[] create2 = Nat.create(12);
                int i2 = 0;
                int i3 = i2;
                while (i2 < i2) {
                    int i4 = ((i2 ^ i) - 1) >> 31;
                    for (int i5 = 0; i5 < 12; i5++) {
                        create[i5] = create[i5] ^ (iArr[i3 + i5] & i4);
                        create2[i5] = create2[i5] ^ (iArr[(i3 + 12) + i5] & i4);
                    }
                    i3 += 24;
                    i2++;
                }
                return SecP384R1Curve.this.createRawPoint(new SecP384R1FieldElement(create), new SecP384R1FieldElement(create2), false);
            }
        };
    }

    protected ECPoint createRawPoint(ECFieldElement eCFieldElement, ECFieldElement eCFieldElement2, boolean z) {
        return new SecP384R1Point(this, eCFieldElement, eCFieldElement2, z);
    }

    protected ECPoint createRawPoint(ECFieldElement eCFieldElement, ECFieldElement eCFieldElement2, ECFieldElement[] eCFieldElementArr, boolean z) {
        return new SecP384R1Point(this, eCFieldElement, eCFieldElement2, eCFieldElementArr, z);
    }

    public ECFieldElement fromBigInteger(BigInteger bigInteger) {
        return new SecP384R1FieldElement(bigInteger);
    }

    public int getFieldSize() {
        return q.bitLength();
    }

    public ECPoint getInfinity() {
        return this.infinity;
    }

    public BigInteger getQ() {
        return q;
    }

    public boolean supportsCoordinateSystem(int i) {
        return i == 2;
    }
}
