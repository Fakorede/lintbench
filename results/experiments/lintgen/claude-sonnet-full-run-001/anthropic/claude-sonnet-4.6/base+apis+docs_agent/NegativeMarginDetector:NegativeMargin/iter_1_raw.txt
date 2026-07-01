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

import java.util.Arrays;
import java.util.Collection;

/**
 * Checks for negative margin values in XML layout files and value resource files.
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

    private static final String[] MARGIN_ATTRS = {
            "layout_margin",
            "layout_marginLeft",
            "layout_marginRight",
            "layout_marginTop",
            "layout_marginBottom",
            "layout_marginStart",
            "layout_marginEnd",
            "layout_marginHorizontal",
            "layout_marginVertical",
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
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(MARGIN_ATTRS);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null && isNegativeDimension(value)) {
            String message = String.format(
                    "Negative margins are not supported in `%1$s`",
                    attribute.getLocalName());
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    /**
     * Returns true if the given dimension value string represents a negative dimension.
     */
    private static boolean isNegativeDimension(@NonNull String value) {
        // Skip resource references and theme attributes
        if (value.startsWith("@") || value.startsWith("?")) {
            return false;
        }

        // Try to parse the numeric portion
        // Dimension values look like "-8dp", "-4px", etc.
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) != '-') {
            return false;
        }

        // Make sure it's actually a number followed by a unit (not just a minus sign)
        // Strip the leading '-' and check if the rest starts with a digit
        String rest = trimmed.substring(1);
        if (rest.isEmpty()) {
            return false;
        }

        // Check that the character after '-' is a digit or '.'
        char first = rest.charAt(0);
        if (!Character.isDigit(first) && first != '.') {
            return false;
        }

        // Try to extract the numeric value to confirm it's actually negative
        try {
            // Find where the unit starts (first non-digit, non-dot character)
            int unitStart = 0;
            boolean hasDot = false;
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (Character.isDigit(c)) {
                    unitStart = i + 1;
                } else if (c == '.' && !hasDot) {
                    hasDot = true;
                    unitStart = i + 1;
                } else {
                    break;
                }
            }

            if (unitStart == 0) {
                return false;
            }

            String numericPart = rest.substring(0, unitStart);
            double numericValue = Double.parseDouble(numericPart);

            // Only report if the value is actually negative (not -0)
            return numericValue > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}