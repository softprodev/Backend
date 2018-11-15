package io.raspberrywallet.manager.modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stasbar.Logger;
import io.raspberrywallet.contract.RequiredInputNotFound;
import io.raspberrywallet.manager.Configuration;
import io.raspberrywallet.manager.cryptography.crypto.exceptions.DecryptionException;
import io.raspberrywallet.manager.cryptography.crypto.exceptions.EncryptionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Module base class
 *
 * @param <Config> type of module specific configuration class implementing ModuleConfig base interface
 */
public abstract class Module<Config extends ModuleConfig> {
    @NotNull
    private String statusString;

    @NotNull
    private HashMap<String, String> input = new HashMap<>();

    /**
     * Configuration object available for every module
     * This object should hold every customizable module specific property
     */
    protected Config configuration;

    /**
     * This constructor enforce that the state and configuration is always present
     *
     * @param initialStatusString - initial module status string
     * @param configClass         - class representation of module specific Config type,
     *                            required for dynamic Config class initialization, either from file and default value
     */
    public Module(@NotNull String initialStatusString, Class<Config> configClass)
            throws IllegalAccessException, InstantiationException {

        statusString = initialStatusString;
        configuration = configClass.newInstance();
    }

    /**
     * This constructor tries to parse configuration from yaml file else initialize with default one
     * This constructor is required for dynamic instantiation
     *
     * @param initialStatusString - initial module status string
     */
    public Module(@NotNull String initialStatusString, Configuration.ModulesConfiguration modulesConfiguration,
                  Class<Config> configClass) throws IllegalAccessException, InstantiationException {

        statusString = initialStatusString;
        Config newConfiguration = parseConfigurationFrom(modulesConfiguration, configClass);
        if (newConfiguration == null) newConfiguration = configClass.newInstance();

        configuration = newConfiguration;
    }

    /**
     * Parses config yaml file representation to module specific Config object
     *
     * @param moduleConfiguration whole `modules` node of yaml file
     * @param configClass         class representation of module specific Config type
     * @return module specific config object
     */
    private Config parseConfigurationFrom(Configuration.ModulesConfiguration moduleConfiguration,
                                          Class<Config> configClass) {

        String configId = getId();
        JsonNode jsonNode = moduleConfiguration.get(configId);
        ObjectMapper mapper = new ObjectMapper();
        try {
            return configuration = mapper.treeToValue(jsonNode, configClass);
        } catch (IOException e) {
            Logger.err("Failed to parse configuration");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Used in all sort of identifications like config.yaml, internal module mapping and UI naming.
     * For now, it's just simplified SimpleClassName
     *
     * @return module identifier.
     */
    public String getId() {
        return this.getClass().getSimpleName();
    }

    public io.raspberrywallet.contract.module.Module asServerModule() {
        return new io.raspberrywallet.contract.module.Module(getId(), getId(), getDescription(), getHtmlUi()) {
        };
    }

    public abstract String getDescription();

    protected abstract void validateInputs() throws RequiredInputNotFound;

    /**
     * @param keyPart - decrypted key part
     * @return encrypted payload
     */
    public byte[] validateAndEncrypt(byte[] keyPart) throws RequiredInputNotFound, EncryptionException {
        validateInputs();
        return encrypt(keyPart);
    }

    /**
     * @param keyPart - unencrypted key part
     * @return encrypted payload
     */
    protected abstract byte[] encrypt(byte[] keyPart) throws EncryptionException, RequiredInputNotFound;


    /**
     * @param keyPart - encrypted key part
     * @return decrypted payload
     */
    public byte[] validateAndDecrypt(byte[] keyPart) throws RequiredInputNotFound, DecryptionException {
        validateInputs();
        return decrypt(keyPart);
    }

    /**
     * @param payload - encrypted payload
     * @return decrypted key part
     */
    protected abstract byte[] decrypt(byte[] payload) throws DecryptionException;

    /**
     * this function should prepare module before consecutive use.
     * Manager should call this.
     */
    public abstract void register();

    /**
     * this function should return HTML UI form or null if not required
     */
    @Nullable
    public abstract String getHtmlUi();

    /**
     * Returns status of the module to show to the user
     *
     * @return message
     */
    public String getStatusString() {
        return statusString == null ? "null" : statusString;
    }

    /**
     * Setting the status message for the user
     *
     * @param status - new status
     */
    public void setStatusString(@NotNull String status) {
        this.statusString = status;
    }

    /**
     * Sets input for this Module from user
     *
     * @param key   - key of the parameter
     * @param value - value of the parameter
     */
    public void setInput(String key, String value) {
        input.put(key, value);
    }

    /**
     * Sets inputs for this Module from user
     */
    public void setInputs(Map<String, String> inputs) {
        input.putAll(inputs);
    }

    /**
     * Checks if user has submitted any input
     *
     * @param key - key of the parameter
     * @return - if key exists
     */
    public boolean hasInput(String key) {
        return input.containsKey(key);
    }

    /**
     * Gets the value which user has submitted
     *
     * @param key - parameter key
     * @return - value of the parameter
     */
    @Nullable
    public String getInput(String key) {
        return input.get(key);
    }

    /**
     * Clear the user inputs, prepare for new
     */
    public void clearInputs() {
        input.clear();
    }


    /**
     * Module info formatted as JSON.
     */
    @Override
    public String toString() {
        return "{\"id\":\"" + getId() + "\", \"status\":\"" + getStatusString() + "\"}";
    }

}