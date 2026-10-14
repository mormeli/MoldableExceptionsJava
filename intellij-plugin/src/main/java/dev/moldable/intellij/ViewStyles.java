package dev.moldable.intellij;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Maps the style names views use ("error", "ok", "accent1", ...) to colors. Every color is a light/dark pair, so the
 * views read well in both IntelliJ themes.
 */
final class ViewStyles {

    static final JBColor ERROR_BACKGROUND = new JBColor(new Color(0xFFDAD6), new Color(0x5C2B2B));
    static final JBColor ERROR_FOREGROUND = new JBColor(new Color(0xB3261E), new Color(0xFF8A80));
    static final JBColor OK_BACKGROUND = new JBColor(new Color(0xDDF2DD), new Color(0x2D4A31));
    static final JBColor OK_FOREGROUND = new JBColor(new Color(0x2E7D32), new Color(0x8BC98E));
    static final JBColor WARNING_BACKGROUND = new JBColor(new Color(0xFFF1C2), new Color(0x4D4320));
    static final JBColor MUTED_FOREGROUND = new JBColor(new Color(0x8C8C8C), new Color(0x7F848E));

    /** Distinct, readable colors for nesting levels (like rainbow brackets). */
    private static final JBColor[] ACCENTS = {
            new JBColor(new Color(0xB07D00), new Color(0xE5C07B)),
            new JBColor(new Color(0x8E24AA), new Color(0xC678DD)),
            new JBColor(new Color(0x1565C0), new Color(0x61AFEF)),
            new JBColor(new Color(0x00897B), new Color(0x56B6C2)),
            new JBColor(new Color(0xD84315), new Color(0xD19A66)),
            new JBColor(new Color(0x2E7D32), new Color(0x98C379)),
    };

    private ViewStyles() {
    }

    /** Text attributes for a span in a highlighted-text view, or null for an unknown style. */
    static @Nullable TextAttributes attributes(@Nullable String style) {
        if (style == null) {
            return null;
        }
        switch (style) {
            case "error":
                return new TextAttributes(null, ERROR_BACKGROUND, JBColor.RED, EffectType.WAVE_UNDERSCORE, Font.BOLD);
            case "warning":
                return new TextAttributes(null, WARNING_BACKGROUND, null, null, Font.PLAIN);
            case "ok":
                return new TextAttributes(null, OK_BACKGROUND, null, null, Font.PLAIN);
            case "muted":
                return new TextAttributes(MUTED_FOREGROUND, null, null, null, Font.PLAIN);
            case "strong":
                return new TextAttributes(null, null, null, null, Font.BOLD);
            default:
                if (style.startsWith("accent")) {
                    try {
                        int n = Integer.parseInt(style.substring("accent".length()));
                        return new TextAttributes(ACCENTS[Math.floorMod(n - 1, ACCENTS.length)], null, null, null, Font.BOLD);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
                return null;
        }
    }

    /** Errors are drawn above everything else, so a red mark is never hidden by a bracket color. */
    static int layer(@Nullable String style) {
        return "error".equals(style) ? HighlighterLayer.ERROR : HighlighterLayer.ADDITIONAL_SYNTAX;
    }

    static @Nullable Color rowBackground(@Nullable String style) {
        if (style == null) {
            return null;
        }
        switch (style) {
            case "error":
                return ERROR_BACKGROUND;
            case "ok":
                return OK_BACKGROUND;
            case "warning":
                return WARNING_BACKGROUND;
            default:
                return null;
        }
    }

    static @Nullable Color rowForeground(@Nullable String style) {
        if (style == null) {
            return null;
        }
        switch (style) {
            case "error":
                return ERROR_FOREGROUND;
            case "ok":
                return OK_FOREGROUND;
            case "muted":
                return MUTED_FOREGROUND;
            default:
                return null;
        }
    }
}
