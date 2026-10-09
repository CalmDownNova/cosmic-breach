package com.cosmicbreach;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every item this mod registers, found without starting the game: each class of the mod that holds a static
 * {@code DeferredRegister.Items} is read for its entries. A new registry class is found by itself, so a test built on this
 * covers items added later.
 */
public final class ModItemIds {
    private ModItemIds() {
    }

    /** The paths (without the namespace) of every registered item, sorted. */
    public static TreeSet<String> paths() {
        TreeSet<String> out = new TreeSet<>();
        try {
            Path root = Paths.get(CosmicBreach.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            List<String> names = new ArrayList<>();
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                    String rel = root.relativize(p).toString().replace('\\', '/').replace('/', '.');
                    names.add(rel.substring(0, rel.length() - ".class".length()));
                });
            }
            for (String name : names) {
                if (!name.startsWith("com.cosmicbreach.") || name.contains(".client.")) {
                    continue;
                }
                try {
                    Class<?> c = Class.forName(name, false, CosmicBreach.class.getClassLoader());
                    for (Field f : c.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers()) && DeferredRegister.Items.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            DeferredRegister.Items items = (DeferredRegister.Items) f.get(null);
                            for (DeferredHolder<?, ?> holder : items.getEntries()) {
                                out.add(holder.getId().getPath());
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // a class that needs the game to load has no item register of interest here
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }
}
