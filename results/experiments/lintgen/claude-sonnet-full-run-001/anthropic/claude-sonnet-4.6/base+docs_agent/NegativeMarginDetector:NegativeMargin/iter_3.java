package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import static com.android.SdkConstants.ANDROID_URI;

/**
 * Checks for negative margin values in XML layout files and values files.
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
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.ALL_RESOURCE_FILES)));

    private static final String[] MARGIN_ATTRS = {
            "layout_margin",
            "layout_marginLeft",
            "layout_marginRight",
            "layout_marginTop",
            "layout_marginBottom",
            "layout_marginStart",
            "layout_marginEnd",
            "layout_marginHorizontal",
            "layout_marginVertical"
    };

    /** Constructs a new {@link NegativeMarginDetector} */
    public NegativeMarginDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES
                || folderType == ResourceFolderType.MENU
                || folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("dimen");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(MARGIN_ATTRS);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle <dimen name="...">-8dp</dimen> in values files
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        // Check if the name contains "margin"
        String name = element.getAttribute("name");
        if (name == null || !name.toLowerCase().contains("margin")) {
            return;
        }

        // Get the text content
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String value = child.getNodeValue();
                if (value != null) {
                    value = value.trim();
                    if (isNegativeDimension(value)) {
                        String message = String.format(
                                "Negative margins are not supported in `%1$s`",
                                name);
                        context.report(ISSUE, element, context.getLocation(element), message);
                    }
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        // Check if the value is a literal dimension (not a reference like @dimen/...)
        if (value.startsWith("@") || value.startsWith("?")) {
            // It's a resource reference; we only flag literal negative values
            return;
        }

        if (isNegativeDimension(value)) {
            String attrName = attribute.getLocalName();
            if (attrName == null) {
                attrName = attribute.getName();
            }
            String message = String.format(
                    "Negative margins are not supported in `%1$s`",
                    attrName);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    /**
     * Returns true if the given dimension string represents a negative value.
     *
     * @param value the dimension string (e.g. "-8dp", "-2px")
     * @return true if the value is negative
     */
    private static boolean isNegativeDimension(@NonNull String value) {
        if (!value.startsWith("-")) {
            return false;
        }

        // Strip the leading '-' and check that the rest is a valid dimension
        String rest = value.substring(1);
        if (rest.isEmpty()) {
            return false;
        }

        // Find where the numeric part ends
        int i = 0;
        boolean hasDigit = false;
        boolean hasDot = false;
        while (i < rest.length()) {
            char c = rest.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
                i++;
            } else if (c == '.' && !hasDot) {
                hasDot = true;
                i++;
            } else {
                break;
            }
        }

        if (!hasDigit) {
            return false;
        }

        // The remainder should be a unit (dp, sp, px, dip, in, mm, pt) or empty
        String unit = rest.substring(i).trim();

        // A bare negative number with no unit is not a valid dimension in XML,
        // but we still flag it to be safe.
        if (unit.isEmpty()) {
            return true;
        }

        switch (unit.toLowerCase()) {
            case "dp":
            case "dip":
            case "sp":
            case "px":
            case "in":
            case "mm":
            case "pt":
                return true;
            default:
                return false;
        }
    }
}