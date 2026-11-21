package dev.nefobric.loader;

import dev.nefobric.loader.util.Messages;

/** 运行端：客户端或专用服务端。 */
public enum Env {
    CLIENT,
    SERVER;

    public String displayName() {
        return Messages.tr(this == CLIENT ? "env.client" : "env.server");
    }
}
