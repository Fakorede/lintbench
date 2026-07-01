package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AccessibilityDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String CONTENT_DESCRIPTION = "contentDescription";
    private static final String HINT = "hint";
    private static final String IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility";
    private static final String NO = "no";

    private static final String[] IMAGE_WIDGETS = {
        "ImageView",
        "ImageButton"
    };

    private static final String[] TEXT_FIELDS = {
        "EditText",
        "AutoCompleteTextView",
        "MultiAutoCompleteTextView",
        "ExtractEditText"
    };

    public static final Issue ISSUE =
            Issue.create(
                    "ContentDescription",
                    "Image without `contentDescription`",
                    "Non-textual widgets like ImageViews and ImageButtons should use the "
                            + "`contentDescription` attribute to specify a textual description "
                            + "of the widget such that screen readers and other accessibility "
                            + "tools can adequately describe the user interface.\n\n"
                            + "Note that elements in application screens that are purely "
                            + "decorative and do not provide any content or enable a user action "
                            + "should not have accessibility content descriptions. In this case, "
                            + "set their descriptions to `@null`. If your app's minSdkVersion "
                            + "is 16 or higher, you can instead set these graphical elements' "
                            + "`android:importantForAccessibility` attributes to `no`.\n\n"
                            + "Note that for text fields, you should not set both the `hint` and "
                            + "the `contentDescription` attributes since the hint will never be "
                            + "shown. Just set the `hint`.",
                    Category.A11Y,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(IMAGE_WIDGETS);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(CONTENT_DESCRIPTION);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        if (isTextField(element) && hasAndroidAttribute(element, HINT)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Do not set both `contentDescription` and `hint` on a text field; "
                            + "set `hint` only");
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (hasAndroidAttribute(element, CONTENT_DESCRIPTION)) {
            return;
        }

        String important = getAndroidAttribute(element, IMPORTANT_FOR_ACCESSIBILITY);
        if (NO.equals(important)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `contentDescription` attribute on image");
    }

    private static boolean isTextField(Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            return false;
        }
        for (String textField : TEXT_FIELDS) {
            if (textField.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAndroidAttribute(Element element, String name) {
        return element.hasAttributeNS(ANDROID_URI, name);
    }

    private static String getAndroidAttribute(Element element, String name) {
        return element.getAttributeNS(ANDROID_URI, name);
    }
}