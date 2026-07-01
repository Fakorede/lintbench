package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

import java.util.Collection;
import java.util.Collections;

public class InefficientWeightDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DisableBaselineAlignment",
            "Missing baselineAligned attribute",
            "When a LinearLayout is used to distribute space proportionally between nested layouts, "
                    + "the baselineAligned property should be turned off to make layout computation faster.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);
        if (!orientation.isEmpty() && !SdkConstants.VALUE_HORIZONTAL.equals(orientation)) {
            return;
        }

        String baselineAligned = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BASELINE_ALIGNED);
        if (!baselineAligned.isEmpty()) {
            return;
        }

        for (int i = 0, n = element.getChildNodes().getLength(); i < n; i++) {
            Node child = element.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (childElement.getTagName().endsWith(SdkConstants.SUFFIX_LAYOUT)) {
                    String weight = childElement.getAttributeNS(
                            SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WEIGHT);
                    if (!weight.isEmpty()) {
                        context.report(
                                ISSUE,
                                element,
                                context.getLocation(element),
                                "Set android:baselineAligned=\"false\" on this LinearLayout for better performance");
                        return;
                    }
                }
            }
        }
    }
}