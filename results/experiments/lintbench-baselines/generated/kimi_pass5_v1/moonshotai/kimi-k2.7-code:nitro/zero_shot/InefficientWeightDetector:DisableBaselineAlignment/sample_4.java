package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BASELINE_ALIGNED;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.TAG_LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_VERTICAL;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class InefficientWeightDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            InefficientWeightDetector.class,
            Scope.RESOURCE_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "DisableBaselineAlignment",
            "Missing baselineAligned attribute",
            "When a LinearLayout is used to distribute space proportionally using "
                    + "android:layout_weight on its children, the android:baselineAligned property "
                    + "should be set to false to make layout computation faster.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_LINEAR_LAYOUT, "android.widget.LinearLayout");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (VALUE_VERTICAL.equals(element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION))) {
            return;
        }

        String baselineAligned = element.getAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED);
        if ("false".equals(baselineAligned)) {
            return;
        }

        boolean hasWeightedChild = false;
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String weight = childElement.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                if (weight != null && !weight.isEmpty()) {
                    try {
                        if (Float.parseFloat(weight) > 0f) {
                            hasWeightedChild = true;
                            break;
                        }
                    } catch (NumberFormatException e) {
                        // ignore invalid values
                    }
                }
            }
            child = child.getNextSibling();
        }

        if (hasWeightedChild) {
            context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Set android:baselineAligned=\"false\" to improve layout performance"
            );
        }
    }
}