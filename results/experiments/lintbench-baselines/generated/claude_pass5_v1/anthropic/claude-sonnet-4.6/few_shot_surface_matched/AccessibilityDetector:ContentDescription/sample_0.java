package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_HINT;
import static com.android.SdkConstants.IMAGE_BUTTON;
import static com.android.SdkConstants.IMAGE_VIEW;

public class AccessibilityDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ContentDescription",
                    "Image without `contentDescription`",
                    "Non-textual widgets like `ImageView`s and `ImageButton`s should use the "
                            + "`contentDescription` attribute to specify a textual description of "
                            + "the widget such that screen readers and other accessibility tools "
                            + "can adequately describe the user interface.\n"
                            + "\n"
                            + "Note that elements in application screens that are purely decorative "
                            + "and do not provide any content or enable a user action should not "
                            + "have accessibility content descriptions. In this case, set their "
                            + "descriptions to `@null`. If your app's `minSdkVersion` is 16 or "
                            + "higher, you can instead set these graphical elements' "
                            + "`android:importantForAccessibility` attributes to `no`.\n"
                            + "\n"
                            + "Note that for text fields, you should not set both the `hint` and "
                            + "the `contentDescription` attributes since the hint will never be "
                            + "shown. Just set the `hint`.",
                    Category.A11Y,
                    3,
                    Severity.WARNING,
                    new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE));

    /** Attribute name for importantForAccessibility */
    private static final String ATTR_IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility";

    public AccessibilityDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(IMAGE_VIEW, IMAGE_BUTTON);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_CONTENT_DESCRIPTION, ATTR_HINT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String attributeName = attribute.getLocalName();
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        if (ATTR_CONTENT_DESCRIPTION.equals(attributeName)) {
            // If contentDescription is set on an EditText or similar text field that also
            // has a hint, warn that both should not be set.
            if (element.hasAttributeNS(ANDROID_URI, ATTR_HINT)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(attribute),
                        "Do not set both `contentDescription` and `hint`: the `hint` will "
                                + "never be shown when a `contentDescription` is also set");
            }
        } else if (ATTR_HINT.equals(attributeName)) {
            // If hint is set and contentDescription is also set, warn.
            if (element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(attribute),
                        "Do not set both `contentDescription` and `hint`: the `hint` will "
                                + "never be shown when a `contentDescription` is also set");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
            // Also check importantForAccessibility="no" as an acceptable alternative
            String importantForAccessibility =
                    element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
            if ("no".equals(importantForAccessibility)
                    || "noHideDescendants".equals(importantForAccessibility)) {
                return;
            }

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `contentDescription` attribute on image");
        }
    }
}