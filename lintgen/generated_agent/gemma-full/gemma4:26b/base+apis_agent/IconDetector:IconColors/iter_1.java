package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import java.util.Arrays;
import java.util.Collection;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ICON_COLOR_ISSUE = new Issue(
            "IconColorIssue",
            "Icon colors do not follow the recommended visual style",
            "Notification icons (ic_stat_) and Action Bar icons (ic_menu_) should only use white " +
                    "and shades of gray to ensure they are visible across different system themes.",
            LintCategory.CORRECTION,
            8,
            true,
            Implementation.create(IconDetector.class, null)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("android:fillColor", "android:strokeColor", "android:tint");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        // The resource name of the vector drawable is the filename itself.
        String resourceName = context.getResourceName(attribute.getOwnerElement());
        if (resourceName != null && (resourceName.startsWith("ic_menu_") || resourceName.startsWith("ic_stat_"))) {
            String value = attribute.getValue();
            if (value != null && value.startsWith("#")) {
                checkGrayscale(context, attribute, value);
            }
        }
    }

    private void checkGrayscale(XmlContext context, Attr attribute, String value) {
        try {
            String hex = value.substring(1);
            int r, g, b;

            if (hex.length() == 6) {
                // Format: RRGGBB
                r = Integer.parseInt(hex.substring(0, 2), 16);
                g = Integer.parseInt(hex.substring(2, 4), 16);
                b = Integer.parseInt(hex.substring(4, 6), 16);
            } else if (hex.length() == 8) {
                // Format: AARRGGBB
                r = Integer.parseInt(hex.substring(2, 4), 16);
                g = Integer.parseInt(hex.substring(4, 6), 16);
                b = Integer.parseInt(hex.substring(6, 8), 16);
            } else {
                return;
            }

            // A color is grayscale if Red == Green == Blue
            if (r != g || g != b) {
                context.report(
                        ICON_COLOR_ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Icon color should be white or shades of gray for icons starting with ic_menu_ or ic_stat_"
                );
            }
        } catch (Exception e) {
            // If parsing fails, it's likely not a hex color string we can analyze.
        }
    }
}