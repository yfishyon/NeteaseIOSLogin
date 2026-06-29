package io.github.yfishyon.neteaselogin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;

import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

import java.lang.reflect.Method;
import java.security.MessageDigest;

public class ModuleMain extends XposedModule {
    private static final String TAG = "NeteaseLogin";
    private static final String MODULE_PKG = "io.github.yfishyon.neteaselogin";
    private static final String EXPECTED_SIG_SHA256 = "b4004d4649124b3ed305fc0368fe71b52fb8c282923f8cafe447afbe1ea67353";
    private static final String OPEN_SOURCE_URL = "https://github.com/yfishyon/NeteaseIOSLogin";
    private static final String PREF_NAME = "neteaselogin_disclaimer";
    private static final String PREF_ACK = "ack";
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
        if (!verifySelfSignature()) {
            log(Log.ERROR, TAG, "签名校验失败，模块被篡改，不加载");
            return;
        }
        try {
            hookSystemProperties();
            hookJsonObject();
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook init failed", t);
        }
        showDisclaimer();
    }

    private boolean verifySelfSignature() {
        try {
            String apkPath = getModuleApkPath();
            if (apkPath == null) {
                log(Log.WARN, TAG, "无模块 APK 路径，跳过签名验证");
                return true;
            }
            java.util.jar.JarFile jar = new java.util.jar.JarFile(apkPath);
            java.util.jar.JarEntry sigEntry = null;
            java.util.Enumeration<java.util.jar.JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                java.util.jar.JarEntry je = e.nextElement();
                String n = je.getName();
                if (n.startsWith("META-INF/") && (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"))) {
                    sigEntry = je;
                    break;
                }
            }
            if (sigEntry == null) {
                jar.close();
                log(Log.WARN, TAG, "APK 无 v1 签名块(META-INF/*.RSA)，跳过");
                return true;
            }
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            java.util.Collection<? extends java.security.cert.Certificate> certs =
                    cf.generateCertificates(jar.getInputStream(sigEntry));
            jar.close();
            if (certs.isEmpty()) return true;
            String sha = sha256Hex(certs.iterator().next().getEncoded());
            boolean ok = EXPECTED_SIG_SHA256.equalsIgnoreCase(sha);
            log(Log.INFO, TAG, "签名 SHA-256=" + sha + " ok=" + ok);
            return ok;
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "verify sig err", t);
            return false;
        }
    }

    private String getModuleApkPath() {
        try {
            android.content.pm.ApplicationInfo ai = this.getModuleApplicationInfo();
            return ai != null ? ai.sourceDir : null;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "getModuleApkPath err: " + t);
            return null;
        }
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void showDisclaimer() {
        try {
            Class<?> activityCls = Class.forName("android.app.Activity");
            Method onResume = activityCls.getDeclaredMethod("onResume");
            final HookHandle[] ref = new HookHandle[1];
            ref[0] = hook(onResume).intercept(chain -> {
                chain.proceed();
                HookHandle h = ref[0];
                if (h != null) { h.unhook(); ref[0] = null; }
                Activity act = (Activity) chain.getThisObject();
                SharedPreferences sp = act.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                if (sp.getBoolean(PREF_ACK, false)) return null;
                new AlertDialog.Builder(act)
                        .setTitle("免责声明")
                        .setMessage("本模块完全免费，仅供学习研究，风险自负。\n"
                                + "使用修改版客户端可能导致账号封禁，建议小号测试。\n\n"
                                + "本模块是免费的，如果你是购买的，请用此截图去退款。")
                        .setNegativeButton("开源地址", (d, w) -> {
                            try {
                                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(OPEN_SOURCE_URL)));
                            } catch (Throwable ignored) { }
                        })
                        .setPositiveButton("确认（不再显示）", (d, w) -> {
                            sp.edit().putBoolean(PREF_ACK, true).apply();
                        })
                        .setCancelable(false)
                        .show();
                return null;
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "showDisclaimer err", t);
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
