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
            "IconSpecifiedBothAsXmlAndBitmap",
            "An icon is specified both as an `.xml` file and a bitmap with the same name.",
            "This issue reports when a drawable resource appears in both `drawable/` folder as an XML file and as a bitmap using the same name. Generally, you expect the drawable XML file to define states and each state has a corresponding drawable bitmap.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE)
            )
    );

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList("android:icon");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String resourceName = attribute.getValue();
        if (resourceName != null && !resourceName.isEmpty()) {
            checkForDuplicateIcons(context, resourceName);
        }
    }

    private void checkForDuplicateIcons(XmlContext context, String resourceName) {
        boolean hasXmlIcon = context.getResourceFolder().getFile(resourceName + ".xml") != null;
        boolean hasBitmapIcon = context.getResourceFolder().getFile(resourceName + ".png") != null
                || context.getResourceFolder().getFile(resourceName + ".jpg") != null;

        if (hasXmlIcon && hasBitmapIcon) {
            context.report(ISSUE, attribute, context.getLocation(attribute), "An icon is specified both as an `.xml` file and a bitmap with the same name.");
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }
}