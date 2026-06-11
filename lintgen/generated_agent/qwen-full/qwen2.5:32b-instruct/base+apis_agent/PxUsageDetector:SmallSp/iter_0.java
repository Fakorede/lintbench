package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class PxUsageDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "SmallTextSize",
            "Avoid using text sizes smaller than 11sp.",
            "Using small text sizes can make your app difficult to read for some users. Consider increasing the text size to at least 11sp.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    PxUsageDetector.class,
                    Collections.emptySet())
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_SIZE);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null && value.endsWith("sp")) {
            try {
                float textSize = Float.parseFloat(value.substring(0, value.length() - 2));
                if (textSize < 11f) {
                    context.report(ISSUE, attribute, context.getLocation(attribute),
                            "Text size is too small. Consider using at least 11sp.");
                }
            } catch (NumberFormatException e) {
                // Ignore invalid values
            }
        }
    }

}