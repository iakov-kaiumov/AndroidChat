package helfi2012.chat;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyPair;

import helfi2012.chat.encryption.RSAEncryptionUtil;

import static org.junit.Assert.*;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();
        String text = "Hello";
        KeyPair keyPair = RSAEncryptionUtil.INSTANCE.generateKeys();
        String eText = RSAEncryptionUtil.INSTANCE.encrypt(text, keyPair.getPublic());
        String dText = RSAEncryptionUtil.INSTANCE.decrypt(eText, keyPair.getPrivate());
        assertEquals(eText, dText);
    }
}
