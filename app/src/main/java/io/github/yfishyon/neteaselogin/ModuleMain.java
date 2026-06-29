package io.github.yfishyon.neteaselogin;

import android.os.Build;
import android.util.Log;

import org.json.JSONArray;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class ModuleMain extends XposedModule {
    private static final String TAG = "NeteaseLogin";
    private static final String MUMU_BUILD_ID = "V417IR";
    private static final int[] PLATFORMS = {1, 2, 8};

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "loaded: " + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!param.isFirstPackage()) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            hookSystemProperties();
            hookJsonObject();
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "init failed", t);
        }
    }

    private void hookSystemProperties() throws Throwable {
        Class<?> cls = Class.forName("android.os.SystemProperties");
        hook(cls.getDeclaredMethod("get", String.class, String.class)).intercept(chain -> {
            String key = (String) chain.getArg(0);
            if ("ro.build.id".equals(key)) return MUMU_BUILD_ID;
            return chain.proceed();
        });
        hook(cls.getDeclaredMethod("get", String.class)).intercept(chain -> {
            String key = (String) chain.getArg(0);
            if ("ro.build.id".equals(key)) return MUMU_BUILD_ID;
            return chain.proceed();
        });
    }

    private void hookJsonObject() throws Throwable {
        Class<?> cls = Class.forName("org.json.JSONObject");
        hook(cls.getDeclaredMethod("optBoolean", String.class, boolean.class)).intercept(chain -> {
            String key = (String) chain.getArg(0);
            if ("select_platform".equals(key)) return Boolean.TRUE;
            return chain.proceed();
        });
        hook(cls.getDeclaredMethod("optBoolean", String.class)).intercept(chain -> {
            String key = (String) chain.getArg(0);
            if ("select_platform".equals(key)) return Boolean.TRUE;
            return chain.proceed();
        });
        hook(cls.getDeclaredMethod("optJSONArray", String.class)).intercept(chain -> {
            String key = (String) chain.getArg(0);
            if ("select_platforms".equals(key)) {
                JSONArray arr = new JSONArray();
                for (int p : PLATFORMS) arr.put(p);
                return arr;
            }
            return chain.proceed();
        });
    }
}
