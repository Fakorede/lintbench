package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
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

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IncorrectIconSize",
            "Launcher icons should follow predefined sizes for each density.",
            "Ensure that your launcher icons are the correct size to fit in with the overall look of the platform.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Collections.emptyList())
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceType.DRAWABLE == folderType.getType();
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr formatAttr = element.getAttributeNode("format");
        if (formatAttr != null && "png".equals(formatAttr.getValue())) {
            checkIconSize(context, element);
        }
    }

    private void checkIconSize(XmlContext context, Element element) {
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null || !nameAttr.getValue().startsWith("ic_launcher")) {
            return;
        }

        Attr sizeAttr = element.getAttributeNode("size");
        if (sizeAttr != null) {
            String sizeValue = sizeAttr.getValue();
            if (!isValidSize(sizeValue)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Launcher icon has incorrect size: " + sizeValue);
            }
        } else {
            context.report(ISSUE, element, context.getLocation(element),
                    "Launcher icon is missing the 'size' attribute.");
        }
    }

    private boolean isValidSize(String size) {
        // Define valid sizes for each density
        List<String> validSizes = Collections.singletonList("48x48");
        return validSizes.contains(size);
    }
}