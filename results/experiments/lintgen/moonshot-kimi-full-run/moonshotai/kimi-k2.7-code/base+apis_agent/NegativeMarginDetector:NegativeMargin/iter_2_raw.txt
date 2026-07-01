package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_HORIZONTAL;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_VERTICAL;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ITEM;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class NegativeMarginDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "NegativeMargin",
            "Negative Margins",
            "Margin values should be positive. Negative values are generally a sign that "
                    + "you are making assumptions about views surrounding the current one, or may be "
                    + "tempted to turn off child clipping to allow a view to escape its parent. "
                    + "Turning off child clipping to do this not only leads to poor graphical "
                    + "performance, it also results in wrong touch event handling since touch events "
                    + "are based strictly on a chain of parent-rect hit tests. Finally, making "
                    + "assumptions about the size of strings can lead to localization problems.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(NegativeMarginDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Set<String> MARGIN_ATTRIBUTES = new HashSet<>(Arrays.asList(
            ATTR_LAYOUT_MARGIN,
            ATTR_LAYOUT_MARGIN_LEFT,
            ATTR_LAYOUT_MARGIN_TOP,
            ATTR_LAYOUT_MARGIN_RIGHT,
            ATTR_LAYOUT_MARGIN_BOTTOM,
            ATTR_LAYOUT_MARGIN_START,
            ATTR_LAYOUT_MARGIN_END,
            ATTR_LAYOUT_MARGIN_HORIZONTAL,
            ATTR_LAYOUT_MARGIN_VERTICAL));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return MARGIN_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (isNegative(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Margin values should not be negative");
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();
        if (name == null || !isMarginAttribute(name)) {
            return;
        }

        String value = element.getTextContent();
        if (isNegative(value)) {
            context.report(
                    ISSUE,
                    nameAttr,
                    context.getValueLocation(nameAttr),
                    "Margin values should not be negative");
        }
    }

    private static boolean isMarginAttribute(@NonNull String name) {
        int separator = name.indexOf(':');
        if (separator != -1) {
            name = name.substring(separator + 1);
        }
        return MARGIN_ATTRIBUTES.contains(name);
    }

    private static boolean isNegative(@Nullable String value) {
        if (value == null) {
            return false;
        }
        value = value.trim();
        if (value.length() < 2 || value.charAt(0) != '-') {
            return false;
        }
        char c = value.charAt(1);
        return Character.isDigit(c) || c == '.';
    }
}