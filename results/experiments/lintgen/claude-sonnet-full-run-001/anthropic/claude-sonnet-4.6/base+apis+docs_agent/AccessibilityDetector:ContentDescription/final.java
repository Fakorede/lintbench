package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_HINT;
import static com.android.SdkConstants.IMAGE_BUTTON;
import static com.android.SdkConstants.IMAGE_VIEW;

public class AccessibilityDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without `contentDescription`",
            "Non-textual widgets like ImageViews and ImageButtons should use the " +
            "`contentDescription` attribute to specify a textual description of " +
            "the widget such that screen readers and other accessibility tools " +
            "can adequately describe the user interface.\n" +
            "\n" +
            "Note that elements in application screens that are purely decorative " +
            "and do not provide any content or enable a user action should not " +
            "have accessibility content descriptions. In this case, set their " +
            "descriptions to `@null`. If your app's minSdkVersion is 16 or higher, " +
            "you can instead set these graphical elements' " +
            "`android:importantForAccessibility` attributes to `no`.\n" +
            "\n" +
            "Note that for text fields, you should not set both the `hint` and the " +
            "`contentDescription` attributes since the hint will never be shown. Just " +
            "set the `hint`.",
            Category.A11Y,
            3,
            Severity.WARNING,
            new Implementation(
                    AccessibilityDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/guide/topics/ui/accessibility/apps#special-cases");

    public AccessibilityDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(IMAGE_VIEW, IMAGE_BUTTON);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
            // Check if it has a hint attribute (for text fields) - not applicable here,
            // but we check for contentDescription absence
            Attr hintAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_HINT);
            if (hintAttr != null) {
                // Has hint but also should check contentDescription
                // For ImageView/ImageButton, hint is not relevant, just report missing contentDescription
            }

            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "[" + element.getTagName() + "] Missing `contentDescription` attribute on image"
            );
        } else {
            // Has contentDescription - check if it's a text field that also has hint
            Attr hintAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_HINT);
            if (hintAttr != null) {
                Attr contentDescAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);
                if (contentDescAttr != null) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(contentDescAttr),
                            "Do not set both `hint` and `contentDescription`: the hint will never be shown " +
                            "when an `EditText` has a `contentDescription`. Just set the `hint`."
                    );
                }
            }
        }
    }
}