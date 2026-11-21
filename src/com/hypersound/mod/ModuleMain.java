package com.hypersound.mod;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** LSPosed 入口（新版 libxposed API）。 */
public class ModuleMain extends XposedModule {

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(4, "HyperSound", "loaded: " + param.getProcessName()
                + " systemServer=" + param.isSystemServer());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg = param.getPackageName();
        // LSPosed 只会注入到已勾选作用域的进程，这里不再用包名白名单过滤。
        Hooks.install(this, pkg, param.getDefaultClassLoader());
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        Hooks.install(this, "android", param.getClassLoader());
    }
}
