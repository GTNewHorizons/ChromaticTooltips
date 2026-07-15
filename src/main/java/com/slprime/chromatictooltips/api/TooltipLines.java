package com.slprime.chromatictooltips.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;

import net.minecraft.util.EnumChatFormatting;

import com.slprime.chromatictooltips.TooltipHandler;
import com.slprime.chromatictooltips.TooltipRegistry;
import com.slprime.chromatictooltips.component.InlineComponent;
import com.slprime.chromatictooltips.component.ParagraphComponent;
import com.slprime.chromatictooltips.component.SpaceComponent;
import com.slprime.chromatictooltips.component.TextComponent;
import com.slprime.chromatictooltips.event.TextLinesConverterEvent;
import com.slprime.chromatictooltips.util.TooltipFontContext;
import com.slprime.chromatictooltips.util.TooltipUtils;

public class TooltipLines {

    public static final String ALT_MODIFIER = "§!alt";
    public static final String CTRL_MODIFIER = "§!ctrl";
    public static final String SHIFT_MODIFIER = "§!shift";

    public static final EnumChatFormatting BASE_COLOR = EnumChatFormatting.GRAY;
    protected static final String HEADER_SUFFIX = "§h";
    protected static final int HEADER_SPACING = 4;

    protected final List<Object> textLines = new ArrayList<>();
    protected EnumSet<TooltipModifier> supportedModifiers = EnumSet.noneOf(TooltipModifier.class);

    public TooltipLines() {}

    public TooltipLines(Object... components) {
        lines(Arrays.asList(components));
    }

    public TooltipLines(List<?> lines) {
        lines(lines);
    }

    public TooltipLines header(String line) {
        this.textLines.add(line + HEADER_SUFFIX);
        return this;
    }

    public TooltipLines line(ITooltipComponent line) {
        this.textLines.add(line);
        return this;
    }

    public TooltipLines line(String line) {
        this.textLines.add(line);
        return this;
    }

    public TooltipLines lines(String... lines) {
        return lines(Arrays.asList(lines));
    }

    public TooltipLines lines(List<?> lines) {
        if (lines == null) return this;

        for (Object line : lines) {
            if (line instanceof TooltipLines tl) {
                this.textLines.addAll(tl.textLines);
                this.supportedModifiers.addAll(tl.supportedModifiers);
            } else if (line instanceof String str) {
                this.textLines.addAll(Arrays.asList(str.split("\n")));
            } else if (line != null) {
                this.textLines.add(line);
            }
        }

        return this;
    }

    public TooltipLines supports(TooltipModifier... modifiers) {
        this.supportedModifiers.addAll(Arrays.asList(modifiers));
        return this;
    }

    EnumSet<TooltipModifier> getSupportedModifiers() {
        return supportedModifiers;
    }

    public TooltipLines paragraph() {
        this.textLines.add("");
        return this;
    }

    public TooltipLines divider() {
        return divider(EnumChatFormatting.GRAY);
    }

    public TooltipLines divider(EnumChatFormatting color) {
        this.textLines.add(color + "---" + EnumChatFormatting.RESET);
        return this;
    }

    public List<ITooltipComponent> build(TooltipContext context) {
        final List<ITooltipComponent> results = new ArrayList<>();
        final TextLinesConverterEvent event = new TextLinesConverterEvent(context.getTarget(), this.textLines);
        TooltipUtils.postEvent(event);

        for (Object line : event.list) {

            if (line instanceof ITooltipComponent component) {
                results.add(component);
            } else if ("".equals(line)) {
                results.add(new ParagraphComponent());
            } else if (line instanceof String str && !TooltipUtils.isBlacklistedLine(str)) {
                final Map.Entry<Matcher, ITooltipLineConverter> lineConverterEntry = TooltipRegistry
                    .getLineConverter(str);

                if (lineConverterEntry != null) {
                    final ITooltipLineConverter converter = lineConverterEntry.getValue();
                    final Matcher matcher = lineConverterEntry.getKey();
                    final ITooltipComponent convertedComponent = converter.convert(matcher, context);

                    if (convertedComponent != null) {
                        results.add(convertedComponent);
                    }

                } else if (str.endsWith(HEADER_SUFFIX)) {
                    results.add(buildTextLine(str.substring(0, str.length() - HEADER_SUFFIX.length()), HEADER_SPACING));
                } else {
                    results.add(buildTextLine(str, TooltipFontContext.DEFAULT_SPACING));
                }

            }

        }

        while (!results.isEmpty() && results.get(0) instanceof SpaceComponent) {
            results.remove(0);
        }

        while (!results.isEmpty() && results.get(results.size() - 1) instanceof SpaceComponent) {
            results.remove(results.size() - 1);
        }

        for (TooltipModifier modifier : this.supportedModifiers) {
            context.supportModifiers(modifier);
        }

        return results;
    }

    protected static ITooltipComponent buildTextLine(String str, int spacing) {
        final String colored = TooltipUtils.applyBaseColorIfAbsent(str, BASE_COLOR);
        final List<ITooltipComponent> inlineRow = new ArrayList<>();
        final StringBuilder buffer = new StringBuilder();
        int i = 0;

        while (i < colored.length()) {
            final char c = colored.charAt(i);

            if (c == '§' && i + 1 < colored.length()
                && (colored.charAt(i + 1) == 'z' || colored.charAt(i + 1) == 'Z')) {
                final char openMark = colored.charAt(i + 1);
                final char closeMark = openMark == 'z' ? 'Z' : 'z';
                final int close = colored.indexOf("§" + closeMark, i + 2);

                if (close > i + 2 && isDigits(colored, i + 2, close)) {
                    final String token = colored.substring(i, close + 2);
                    final ITooltipComponent tokenComponent = TooltipHandler.getTooltipComponent(token);

                    if (tokenComponent != null) {
                        if (buffer.length() > 0) {
                            inlineRow.add(new TextComponent(buffer.toString(), spacing));
                            buffer.setLength(0);
                        }

                        inlineRow.add(tokenComponent);
                        i = close + 2;
                        continue;
                    }
                }
            }

            buffer.append(c);
            i++;
        }

        if (inlineRow.isEmpty()) {
            return new TextComponent(colored, spacing);
        }

        if (buffer.length() > 0) {
            inlineRow.add(new TextComponent(buffer.toString(), spacing));
        }

        return inlineRow.size() == 1 ? inlineRow.get(0)
            : new InlineComponent(Collections.singletonList(inlineRow), 0, 0);
    }

    private static boolean isDigits(String str, int from, int to) {

        for (int i = from; i < to; i++) {
            if (!Character.isDigit(str.charAt(i))) return false;
        }

        return true;
    }

    public boolean isEmpty() {
        return this.textLines.isEmpty();
    }

    public void clear() {
        this.textLines.clear();
    }

    public int size() {
        return this.textLines.size();
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (obj instanceof TooltipLines other) {
            return equalsLines(this.textLines, other.textLines);
        }

        return false;
    }

    protected static boolean equalsLines(List<?> a, List<?> b) {
        if (a.size() != b.size()) return false;

        for (int i = 0; i < a.size(); i++) {
            final Object aObject = a.get(i);
            final Object bObject = b.get(i);

            if (!Objects.equals(aObject, bObject) && !String.valueOf(aObject)
                .equals(String.valueOf(bObject))) {
                return false;
            }
        }

        return true;
    }

}
