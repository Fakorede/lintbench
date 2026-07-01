package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;

public class RtlDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";
    private static final String VALUE_INHERIT = "inherit";

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "The `android:textAlignment` attribute is only available on API 17 and higher. "
                    + "On older platforms it will be ignored, so you should also specify "
                    + "`android:gravity` or `android:layout_gravity` to ensure correct "
                    + "alignment on all supported devices.",
            Category.I18N,
            3,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getProject().getMinSdk() >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();

        if (hasAndroidAttribute(element, ATTR_GRAVITY)
                || hasAndroidAttribute(element, ATTR_LAYOUT_GRAVITY)) {
            return;
        }

        String value = attribute.getValue();
        if (VALUE_INHERIT.equals(value)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "textAlignment will be ignored on API levels lower than 17; "
                        + "you should also use `android:gravity` or `android:layout_gravity`"
        );
    }

    private static boolean hasAndroidAttribute(Element element, String localName) {
        if (element.hasAttributeNS(ANDROID_URI, localName)) {
            return true;
        }
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            if (localName.equals(attr.getLocalName())) {
                return true;
            }
        }
        return false;
    }
}