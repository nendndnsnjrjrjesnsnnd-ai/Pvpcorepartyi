package dev.pvpcore.party.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public final class Text {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private Text() {}

    /** Renkli (&) metni Component'e cevirir. */
    public static Component c(String s) {
        return LEGACY.deserialize(s == null ? "" : s);
    }

    /** Esya adi/lore icin: italik kapali. */
    public static Component item(String s) {
        return c(s).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> items(List<String> lines) {
        List<Component> out = new ArrayList<>();
        if (lines != null) {
            for (String l : lines) out.add(item(l));
        }
        return out;
    }
}
