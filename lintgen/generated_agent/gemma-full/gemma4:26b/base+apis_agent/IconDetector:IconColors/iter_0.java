package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Attributes;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintCategory;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;

import java.util.Arrays;
import java.util.Collection;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ICON_COLOR_ISSUE = new Issue(
            "IconColorIssue",
            "Icon colors do not follow the recommended visual style",
            "Notification icons (ic_stat_) and Action Bar icons (ic_menu_) should only use white " +
                    "and shades of gray to ensure they are visible across different system themes.",
            LintCategory.CORRECTIONS,
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
        if (context.getXmlFile() == null) {
            return;
        }

        String fileName = context.getXmlFile().getName();
        // Check if the resource name follows the convention for notification or action bar icons
        if (fileName.startsWith("ic_menu_") || fileName.startsWith("ic_stat_")) {
            String value = attribute.getValue();
            if (value != null && value.startsWith("#")) {
                checkGrayscale(context, attribute, value);
            }
        }
    }

    /**
     * Parses the hex color string and reports an issue if the color is not grayscale.
     */
    private void checkGrayscale(XmlContext context, Attr attribute, String value) {
        try {
            // Remove '#' prefix
            String hex = value.substring(1);
            long colorLong;

            if (hex.length() == 6) {
                // Format: RRGGBB -> Convert to AARRGGBB with full opacity
                colorLong = Long.parseLong("FF" + hex, 16);
            } else if (hex.length() == 8) {
                // Format: AARRGGBB
                colorLong = Long.parseLong(hex, 16);
            } else {
                // Unsupported format for this specific check
                return;
            }

            int r = (int) ((colorLong >> 16) & 0xFF);
            int g = (int) ((colorLong >> 8) & 0xFF);
            int b = (int) (colorLong & 0xFF);

            // A color is grayscale if Red == Green == Blue
            if (r != g || g != b) {
                context.report(
                        ICON_COLOR_ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Icon color should be white or shades of gray for icons starting with ic_menu_ or ic_stat_"
                );
            }
        } catch (NumberFormatException e) {
            // If the string is not a valid hex number, we skip it as it might be a resource reference (@color/...)
            // which requires complex resolution logic.
        }
    }
}