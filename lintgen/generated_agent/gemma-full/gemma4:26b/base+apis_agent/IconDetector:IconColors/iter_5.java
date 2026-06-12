package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import java.util.Arrays;
import java.util.Collection;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements XmlScanner {

    public static final Issue ICON_COLOR_ISSUE = Issue.create(
            "IconColorIssue",
            "Icon colors do not follow the recommended visual style",
            "Notification icons (ic_stat_) and Action 'ic_menu_' icons should only use white " +
                    "and shades of gray to ensure they are visible across different system themes.",
            Implementation.create(IconDetector.class, null)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("fillColor", "strokeColor", "tint");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String uri = attribute.getOwnerElement().getOwnerDocument().getBaseURI();
        if (uri == null) {
            return;
        }

        // Extract filename from URI (e.g., .../res/drawable/ic_stat_notification.xml -> ic_stat_notification)
        String fileName = uri.substring(uri.lastIndexOf('/') + 1);
        if (fileName.endsWith(".xml")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        // Check if the icon follows the naming convention for system icons
        if (fileName.startsWith("ic_menu_") || fileName.startsWith("ic_stat_")) {
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
                // Not a standard hex color format we can easily validate for grayscale
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
            // If parsing fails, we skip the check for this specific attribute value
        }
    }
}