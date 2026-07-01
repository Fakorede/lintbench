package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_HINT;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AccessibilityDetector extends LayoutDetector implements XmlScanner {

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
                    4,
                    Severity.WARNING,
                    new Implementation(
                            AccessibilityDetector.class, Scope.LAYOUT_RESOURCE_FILES));

    @Override
    public Collection<String> getApplicableElements() {
        return null; // Visit all elements to catch standard and custom image/text views
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_CONTENT_DESCRIPTION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (tagName.equals("ImageView")
                || tagName.equals("ImageButton")
                || tagName.equals("QuickContactBadge")
                || tagName.endsWith(".ImageView")
                || tagName.endsWith(".ImageButton")
                || tagName.endsWith(".QuickContactBadge")) {
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
                String important = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
                if ("no".equals(important) || "noHideDescendants".equals(important)) {
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

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();
        if (tagName.equals("EditText")
                || tagName.endsWith(".EditText")
                || tagName.equals("AutoCompleteTextView")
                || tagName.endsWith(".AutoCompleteTextView")
                || tagName.equals("MultiAutoCompleteTextView")
                || tagName.endsWith(".MultiAutoCompleteTextView")
                || tagName.equals("TextInputEditText")
                || tagName.endsWith(".TextInputEditText")) {
            if (element.hasAttributeNS(ANDROID_URI, ATTR_HINT)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Do not set both `contentDescription` and `hint` on text fields, "
                                + "as the `hint` will never be shown. Just set the `hint`.");
            }
        }
    }
}