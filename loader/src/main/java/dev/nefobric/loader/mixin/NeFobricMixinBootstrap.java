package dev.nefobric.loader.mixin;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/** Mixin 启动时第一个找的服务：告诉它用 {@link NeFobricMixinService}。 */
public final class NeFobricMixinBootstrap implements IMixinServiceBootstrap {
    @Override
    public String getName() {
        return "NeFobric";
    }

    @Override
    public String getServiceClassName() {
        return NeFobricMixinService.class.getName();
    }

    @Override
    public void bootstrap() {
        // 不需要额外准备：类加载器在 Mixin 启动之前就建好了
    }
}
