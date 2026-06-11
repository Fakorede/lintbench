package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class InefficientWeightDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InefficientLayoutWeight",
            "When only a single widget in a `LinearLayout` defines a weight, it is more efficient to assign a width/height of `0dp` to it since it will absorb all the remaining space anyway. With a declared width/height of `0dp` it does not have to measure its own size first.",
            "When only one child in a `LinearLayout` has a non-zero weight, setting its width or height to `0dp` is more efficient than using a different value because it will absorb all the remaining space anyway. This avoids unnecessary measurement steps.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("LinearLayout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("LinearLayout".equals(element.getTagName())) {
            boolean hasNonZeroWeight = false;
            int childWithWeightCount = 0;

            for (int i = 0; i < element.getChildNodes().getLength(); i++) {
                Object item = element.getChildNodes().item(i);
                if (item instanceof Element) {
                    Element childElement = (Element) item;
                    Attr weightAttr = childElement.getAttributeNode("android:layout_weight");
                    if (weightAttr != null && !"0".equals(weightAttr.getValue())) {
                        hasNonZeroWeight = true;
                        childWithWeightCount++;
                    }
                }
            }

            if (hasNonZeroWeight && childWithWeightCount == 1) {
                for (int i = 0; i < element.getChildNodes().getLength(); i++) {
                    Object item = element.getChildNodes().item(i);
                    if (item instanceof Element) {
                        Element childElement = (Element) item;
                        Attr weightAttr = childElement.getAttributeNode("android:layout_weight");
                        if (weightAttr != null && !"0".equals(weightAttr.getValue())) {
                            checkChildDimensions(context, childElement);
                        }
                    }
                }
            }
        }
    }

    private void checkChildDimensions(XmlContext context, Element element) {
        Attr widthAttr = element.getAttributeNode("android:layout_width");
        Attr heightAttr = element.getAttributeNode("android:layout_height");

        if (widthAttr != null && !"0dp".equals(widthAttr.getValue())) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Set layout_width to `0dp` for more efficient weight handling.");
        }

        if (heightAttr != null && !"0dp".equals(heightAttr.getValue())) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Set layout_height to `0dp` for more efficient weight handling.");
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }
}