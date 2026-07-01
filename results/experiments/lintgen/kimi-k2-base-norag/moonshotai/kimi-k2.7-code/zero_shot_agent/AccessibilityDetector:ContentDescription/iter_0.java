package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AccessibilityDetector extends Detector implements XmlScanner {

    private static final String[] IMAGE_WIDGETS = new String[] {
            SdkConstants.TAG_IMAGE_VIEW,
            SdkConstants.TAG_IMAGE_BUTTON,
            SdkConstants.TAG_QUICK_CONTACT_BADGE
    };

    private static final String[] TEXT_FIELDS = new String[] {
            SdkConstants.TAG_EDIT_TEXT,
            SdkConstants.TAG_AUTO_COMPLETE_TEXT_VIEW,
            SdkConstants.TAG_MULTI_AUTO_COMPLETE_TEXT_VIEW,
            SdkConstants.TAG_EXTRACT_EDIT_TEXT
    };

    private static final String MESSAGE_MISSING =
            "Non-textual widgets should provide a `contentDescription` for accessibility. "
                    + "If the widget is purely decorative, set it to `@null` or "
                    + "`android:importantForAccessibility=\"no\"`.";

    private static final String MESSAGE_TEXT_FIELD =
            "Do not set both `android:hint` and `android:contentDescription` on text fields; "
                    + "the hint will not be shown.";

    public static final Issue ISSUE = Issue.create(
            "ContentDescription",
            "Image without contentDescription",
            "Non-textual widgets like ImageViews and ImageButtons should use the "
                    + "`contentDescription` attribute to specify a textual description of the "
                    + "widget such that screen readers and other accessibility tools can "
                    + "adequately describe the user interface.",
            Category.ACCESSIBILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    AccessibilityDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE_SCOPE)));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(IMAGE_WIDGETS_AND_TEXT_FIELDS);
    }

    private static final String[] IMAGE_WIDGETS_AND_TEXT_FIELDS;
    static {
        String[] combined = new String[IMAGE_WIDGETS.length + TEXT_FIELDS.length];
        System.arraycopy(IMAGE_WIDGETS, 0, combined, 0, IMAGE_WIDGETS.length);
        System.arraycopy(TEXT_FIELDS, 0, combined, IMAGE_WIDGETS.length, TEXT_FIELDS.length);
        IMAGE_WIDGETS_AND_TEXT_FIELDS = combined;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (isTextField(tag)) {
            checkTextField(context, element);
        } else {
            checkImageWidget(context, element);
        }
    }

    private static boolean isTextField(String tag) {
        for (String textField : TEXT_FIELDS) {
            if (textField.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private void checkImageWidget(XmlContext context, Element element) {
        Attr contentDescription = element.getAttributeNodeNS(
                SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_CONTENT_DESCRIPTION);

        if (contentDescription != null) {
            String value = contentDescription.getValue();
            if (SdkConstants.VALUE_NULL.equals(value)) {
                return;
            }
            if (value.isEmpty()) {
                context.report(
                        ISSUE,
                        contentDescription,
                        context.getValueLocation(contentDescription),
                        MESSAGE_MISSING);
                return;
            }
            return;
        }

        Attr importantForAccessibility = element.getAttributeNodeNS(
                SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY);
        if (importantForAccessibility != null
                && "no".equals(importantForAccessibility.getValue())
                && context.getMainProject().getMinSdkVersion().getFeatureLevel() >= 16) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                MESSAGE_MISSING);
    }

    private void checkTextField(XmlContext context, Element element) {
        Attr hint = element.getAttributeNodeNS(
                SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_HINT);
        Attr contentDescription = element.getAttributeNodeNS(
                SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_CONTENT_DESCRIPTION);
        if (hint != null && contentDescription != null) {
            context.report(
                    ISSUE,
                    contentDescription,
                    context.getValueLocation(contentDescription),
                    MESSAGE_TEXT_FIELD);
        }
    }
}