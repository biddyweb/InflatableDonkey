/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.horrorho.inflatabledonkey.data.backup;

import com.github.horrorho.inflatabledonkey.protocol.CloudKit;
import com.google.protobuf.ByteString;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import net.jcip.annotations.Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BackupAccountFactory.
 *
 * @author Ahseya
 */
@Immutable
public final class BackupAccountFactory {

    private static final Logger logger = LoggerFactory.getLogger(BackupAccountFactory.class);

    private static final String HMAC_KEY = "HMACKey";
    private static final String DEVICES = "devices";

    // TODO BiFunction<byte[], String, Optional<byte[]>> decrypt
    public static BackupAccount from(CloudKit.Record record, BiFunction<byte[], String, byte[]> decrypt) {
        List<CloudKit.RecordField> records = record.getRecordFieldList();

        Optional<byte[]> hmacKey = hmacKey(records)
                .map(k -> decrypt.apply(k, HMAC_KEY));

        Collection<String> devices = devices(records);

        return new BackupAccount(hmacKey, devices);
    }

    public static Optional<byte[]> hmacKey(List<CloudKit.RecordField> records) {
        return records.stream()
                .filter(value -> value.getIdentifier().getName().equals(HMAC_KEY))
                .map(CloudKit.RecordField::getValue)
                .map(CloudKit.RecordFieldValue::getBytesValue)
                .map(ByteString::toByteArray)
                .findFirst();
    }

    public static Collection<String> devices(List<CloudKit.RecordField> records) {
        return records.stream()
                .filter(value -> value.getIdentifier().getName().equals(DEVICES))
                .map(CloudKit.RecordField::getValue)
                .map(CloudKit.RecordFieldValue::getRecordFieldValueList)
                .flatMap(Collection::stream)
                .map(CloudKit.RecordFieldValue::getReferenceValue)
                .map(CloudKit.RecordReference::getRecordIdentifier)
                .map(CloudKit.RecordIdentifier::getValue)
                .map(CloudKit.Identifier::getName)
                .collect(Collectors.toList());
    }
}
