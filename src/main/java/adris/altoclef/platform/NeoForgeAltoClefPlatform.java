package adris.altoclef.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

public final class NeoForgeAltoClefPlatform implements AltoClefPlatform {
    private long ticks;

    @Override
    public boolean playerReady() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.getConnection() != null;
    }

    @Override
    public long tickCount() {
        return ticks++;
    }

    @Override
    public void log(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        } else {
            System.out.println("[AltoClef] " + message);
        }
    }

    @Override
    public void stopPathing() {
        runBaritone("stop");
    }

    @Override
    public boolean runBaritone(String command) {
        return runBaritoneReflective(command);
    }

    private static boolean runBaritoneReflective(String command) {
        try {
            Class<?> api = Class.forName("baritone.c");
            Object provider = invokeNoArgByReturn(api, null, "baritone.api.IBaritoneProvider");
            Object baritone = invokeNoArgByReturn(provider.getClass(), provider, "baritone.d");
            Object commandManager = invokeNoArgByReturn(baritone.getClass(), baritone, "baritone.hm");
            Method execute = methodByReturn(commandManager.getClass(), "a", boolean.class, String.class);
            Object result = execute.invoke(commandManager, command);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeNoArgByReturn(Class<?> type, Object target, String returnType) throws Exception {
        for (Method method : type.getMethods()) {
            if (method.getName().equals("a") && method.getParameterCount() == 0 && method.getReturnType().getName().equals(returnType)) {
                return method.invoke(target);
            }
        }
        throw new NoSuchMethodException(returnType);
    }

    private static Method methodByReturn(Class<?> type, String name, Class<?> returnType, Class<?>... params) throws Exception {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getReturnType().equals(returnType) && sameParams(method, params)) {
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean sameParams(Method method, Class<?>[] params) {
        Class<?>[] actual = method.getParameterTypes();
        if (actual.length != params.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!actual[i].equals(params[i])) {
                return false;
            }
        }
        return true;
    }
}
