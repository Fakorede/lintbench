package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InefficientWeightDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_BASELINE_ALIGNED = "baselineAligned";
    private static final String ATTR_ORIENTATION = "orientation";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String VALUE_FALSE = "false";
    private static final String VALUE_VERTICAL = "vertical";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing `baselineAligned` attribute",
                    "When a `LinearLayout` is used to distribute space proportionally between "
                            + "nested layouts, the baseline alignment property should be turned "
                            + "off to make the layout computation faster. "
                            + "Add `android:baselineAligned=\"false\"` to this `LinearLayout`.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (VALUE_FALSE.equals(element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED))) {
            return;
        }

        if (VALUE_VERTICAL.equals(element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION))) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (isNestedLayout(childElement) && hasWeight(childElement)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Missing `android:baselineAligned=\"false\"`; "
                                + "nested layouts with weights slow down layout");
                return;
            }
        }
    }

    private static boolean isNestedLayout(Element element) {
        String tag = element.getTagName();
        if (tag == null) {
            return false;
        }

        return tag.endsWith("Layout")
                || tag.equals("ViewPager")
                || tag.equals("ViewPager2")
                || tag.equals("RecyclerView")
                || tag.equals("ListView")
                || tag.equals("GridView")
                || tag.equals("ScrollView")
                || tag.equals("HorizontalScrollView")
                || tag.equals("NestedScrollView")
                || tag.equals("Toolbar")
                || tag.equals("RadioGroup")
                || tag.equals("SearchView");
    }

    private static boolean hasWeight(Element element) {
        String weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
        if (weight == null || weight.isEmpty()) {
            return false;
        }

        try {
            return Float.parseFloat(weight) > 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}