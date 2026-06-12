package com.android.tools.lint.checks;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements XmlScanner {

    public static final Issue ICON_COLOR_SCHEME_ISSUE = Issue.create(
            "IconColorSchemeIssue",
            "Icon colors do not follow the recommended visual style",
            "Notification icons (ic_stat_) and Action Bar icons (ic_menu_) should only use white " +
                    "and shades of gray to ensure they are visible across different system themes.",
            Implementation.create(IconDetector.class)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("vector");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("fillColor", "strokeColor");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null) return;

        String uri = element.getOwnerDocument().getBaseURI();
        if (uri == null) return;
        
        String fileName = uri.substring(uri.lastIndexOf('/') + 1);

        if (fileName.startsWith("ic_stat_") || fileName.startsWith("ic_menu_")) {
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
            } else if (hex.length() == 3) {
                // Format: RGB
                r = Integer.parseInt(hex.substring(0, 1), 16);
                g = Integer.parseInt(hex.substring(1, 2), 16);
                b = Integer.parseInt(hex.substring(2, 3), 16);
                r = (r << 4) | r;
                g = (g << 4) | g;
                b = (b << 4) | b;
            } else if (hex.length() == 4) {
                // Format: ARGB
                r = Integer.parseInt(hex.substring(1, 2), 16);
                g = Integer.parseInt(hex.substring(2, 3), 16);
                b = Integer.parseInt(hex.substring(3, 4), 16);
                r = (r << 4) | r;
                g = (g << 4) | g;
                b = (b << 4) | b;
            } else {
                return;
            }

            if (r != g || g != b) {
                context.report(
                        ICON_COLOR_SCHEME_ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Icon color should be white or shades of gray for icons starting with ic_menu_ or ic_stat_"
                );
            }
        } catch (Exception e) {
            // Ignore parsing errors for malformed hex strings
        }
    }
}