package dev.nefobric.loader.metadata;

import dev.nefobric.loader.Env;

/** 只在某一端生效的模组或依赖。 */
public enum SideRestriction {
    ANY,
    CLIENT,
    SERVER;

    public boolean appliesTo(Env env) {
        return this == ANY
                || (this == CLIENT && env == Env.CLIENT)
                || (this == SERVER && env == Env.SERVER);
    }

    public String displayName() {
        return this == SERVER ? Env.SERVER.displayName() : Env.CLIENT.displayName();
    }
}
