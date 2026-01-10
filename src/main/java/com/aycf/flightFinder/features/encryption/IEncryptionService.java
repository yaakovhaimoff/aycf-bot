package com.aycf.flightFinder.features.encryption;

public interface IEncryptionService {

    EncryptedData encrypt(String plainText);

    String decrypt(String cipherTextBase64, String ivBase64);

    record EncryptedData(String cipherTextBase64, String ivBase64) {}
}
