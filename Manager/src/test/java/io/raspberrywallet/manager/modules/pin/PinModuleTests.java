package io.raspberrywallet.manager.modules.pin;

import io.raspberrywallet.contract.RequiredInputNotFound;
import io.raspberrywallet.manager.cryptography.crypto.exceptions.DecryptionException;
import io.raspberrywallet.manager.cryptography.crypto.exceptions.EncryptionException;
import io.raspberrywallet.manager.modules.Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinModuleTests {
    
    private final static String data = "secret data for encryption";
    
    private Module<PinConfig> pinModule;
    
    @BeforeEach
    public void initializeModule() throws IllegalAccessException, InstantiationException {
        pinModule = new PinModule();
    }
    
    @Test
    public void PinModuleConstructorDoesNotThrow() throws IllegalAccessException, InstantiationException {
        Module<PinConfig> pinModule = new PinModule();
    }

    
    @Test
    public void PinModuleEncryptsAndDecryptsCorrectly() throws EncryptionException, RequiredInputNotFound, DecryptionException {
        pinModule.setInput("pin", "1234");

        byte[] encryptedData = pinModule.validateAndEncrypt(data.getBytes());
        byte[] decryptedData = pinModule.validateAndDecrypt(encryptedData);
        
        assertTrue(Arrays.equals(data.getBytes(), decryptedData));
    }
    
    @Test
    public void DecryptionDoesThrowWithWrongPin() throws EncryptionException, RequiredInputNotFound {
        pinModule.setInput("pin", "1234");

        byte[] encryptedData = pinModule.validateAndEncrypt(data.getBytes());
        pinModule.clearInputs();
        pinModule.setInput("pin","4567");
        assertThrows(DecryptionException.class, () -> pinModule.validateAndDecrypt(encryptedData));
    }
    
}
