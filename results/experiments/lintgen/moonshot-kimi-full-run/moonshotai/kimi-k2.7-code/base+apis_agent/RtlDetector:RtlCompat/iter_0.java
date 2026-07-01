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

import java.util.Collection;
import java.util.Collections;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issue",
            "The `textAlignment` attribute is only available on API 17 and higher. "
                    + "When supporting older versions, you should also specify a `gravity` or "
                    + "`layout_gravity` attribute so that older platforms still align the text "
                    + "correctly.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getMainProject().getMinSdk() >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (hasAndroidAttribute(element, SdkConstants.ATTR_GRAVITY)
                || hasAndroidAttribute(element, SdkConstants.ATTR_LAYOUT_GRAVITY)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "When targeting API levels below 17, `textAlignment` should be accompanied by "
                        + "a `gravity` or `layout_gravity` attribute for compatibility."
        );
    }

    private static boolean hasAndroidAttribute(Element element, String name) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, name);
    }
}