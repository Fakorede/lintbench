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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AccessibilityDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ContentDescription",
                    "Image without contentDescription",
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
                            + "`contentDescription` attributes since the hint will never be shown. "
                            + "Just set the `hint`.",
                    Category.A11Y,
                    3,
                    Severity.WARNING,
                    new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_CONTENT_DESCRIPTION = "contentDescription";
    private static final String ATTR_HINT = "hint";
    private static final String ATTR_IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility";

    private static final String IMAGE_VIEW = "ImageView";
    private static final String IMAGE_BUTTON = "ImageButton";
    private static final String EDIT_TEXT = "EditText";
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                IMAGE_VIEW, IMAGE_BUTTON, EDIT_TEXT, AUTO_COMPLETE_TEXT_VIEW, MULTI_AUTO_COMPLETE_TEXT_VIEW);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_CONTENT_DESCRIPTION, ATTR_HINT, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if (IMAGE_VIEW.equals(tag) || IMAGE_BUTTON.equals(tag)) {
            if (!hasAndroidAttr(element, ATTR_CONTENT_DESCRIPTION)) {
                boolean importantForAccessibilityNo =
                        "no".equals(getAndroidAttrValue(element, ATTR_IMPORTANT_FOR_ACCESSIBILITY));
                if (importantForAccessibilityNo && context.getMainProject().getMinSdk() >= 16) {
                    return;
                }
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Missing `contentDescription` attribute on image");
            }
        } else if (EDIT_TEXT.equals(tag)
                || AUTO_COMPLETE_TEXT_VIEW.equals(tag)
                || MULTI_AUTO_COMPLETE_TEXT_VIEW.equals(tag)) {
            // This case is handled in visitAttribute so that the conflicting
            // contentDescription attribute itself is flagged.
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (!ATTR_CONTENT_DESCRIPTION.equals(name)) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        String tag = owner.getTagName();
        if (EDIT_TEXT.equals(tag)
                || AUTO_COMPLETE_TEXT_VIEW.equals(tag)
                || MULTI_AUTO_COMPLETE_TEXT_VIEW.equals(tag)) {
            if (hasAndroidAttr(owner, ATTR_HINT)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Do not set both `contentDescription` and `hint`; the `hint` will not "
                                + "be shown. Just set the `hint`.");
            }
        }
    }

    private static boolean hasAndroidAttr(Element element, String localName) {
        return element.hasAttributeNS(ANDROID_URI, localName)
                || element.hasAttribute(localName);
    }

    private static String getAndroidAttrValue(Element element, String localName) {
        if (element.hasAttributeNS(ANDROID_URI, localName)) {
            return element.getAttributeNS(ANDROID_URI, localName);
        }
        if (element.hasAttribute(localName)) {
            return element.getAttribute(localName);
        }
        return null;
    }
}