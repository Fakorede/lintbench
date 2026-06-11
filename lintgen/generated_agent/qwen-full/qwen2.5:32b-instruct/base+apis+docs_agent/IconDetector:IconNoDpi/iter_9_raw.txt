package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class IconDetector extends Detector implements XmlScanner {

    private static final Issue ISSUE = Issue.create(
            "AmbiguousIcon",
            "Bitmaps that appear in `drawable-nodpi` folders will not be scaled by the Android framework. If a drawable resource of the same name appears both in a `-nodpi` folder as well as a dpi folder such as `drawable-hdpi`, then the behavior is ambiguous and probably not intentional.",
            "Delete one or the other, or use different names for the icons.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Collections.emptySet())
    );

    private final Set<String> nodpiIcons = new HashSet<>();
    private final Set<String> dpiIcons = new HashSet<>();

    @Override
    public Issue getIssue() {
        return ISSUE;
    }

    @Override
    public boolean appliesToFolderType(ResourceFolderType folderType) {
        return ResourceType.DRAWABLE == folderType.getType();
    }

    @Override
    public void beforeCheckRootTag(Context context, String rootTagName) {
        nodpiIcons.clear();
        dpiIcons.clear();
    }

    @Override
    public Set<String> getApplicableElements() {
        return Collections.singleton("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getNodeName().equals("item")) {
            String iconName = element.getAttribute("name");

            ResourceFolderType folderType = context.getFolderType();
            if (folderType.getName().contains("-nodpi")) {
                nodpiIcons.add(iconName);
            } else {
                dpiIcons.add(iconName);
            }
        }
    }

    @Override
    public void afterCheckRootTag(Context context, String rootTagName) {
        for (String iconName : nodpiIcons) {
            if (dpiIcons.contains(iconName)) {
                context.report(ISSUE, context.getLocation(element),
                        "Icon `" + iconName + "` appears in both `-nodpi` and dpi folders.");
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceType.DRAWABLE == folderType.getType();
    }
}