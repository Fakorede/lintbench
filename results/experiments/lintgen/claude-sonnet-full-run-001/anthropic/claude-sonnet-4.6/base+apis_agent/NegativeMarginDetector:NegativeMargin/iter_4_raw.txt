package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

/**
 * Checks for negative margin values in XML layout files and dimension resource files.
 */
public class NegativeMarginDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "NegativeMargin",
            "Negative Margins",
            "Margin values should be positive. Negative values are generally a sign that " +
            "you are making assumptions about views surrounding the current one, or may be " +
            "tempted to turn off child clipping to allow a view to escape its parent. " +
            "Turning off child clipping to do this not only leads to poor graphical " +
            "performance, it also results in wrong touch event handling since touch events " +
            "are based strictly on a chain of parent-rect hit tests. Finally, making " +
            "assumptions about the size of strings can lead to localization problems.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(
                    NegativeMarginDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_LAYOUT_MARGIN = "layout_margin";
    private static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String ATTR_LAYOUT_MARGIN_TOP = "layout_marginTop";
    private static final String ATTR_LAYOUT_MARGIN_BOTTOM = "layout_marginBottom";
    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";
    private static final String ATTR_LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal";
    private static final String ATTR_LAYOUT_MARGIN_VERTICAL = "layout_marginVertical";

    private static final String TAG_DIMEN = "dimen";
    private static final String TAG_ITEM = "item";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";

    /** Constructs a new {@link NegativeMarginDetector} */
    public NegativeMarginDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_DIMEN, TAG_ITEM);
    }

    @Override
    @Nullable
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
                ATTR_LAYOUT_MARGIN_VERTICAL
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only process in values folder
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        String tagName = element.getTagName();
        String name = null;
        boolean isDimen = false;

        // Check <dimen> elements
        if (TAG_DIMEN.equals(tagName)) {
            name = element.getAttribute(ATTR_NAME);
            isDimen = true;
        }
        // Check <item type="dimen"> elements
        else if (TAG_ITEM.equals(tagName)) {
            String type = element.getAttribute(ATTR_TYPE);
            if ("dimen".equals(type)) {
                name = element.getAttribute(ATTR_NAME);
                isDimen = true;
            }
        }

        if (isDimen && name != null && containsMargin(name)) {
            String value = getTextContent(element);
            if (value != null && isNegativeDimension(value.trim())) {
                String message = String.format(
                        "Negative margins are not supported in `%1$s`", name);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        // Handle resource references like @dimen/negative_margin
        if (value.startsWith("@") || value.startsWith("?")) {
            return;
        }

        // Check if the value is a negative dimension
        if (isNegativeDimension(value.trim())) {
            String message = String.format(
                    "Negative margins are not supported in `%1$s`",
                    attribute.getName());
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    /**
     * Returns true if the given name contains "margin" (case-insensitive).
     */
    private static boolean containsMargin(@NonNull String name) {
        return name.toLowerCase().contains("margin");
    }

    @Nullable
    private static String getTextContent(@NonNull Element element) {
        StringBuilder sb = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                sb.append(child.getNodeValue());
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Checks if a dimension value string represents a negative value.
     *
     * @param value the dimension string (e.g., "-8dp", "-2px")
     * @return true if the value is negative
     */
    private static boolean isNegativeDimension(@NonNull String value) {
        if (!value.startsWith("-")) {
            return false;
        }
        // Strip the leading minus sign
        String remainder = value.substring(1).trim();
        if (remainder.isEmpty()) {
            return false;
        }
        // Strip unit suffix
        String numericPart = stripUnit(remainder);
        if (numericPart == null || numericPart.isEmpty()) {
            return false;
        }
        try {
            float numValue = Float.parseFloat(numericPart);
            return numValue > 0;
        } catch (NumberFormatException e) {
            // Not a valid number, ignore
            return false;
        }
    }

    /**
     * Strips the unit suffix from a dimension string and returns the numeric part.
     *
     * @param value the dimension string without leading minus sign (e.g., "8dp", "2px")
     * @return the numeric part as a string, or the original value if no unit found
     */
    @Nullable
    private static String stripUnit(@NonNull String value) {
        String[] units = {"dip", "dp", "sp", "px", "pt", "mm", "in"};
        for (String unit : units) {
            if (value.endsWith(unit)) {
                String numericPart = value.substring(0, value.length() - unit.length());
                if (!numericPart.isEmpty()) {
                    return numericPart;
                }
                return null;
            }
        }
        // No unit found - return as-is (might be a plain number)
        return value;
    }
}