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

import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Map;

public class IconDetector extends ResourceXmlDetector {

    private static final Issue ISSUE = Issue.create(
            "AmbiguousIcon",
            "Bitmaps that appear in `drawable-nodpi` folders will not be scaled by the Android framework. If a drawable resource of the same name appears both in a `-nodpi` folder as well as a dpi folder such as `drawable-hdpi`, then the behavior is ambiguous and probably not intentional.",
            "Delete one or the other, or use different names for the icons.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, Location> nodpiLocations = new HashMap<>();
    private final Map<String, Location> dpiLocations = new HashMap<>();

    @Override
    public boolean appliesToFolderType(ResourceFolderType folderType) {
        return ResourceType.DRAWABLE.equals(folderType.getType());
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("name");
        if (context.getFolderType().isNodpi()) {
            nodpiLocations.put(name, Location.create(context));
        } else {
            dpiLocations.put(name, Location.create(context));
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        for (String name : nodpiLocations.keySet()) {
            if (dpiLocations.containsKey(name)) {
                context.report(
                        ISSUE,
                        nodpiLocations.get(name),
                        "Bitmap appears in both `-nodpi` and dpi folders. Delete one or the other, or use different names for the icons."
                );
                context.report(
                        ISSUE,
                        dpiLocations.get(name),
                        "Bitmap appears in both `-nodpi` and dpi folders. Delete one or the other, or use different names for the icons."
                );
            }
        }
    }

}