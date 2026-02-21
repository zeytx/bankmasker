package io.github.zeytx.bankmasker;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;

/**
 * Jackson module that allows per-{@link ObjectMapper} masking configuration.
 *
 * <p>Register this module to override the global {@link MaskingConfig} singleton
 * for a specific ObjectMapper instance. This is useful in multi-tenant applications
 * or parallel tests where different configurations are needed.
 *
 * <p>Example:
 * <pre>{@code
 * MaskingConfig perMapperConfig = MaskingConfig.create()
 *     .setEnabled(true)
 *     .setDefaultMaskChar('#');
 *
 * ObjectMapper mapper = new ObjectMapper();
 * mapper.registerModule(new MaskingModule(perMapperConfig));
 * }</pre>
 *
 * <p>If no {@link MaskingModule} is registered, the serializer falls back to the
 * global {@link MaskingConfig#getInstance()} singleton.
 *
 * @since 1.1.0
 * @see MaskingConfig
 * @see MaskingSerializer
 */
public class MaskingModule extends Module {

    /**
     * Attribute key for per-mapper config stored in Jackson's context attributes.
     */
    static final Object CONFIG_KEY = MaskingModule.class.getName() + ".config";

    private final MaskingConfig config;

    /**
     * Creates a module with a specific masking configuration.
     *
     * @param config the per-mapper configuration
     */
    public MaskingModule(MaskingConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("MaskingConfig must not be null");
        }
        this.config = config;
    }

    @Override
    public String getModuleName() {
        return "BankMaskerModule";
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext context) {
        // Store the per-mapper config as a default attribute on the ObjectMapper
        Object owner = context.getOwner();
        if (owner instanceof ObjectMapper mapper) {
            mapper.setDefaultAttributes(
                    ContextAttributes.getEmpty().withPerCallAttribute(CONFIG_KEY, config));
        }
    }

    /**
     * Returns the masking configuration associated with this module.
     *
     * @return the masking config
     */
    public MaskingConfig getConfig() {
        return config;
    }

    /**
     * Resolves the {@link MaskingConfig} from the per-ObjectMapper context,
     * falling back to the global singleton.
     *
     * @param provider the serializer provider
     * @return the resolved config
     */
    static MaskingConfig resolveConfig(SerializerProvider provider) {
        if (provider != null) {
            Object attr = provider.getAttribute(CONFIG_KEY);
            if (attr instanceof MaskingConfig perMapper) {
                return perMapper;
            }
        }
        return MaskingConfig.getInstance();
    }
}

