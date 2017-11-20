package helfi2012.chat;

import org.junit.Test;

import java.security.KeyPair;

import helfi2012.chat.encryption.RSAEncryptionUtil;

public class UnitTest {

    @Test
    public void test() {
        String text = "hello";
        KeyPair keyPair = RSAEncryptionUtil.INSTANCE.generateKeys();
        RSAEncryptionUtil.INSTANCE.encrypt(text, keyPair.getPublic());
    }

}