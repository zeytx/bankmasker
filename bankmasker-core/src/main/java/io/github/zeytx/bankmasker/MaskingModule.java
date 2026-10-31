package io.github.zeytx.bankmasker;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.List;

/**
 * Jackson 2 module that allows per-{@link ObjectMapper} masking configuration.
 * For Jackson 3 use {@link Jackson3MaskingModule}.
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
        // Store the per-mapper config as a *shared* default attribute, keeping any
        // existing defaults. A per-call attribute here would back the mapper
        // defaults with a mutable map, so attributes set by any serializer during
        // one call (provider.setAttribute) would leak into later calls and threads.
        Object owner = context.getOwner();
        if (owner instanceof ObjectMapper mapper) {
            mapper.setDefaultAttributes(mapper.getSerializationConfig().getAttributes()
                    .withSharedAttribute(MaskingSupport.CONFIG_KEY, config));
        }
        context.addBeanSerializerModifier(new FieldMaskModifier(config));
    }

    /** Applies {@link MaskingConfig#maskField} rules to properties without {@link MaskData}. */
    private static final class FieldMaskModifier extends BeanSerializerModifier {

        private final MaskingConfig config;

        FieldMaskModifier(MaskingConfig config) {
            this.config = config;
        }

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig serializationConfig,
                                                         BeanDescription beanDesc,
                                                         List<BeanPropertyWriter> properties) {
            for (BeanPropertyWriter property : properties) {
                MaskType type = config.getFieldMask(property.getName());
                // An explicit serializer (e.g. from @MaskData) takes precedence
                if (type != null && !property.hasSerializer()) {
                    property.assignSerializer(new MaskingSerializer(MaskingSupport.of(type, property.getName())));
                }
            }
            return properties;
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
        return MaskingSupport.configOrGlobal(provider == null ? null : provider.getAttribute(MaskingSupport.CONFIG_KEY));
    }
}

