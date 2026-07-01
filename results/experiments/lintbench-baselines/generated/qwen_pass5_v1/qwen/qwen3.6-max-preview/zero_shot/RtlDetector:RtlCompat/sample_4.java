package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class RtlDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
        "RtlCompat",
        "Right-to-left text compatibility issues",
        "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
        "if you are supporting older versions than API 17, you must also specify a " +
        "gravity or layout_gravity attribute, since older platforms will ignore the " +
        "`textAlignment` attribute.",
        Category.RTL,
        6,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getMinSdk() >= 17) {
            return;
        }

        Node textAlignment = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_ALIGNMENT);
        if (textAlignment == null) {
            return;
        }

        Node gravity = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY);
        Node layoutGravity = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY);

        if (gravity == null && layoutGravity == null) {
            context.report(ISSUE, element, context.getLocation(textAlignment),
                "When using `textAlignment`, also specify `android:gravity` or " +
                "`android:layout_gravity` for compatibility with API levels < 17");
        }
    }
}