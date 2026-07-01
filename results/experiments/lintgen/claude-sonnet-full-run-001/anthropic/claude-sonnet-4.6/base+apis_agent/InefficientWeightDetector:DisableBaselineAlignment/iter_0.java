package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue DISABLE_BASELINE_ALIGNMENT = Issue.create(
            "DisableBaselineAlignment",
            "Missing `baselineAligned` attribute",
            "When a `LinearLayout` is used to distribute the space proportionally between " +
            "nested layouts, the baseline alignment property should be turned off to " +
            "make the layout computation faster.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    InefficientWeightDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String LINEAR_LAYOUT = "LinearLayout";
    private static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    private static final String ANDROID_NS = SdkConstants.ANDROID_URI;

    public InefficientWeightDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if baselineAligned is already set to false
        Attr baselineAligned = element.getAttributeNodeNS(ANDROID_NS,
                SdkConstants.ATTR_BASELINE_ALIGNED);
        if (baselineAligned != null) {
            String value = baselineAligned.getValue();
            if (value.equals("false")) {
                // Already disabled, no issue
                return;
            }
        }

        // Check if any direct children are layouts with layout_weight set
        boolean hasWeightedChildLayout = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            // Check if child has layout_weight
            Attr weightAttr = childElement.getAttributeNodeNS(ANDROID_NS, ATTR_LAYOUT_WEIGHT);
            if (weightAttr == null) {
                continue;
            }

            // Check if child is a layout (ViewGroup)
            if (isLayout(tagName)) {
                hasWeightedChildLayout = true;
                break;
            }
        }

        if (hasWeightedChildLayout) {
            // baselineAligned is not set to false, report issue
            LintFix fix = LintFix.create()
                    .set(ANDROID_NS, SdkConstants.ATTR_BASELINE_ALIGNED, "false")
                    .build();

            context.report(
                    DISABLE_BASELINE_ALIGNMENT,
                    element,
                    context.getNameLocation(element),
                    "Set `android:baselineAligned=\"false\"` on this element for better performance",
                    fix
            );
        }
    }

    private boolean isLayout(String tagName) {
        // Common layout/ViewGroup names
        return tagName.equals("LinearLayout")
                || tagName.equals("RelativeLayout")
                || tagName.equals("FrameLayout")
                || tagName.equals("TableLayout")
                || tagName.equals("TableRow")
                || tagName.equals("GridLayout")
                || tagName.equals("ConstraintLayout")
                || tagName.equals("ScrollView")
                || tagName.equals("HorizontalScrollView")
                || tagName.equals("ListView")
                || tagName.equals("GridView")
                || tagName.equals("ViewGroup")
                || tagName.equals("merge")
                || tagName.equals("include")
                || tagName.endsWith("Layout")
                || tagName.endsWith("View") && !tagName.equals("View")
                        && !tagName.equals("TextView")
                        && !tagName.equals("ImageView")
                        && !tagName.equals("EditText")
                        && !tagName.equals("Button")
                        && !tagName.equals("CheckBox")
                        && !tagName.equals("RadioButton")
                        && !tagName.equals("Switch")
                        && !tagName.equals("ToggleButton")
                        && !tagName.equals("SeekBar")
                        && !tagName.equals("ProgressBar")
                        && !tagName.equals("RatingBar")
                        && !tagName.equals("Spinner")
                        && !tagName.equals("AutoCompleteTextView")
                        && !tagName.equals("MultiAutoCompleteTextView")
                        && !tagName.equals("VideoView")
                        && !tagName.equals("SurfaceView")
                        && !tagName.equals("TextureView")
                        && !tagName.equals("WebView");
    }
}