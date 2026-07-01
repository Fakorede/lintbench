package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_HINT;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY;
import static com.android.SdkConstants.IMAGE_BUTTON;
import static com.android.SdkConstants.IMAGE_VIEW;

public class AccessibilityDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ContentDescription",
                    "Image without `contentDescription`",
                    "Non-textual widgets like ImageViews and ImageButtons should use the "
                            + "`contentDescription` attribute to specify a textual description of "
                            + "the widget such that screen readers and other accessibility tools "
                            + "can adequately describe the user interface.\n\n"
                            + "Note that elements in application screens that are purely decorative "
                            + "and do not provide any content or enable a user action should not "
                            + "have accessibility content descriptions. In this case, set their "
                            + "descriptions to `@null`. If your app's minSdkVersion is 16 or higher, "
                            + "you can instead set these graphical elements' "
                            + "`android:importantForAccessibility` attributes to `no`.\n\n"
                            + "Note that for text fields, you should not set both the `hint` and the "
                            + "`contentDescription` attributes since the hint will never be shown. Just "
                            + "set the `hint`.",
                    Category.A11Y,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(IMAGE_VIEW, IMAGE_BUTTON);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_CONTENT_DESCRIPTION);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        // Check if this is a text field that has both hint and contentDescription set
        if (isTextField(tagName)) {
            // If an edit text has both hint and contentDescription, warn that hint will never show
            if (element.hasAttributeNS(ANDROID_URI, ATTR_HINT)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Do not set both `contentDescription` and `hint`: the `hint` will never be "
                                + "shown when a `contentDescription` is set");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if the element is missing contentDescription
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
            // Also check for importantForAccessibility="no" as an acceptable alternative
            String importantForAccessibility =
                    element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
            if ("no".equals(importantForAccessibility)
                    || "noHideDescendants".equals(importantForAccessibility)) {
                // Element is marked as not important for accessibility, so no content description needed
                return;
            }

            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `contentDescription` attribute on image");
        }
    }

    /**
     * Returns true if the given tag name represents a text input field.
     */
    private static boolean isTextField(@NonNull String tagName) {
        return tagName.equals("EditText")
                || tagName.equals("AutoCompleteTextView")
                || tagName.equals("MultiAutoCompleteTextView")
                || tagName.endsWith("EditText");
    }
}