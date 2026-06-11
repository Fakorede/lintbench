package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Map;

public class IconDetector extends ResourceXmlDetector {

    private static final Issue ISSUE = Issue.create(
            "IdenticalBitmaps",
            "Identical bitmaps across various configurations",
            "If an icon is provided under different configuration parameters such as `drawable-hdpi` or `-v11`, they should typically be different. This detector catches cases where the same icon is provided in different configuration folders which is usually not intentional.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, String> bitmapToPathMap = new HashMap<>();

    @Override
    public boolean appliesToFolderType(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        String bitmapPath = getBitmapPath(context);
        if (bitmapToPathMap.containsKey(bitmapPath)) {
            Location existingLocation = Location.create(
                    context.getDriver().getProject(),
                    bitmapToPathMap.get(bitmapPath)
            );
            context.report(ISSUE, context.getLocation(root), "Identical bitmap found in " + existingLocation);
        } else {
            bitmapToPathMap.put(bitmapPath, context.getFile().getName());
        }
    }

    private String getBitmapPath(XmlContext context) {
        return context.file.getAbsolutePath();
    }
}