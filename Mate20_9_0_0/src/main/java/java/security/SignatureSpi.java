package java.security;

import java.nio.ByteBuffer;
import java.security.spec.AlgorithmParameterSpec;
import sun.security.jca.JCAUtil;

public abstract class SignatureSpi {
    protected SecureRandom appRandom = null;

    @Deprecated
    protected abstract Object engineGetParameter(String str) throws InvalidParameterException;

    protected abstract void engineInitSign(PrivateKey privateKey) throws InvalidKeyException;

    protected abstract void engineInitVerify(PublicKey publicKey) throws InvalidKeyException;

    @Deprecated
    protected abstract void engineSetParameter(String str, Object obj) throws InvalidParameterException;

    protected abstract byte[] engineSign() throws SignatureException;

    protected abstract void engineUpdate(byte b) throws SignatureException;

    protected abstract void engineUpdate(byte[] bArr, int i, int i2) throws SignatureException;

    protected abstract boolean engineVerify(byte[] bArr) throws SignatureException;

    protected void engineInitSign(PrivateKey privateKey, SecureRandom random) throws InvalidKeyException {
        this.appRandom = random;
        engineInitSign(privateKey);
    }

    protected void engineUpdate(ByteBuffer input) {
        if (input.hasRemaining()) {
            try {
                int pos;
                if (input.hasArray()) {
                    byte[] b = input.array();
                    int ofs = input.arrayOffset();
                    pos = input.position();
                    int lim = input.limit();
                    engineUpdate(b, ofs + pos, lim - pos);
                    input.position(lim);
                } else {
                    int len = input.remaining();
                    byte[] b2 = new byte[JCAUtil.getTempArraySize(len)];
                    while (len > 0) {
                        pos = Math.min(len, b2.length);
                        input.get(b2, 0, pos);
                        engineUpdate(b2, 0, pos);
                        len -= pos;
                    }
                }
            } catch (SignatureException e) {
                throw new ProviderException("update() failed", e);
            }
        }
    }

    protected int engineSign(byte[] outbuf, int offset, int len) throws SignatureException {
        byte[] sig = engineSign();
        if (len < sig.length) {
            throw new SignatureException("partial signatures not returned");
        } else if (outbuf.length - offset >= sig.length) {
            System.arraycopy(sig, 0, outbuf, offset, sig.length);
            return sig.length;
        } else {
            throw new SignatureException("insufficient space in the output buffer to store the signature");
        }
    }

    protected boolean engineVerify(byte[] sigBytes, int offset, int length) throws SignatureException {
        byte[] sigBytesCopy = new byte[length];
        System.arraycopy(sigBytes, offset, sigBytesCopy, 0, length);
        return engineVerify(sigBytesCopy);
    }

    protected void engineSetParameter(AlgorithmParameterSpec params) throws InvalidAlgorithmParameterException {
        throw new UnsupportedOperationException();
    }

    protected AlgorithmParameters engineGetParameters() {
        throw new UnsupportedOperationException();
    }

    public Object clone() throws CloneNotSupportedException {
        if (this instanceof Cloneable) {
            return super.clone();
        }
        throw new CloneNotSupportedException();
    }
}
