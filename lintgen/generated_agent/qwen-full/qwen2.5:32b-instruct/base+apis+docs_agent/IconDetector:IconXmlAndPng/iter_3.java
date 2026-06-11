package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class IconDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "IconSpecifiedBothAsXmlAndBitmap",
            "Icon is specified both as `.xml` file and as a bitmap",
            "If a drawable resource appears as an `.xml` file in the `drawable/` folder, it's usually not intentional for it to also appear as a bitmap using the same name; generally you expect the drawable XML file to define states and each state has a corresponding drawable bitmap.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String resourceName = getResourceName(element);
        if (resourceName != null && isBitmapResourceExists(context, resourceName)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Icon is specified both as `.xml` file and as a bitmap");
        }
    }

    private boolean isBitmapResourceExists(XmlContext context, String resourceName) {
        return context.getModule().getResources().getResource(ResourceType.DRAWABLE, resourceName).isPresent();
    }

    private String getResourceName(Element element) {
        // Assuming the resource name can be extracted from an attribute or tag
        // This method should be implemented based on how the resource name is defined in XML
        return element.getAttribute("name");
    }
}