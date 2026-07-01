package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DisableBaselineAlignment",
            "Missing baselineAligned attribute",
            "When a LinearLayout is used to distribute space proportionally between "
            + "nested layouts using layout weights, baseline alignment computation is "
            + "unnecessary and can make layout measurement slower. Set "
            + "`android:baselineAligned=\"false\"` on the LinearLayout to improve performance.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        if (SdkConstants.VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        String baselineAligned = element.getAttributeNS(
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED);
        if (SdkConstants.VALUE_FALSE.equals(baselineAligned)) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (isLayout(childElement)) {
                String weight = childElement.getAttributeNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty()) {
                    float value;
                    try {
                        value = Float.parseFloat(weight);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if (value > 0f) {
                        context.report(
                                ISSUE,
                                element,
                                context.getLocation(element),
                                "Set android:baselineAligned=\"false\" to disable baseline "
                                + "alignment when using weights with nested layouts.");
                        return;
                    }
                }
            }
        }
    }

    private static boolean isLayout(@NonNull Element element) {
        String tag = element.getTagName();
        return tag != null && tag.endsWith("Layout");
    }
}