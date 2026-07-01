package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class NegativeMarginDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "NegativeMargin",
                    "Negative Margins",
                    "Margin values should be positive. Negative values are generally a sign that "
                            + "you are making assumptions about views surrounding the current one, "
                            + "or may be tempted to turn off child clipping to allow a view to "
                            + "escape its parent. Turning off child clipping to do this not only "
                            + "leads to poor graphical performance, it also results in wrong touch "
                            + "event handling since touch events are based strictly on a chain of "
                            + "parent-rect hit tests. Finally, making assumptions about the size "
                            + "of strings can lead to localization problems.",
                    Category.USABILITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ATTR_LAYOUT_MARGIN = "layout_margin";
    private static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String ATTR_LAYOUT_MARGIN_TOP = "layout_marginTop";
    private static final String ATTR_LAYOUT_MARGIN_BOTTOM = "layout_marginBottom";
    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";
    private static final String ATTR_LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal";
    private static final String ATTR_LAYOUT_MARGIN_VERTICAL = "layout_marginVertical";

    private static final String TAG_STYLE = "style";
    private static final String TAG_ITEM = "item";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_LAYOUT_MARGIN,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_RIGHT,
                ATTR_LAYOUT_MARGIN_TOP,
                ATTR_LAYOUT_MARGIN_BOTTOM,
                ATTR_LAYOUT_MARGIN_START,
                ATTR_LAYOUT_MARGIN_END,
                ATTR_LAYOUT_MARGIN_HORIZONTAL,
                ATTR_LAYOUT_MARGIN_VERTICAL);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ITEM);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (isNegativeDimension(value)) {
            String message =
                    String.format(
                            "Negative margins are not supported in `%1$s`",
                            attribute.getLocalName());
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <item name="android:layout_margin..."> in style resources
        String name = element.getAttribute("name");
        if (name != null && isMarginAttribute(name)) {
            String value = element.getTextContent();
            if (value != null) {
                value = value.trim();
                if (isNegativeDimension(value)) {
                    String message =
                            String.format("Negative margins are not supported in `%1$s`", name);
                    context.report(ISSUE, element, context.getLocation(element), message);
                }
            }
        }
    }

    private static boolean isMarginAttribute(@NonNull String name) {
        // Strip namespace prefix if present (e.g. "android:layout_marginLeft")
        int colonIndex = name.indexOf(':');
        String localName = colonIndex >= 0 ? name.substring(colonIndex + 1) : name;
        return localName.equals(ATTR_LAYOUT_MARGIN)
                || localName.equals(ATTR_LAYOUT_MARGIN_LEFT)
                || localName.equals(ATTR_LAYOUT_MARGIN_RIGHT)
                || localName.equals(ATTR_LAYOUT_MARGIN_TOP)
                || localName.equals(ATTR_LAYOUT_MARGIN_BOTTOM)
                || localName.equals(ATTR_LAYOUT_MARGIN_START)
                || localName.equals(ATTR_LAYOUT_MARGIN_END)
                || localName.equals(ATTR_LAYOUT_MARGIN_HORIZONTAL)
                || localName.equals(ATTR_LAYOUT_MARGIN_VERTICAL);
    }

    private static boolean isNegativeDimension(@NonNull String value) {
        // Check for literal negative dimension values like "-8dp", "-4px", etc.
        if (value.startsWith("-")) {
            // Make sure it's a dimension value (not a reference like @dimen/...)
            // A negative literal dimension starts with '-' followed by digits
            if (value.length() > 1 && Character.isDigit(value.charAt(1))) {
                return true;
            }
        }
        return false;
    }
}