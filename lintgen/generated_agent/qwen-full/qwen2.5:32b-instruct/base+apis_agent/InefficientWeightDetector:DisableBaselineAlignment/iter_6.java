package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Collections;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InefficientBaselineAlignment",
            "Missing `baselineAligned` attribute in LinearLayout with weights",
            "When a `LinearLayout` is used to distribute the space proportionally between nested layouts, " +
                    "the baseline alignment property should be turned off to make the layout computation faster.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.LAYOUT_LINEARLAYOUT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.hasAttribute(SdkConstants.ATTR_LAYOUT_WEIGHT)) {
            String baselineAligned = element.getAttribute(SdkConstants.ATTR_BASELINE_ALIGNED);
            if (baselineAligned.isEmpty() || Boolean.parseBoolean(baselineAligned)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Missing `baselineAligned` attribute in LinearLayout with weights");
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // No-op
    }
}