/* 
 * The MIT License
 *
 * Copyright 2016 Ahseya.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.horrorho.inflatabledonkey.pcs.xfile;

import com.github.horrorho.inflatabledonkey.crypto.AESWrap;
import com.github.horrorho.inflatabledonkey.crypto.Curve25519;
import com.github.horrorho.inflatabledonkey.keybag.KeyBag;
import com.github.horrorho.inflatabledonkey.keybag.KeyBags;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Optional;
import net.jcip.annotations.Immutable;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FileKeyAssistant.
 *
 * @author Ahseya
 */
@Immutable
public final class FileKeyAssistant {

    private static final Logger logger = LoggerFactory.getLogger(FileKeyAssistant.class);

    public static Optional<byte[]> uuid(byte[] fileKey) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(fileKey);
            byte[] uuid = new byte[0x10];
            buffer.get(uuid);
            return Optional.of(uuid);

        } catch (BufferUnderflowException ex) {
            logger.warn("-- uuid() - BufferUnderflowException: {}", ex);
            return Optional.empty();
        }
    }

    public static Optional<byte[]> unwrap(KeyBags keyBags, int protectionClass, byte[] fileKey) {
        return uuid(fileKey)
                .flatMap(keyBags::keybag)
                .flatMap(keyBag -> unwrap(keyBag, protectionClass, fileKey));
    }

    public static Optional<byte[]> unwrap(KeyBag keyBag, int protectionClass, byte[] fileKey) {
        Optional<byte[]> optionalPublicKey = keyBag.publicKey(protectionClass);
        Optional<byte[]> optionalPrivateKey = keyBag.privateKey(protectionClass);
        Optional<byte[]> uuid = uuid(fileKey);

        boolean uuidMatch = uuid.map(u -> Arrays.areEqual(keyBag.uuid(), u))
                .orElse(false);
        if (!uuidMatch) {
            logger.warn("-- unwrap() - fileKey/ keybag uuid mismatch: 0x{} 0x{}",
                    uuid.map(Hex::toHexString), Hex.toHexString(keyBag.uuid()));
        }

        return optionalPublicKey.isPresent() && optionalPrivateKey.isPresent()
                ? unwrap(optionalPublicKey.get(), optionalPrivateKey.get(), protectionClass, fileKey)
                : Optional.empty();
    }

    public static Optional<byte[]>
            unwrap(byte[] myPublicKey, byte[] myPrivateKey, int protectionClass, byte[] fileKey) {

        try {
            // Version 2 support only.
            ByteBuffer buffer = ByteBuffer.wrap(fileKey);

            byte[] uuid = new byte[0x10];
            buffer.get(uuid);

            buffer.getInt(); // ignored
            buffer.getInt(); // ignored
            int pc = buffer.getInt();
            buffer.getInt(); // ignored
            int length = buffer.getInt();

            byte[] longKey = new byte[buffer.limit() - buffer.position()];
            buffer.get(longKey);

            if (longKey.length != length) {
                logger.warn("-- unwrap() - incongruent key length");
            }

            if (pc != protectionClass) {
                logger.warn("-- unwrap() - incongruent protection class");
            }

            return FileKeyAssistant.unwrap(myPublicKey, myPrivateKey, longKey);

        } catch (BufferUnderflowException ex) {
            logger.warn("-- unwrap() - BufferUnderflowException: {}", ex);
            return Optional.empty();
        }
    }

    public static Optional<byte[]> unwrap(byte[] myPublicKey, byte[] myPrivateKey, byte[] longKey) {
        byte[] otherPublicKey = new byte[0x20];
        byte[] wrappedKey = new byte[longKey.length - 0x20];

        ByteBuffer buffer = ByteBuffer.wrap(longKey);
        buffer.get(otherPublicKey);
        buffer.get(wrappedKey);

        return curve25519Unwrap(myPublicKey, myPrivateKey, otherPublicKey, wrappedKey);
    }

    public static Optional<byte[]> curve25519Unwrap(
            byte[] myPublicKey,
            byte[] myPrivateKey,
            byte[] otherPublicKey,
            byte[] wrappedKey) {

        SHA256Digest sha256 = new SHA256Digest();

        byte[] shared = Curve25519.agreement(otherPublicKey, myPrivateKey);
        byte[] pad = new byte[]{0x00, 0x00, 0x00, 0x01};
        byte[] hash = new byte[sha256.getDigestSize()];

        sha256.reset();
        sha256.update(pad, 0, pad.length);
        sha256.update(shared, 0, shared.length);
        sha256.update(otherPublicKey, 0, otherPublicKey.length);
        sha256.update(myPublicKey, 0, myPublicKey.length);
        sha256.doFinal(hash, 0);

        return AESWrap.unwrap(hash, wrappedKey);
    }
}
// TODO buffer underflow exceptions
