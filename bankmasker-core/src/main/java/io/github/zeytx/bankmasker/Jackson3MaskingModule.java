package io.github.zeytx.bankmasker;

import tools.jackson.core.Version;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.MapperBuilder;

/**
 * Jackson 3 ({@code tools.jackson}) module for per-mapper masking configuration;
 * the counterpart of the Jackson 2 {@link MaskingModule}.
 *
 * <p>Masking itself needs no module: {@link MaskData} works out of the box.
 * Register this module only to override the global {@link MaskingConfig}
 * for one mapper:
 * <pre>{@code
 * JsonMapper mapper = JsonMapper.builder()
 *     .addModule(new Jackson3MaskingModule(MaskingConfig.create().setDefaultMaskChar('#')))
 *     .build();
 * }</pre>
 *
 * @since 1.2.0
 * @see MaskingConfig
 * @see Jackson3MaskingSerializer
 */
public class Jackson3MaskingModule extends JacksonModule {

    private final MaskingConfig config;

    /**
     * Creates a module with a specific masking configuration.
     *
     * @param config the per-mapper configuration
     */
    public Jackson3MaskingModule(MaskingConfig config) {
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
        // Shared attribute on top of the builder's existing defaults (see MaskingModule)
        if (context.getOwner() instanceof MapperBuilder<?, ?> builder) {
            builder.defaultAttributes(builder.defaultAttributes()
                    .withSharedAttribute(MaskingSupport.CONFIG_KEY, config));
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

    static MaskingConfig resolveConfig(SerializationContext ctxt) {
        return MaskingSupport.configOrGlobal(ctxt == null ? null : ctxt.getAttribute(MaskingSupport.CONFIG_KEY));
    }
}
