package io.github.zeytx.bankmasker;

/**
 * Internal strategy variant that receives the resolved {@link MaskingConfig}
 * explicitly, so built-in mask types honor per-ObjectMapper configuration
 * (registered via {@link MaskingModule}) instead of always reading the
 * global singleton.
 *
 * @since 1.1.0
 */
@FunctionalInterface
interface ConfigAwareStrategy {

    String mask(String value, MaskingConfig config);

    /**
     * Adapts a config-aware strategy to the public {@link MaskingStrategy}
     * contract: the single-arg method uses the global singleton, while the
     * two-arg overload delegates the given config.
     */
    static MaskingStrategy adapt(ConfigAwareStrategy strategy) {
        return new MaskingStrategy() {
            @Override
            public String mask(String value) {
                return strategy.mask(value, MaskingConfig.getInstance());
            }

            @Override
            public String mask(String value, MaskingConfig config) {
                return strategy.mask(value, config);
            }
        };
    }
}
