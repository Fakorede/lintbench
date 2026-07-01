package com.android.tools.lint.checks;

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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_BASELINE_ALIGNED = "baselineAligned";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ATTR_ORIENTATION = "orientation";
    private static final String VALUE_VERTICAL = "vertical";
    private static final String VALUE_FALSE = "false";

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Disable Baseline Alignment",
                    "When a LinearLayout is used to distribute space proportionally between its"
                            + " children using layout_weight, the baseline alignment property"
                            + " should be turned off to make layout computation faster. Set"
                            + " android:baselineAligned=\"false\" on the LinearLayout.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            return;
        }

        String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if (VALUE_FALSE.equals(baselineAligned)) {
            return;
        }

        NodeList children = element.getChildNodes();
        boolean hasWeight = false;
        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
            if (!weight.isEmpty() && !"0".equals(weight)) {
                hasWeight = true;
                break;
            }
        }

        if (hasWeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Set android:baselineAligned=\"false\" to improve layout performance when"
                            + " using layout_weight");
        }
    }
}