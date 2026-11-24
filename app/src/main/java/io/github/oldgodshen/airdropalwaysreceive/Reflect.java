package io.github.oldgodshen.airdropalwaysreceive;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

final class Reflect {
    private Reflect() {
    }

    static Field findField(Class<?> owner, String... names) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    // Try the next candidate.
                }
            }
        }
        return null;
    }

    static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Try the superclass.
            }
        }
        return null;
    }

    static Method findAnyMethod(Class<?> owner, String... names) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                for (String name : names) {
                    if (method.getName().equals(name)) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
        }
        return null;
    }

    static Object getField(Object target, Field field) {
        try {
            return field.get(target);
        } catch (Throwable throwable) {
            Logs.warn("field read failed: " + field.getName(), throwable);
            return null;
        }
    }

    static void setField(Object target, Field field, Object value) {
        try {
            field.set(target, value);
        } catch (Throwable throwable) {
            Logs.warn("field write failed: " + field.getName(), throwable);
        }
    }

    static void setInt(Object target, Field field, int value) {
        try {
            field.setInt(target, value);
        } catch (Throwable throwable) {
            Logs.warn("int field write failed: " + field.getName(), throwable);
        }
    }

    static void invoke(Object target, Method method, Object... args) {
        try {
            method.invoke(target, args);
        } catch (Throwable throwable) {
            Logs.warn("method invoke failed: " + method.getName(), throwable);
        }
    }

    static String describe(Class<?> type) {
        return type == null ? "null" : type.getName() + Arrays.toString(type.getDeclaredFields());
    }
}
