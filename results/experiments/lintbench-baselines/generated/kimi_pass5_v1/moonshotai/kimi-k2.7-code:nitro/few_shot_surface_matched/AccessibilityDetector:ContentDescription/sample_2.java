package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AccessibilityDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ContentDescription",
                    "Missing contentDescription attribute on image",
                    "Non-textual widgets like ImageViews and ImageButtons should use the "
                            + "`contentDescription` attribute to specify a textual description "
                            + "of the widget so that screen readers and other accessibility "
                            + "tools can adequately describe the user interface.",
                    Category.A11Y,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_CONTENT_DESCRIPTION = "contentDescription";
    private static final String ATTR_IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility";
    private static final String ATTR_HINT = "hint";
    private static final String VALUE_NO = "no";
    private static final String IMAGE_VIEW = "ImageView";
    private static final String IMAGE_BUTTON = "ImageButton";
    private static final String EDIT_TEXT = "EditText";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(IMAGE_VIEW, IMAGE_BUTTON, EDIT_TEXT);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_CONTENT_DESCRIPTION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (EDIT_TEXT.equals(tag)) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
            return;
        }

        if (VALUE_NO.equals(
                element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY))) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `contentDescription` attribute on image");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element element = attribute.getOwnerElement();
        String tag = element.getTagName();
        if (!EDIT_TEXT.equals(tag)) {
            return;
        }

        if (element.hasAttributeNS(ANDROID_URI, ATTR_HINT)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Do not set both `contentDescription` and `hint` on an EditText; "
                            + "use only `hint`");
        }
    }
}