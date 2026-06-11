package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Set;

public class IconDetector extends ResourceXmlDetector {
    private static final Set<ResourceType> FOLDERS = Set.of(ResourceType.DRAWABLE);

    @NonNull
    @Override
    public Set<ResourceType> getApplicableResourceTypes() {
        return FOLDERS;
    }

    private static final String ISSUE_NAME = "IconSpecifiedBothAsXmlAndBitmap";
    private static final String ISSUE_ID = "IconSpecifiedBothAsXmlAndBitmap";
    private static final Category CATEGORY = Category.CORRECTNESS;
    private static final int PRIORITY = 6;
    private static final Severity SEVERITY = Severity.WARNING;

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            "Icon is specified both as .xml file and as a bitmap",
            "If a drawable resource appears as an `.xml` file in the `drawable/` folder, it's usually not intentional for it to also appear as a bitmap using the same name; generally you expect the drawable XML file to define states and each state has a corresponding drawable bitmap.",
            CATEGORY,
            PRIORITY,
            SEVERITY,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @NonNull
    @Override
    public Set<Issue> getApplicableIssues() {
        return Set.of(ISSUE);
    }

    private boolean isXmlFile(@NonNull Context context, @NonNull String name) throws IOException, SAXException {
        Document doc = context.getDriver().getXmlDocument(context.getProject(), ResourceType.DRAWABLE, name + ".xml");
        return doc != null;
    }

    private boolean hasBitmapResource(@NonNull Context context, @NonNull String name) {
        for (String density : context.getResources().getAllDensities()) {
            if (context.getResources().getResource(ResourceType.DRAWABLE, density, name) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void visitResource(@NonNull Context context, @NonNull String resourceName, @NonNull Document document) {
        try {
            boolean isXmlDefined = isXmlFile(context, resourceName);
            boolean hasBitmap = hasBitmapResource(context, resourceName);

            if (isXmlDefined && hasBitmap) {
                context.report(ISSUE, context.getLocation(document),
                        "Icon `" + resourceName + "` is specified both as .xml file and as a bitmap");
            }
        } catch (IOException | SAXException e) {
            // Handle exceptions appropriately
            e.printStackTrace();
        }
    }
}