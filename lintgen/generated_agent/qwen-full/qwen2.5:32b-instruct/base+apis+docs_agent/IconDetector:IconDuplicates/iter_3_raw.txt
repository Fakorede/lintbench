package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue DUPLICATE_ICONS = Issue.create(
            "DuplicateIcons",
            "Duplicated icons under different names can be consolidated to make the application smaller.",
            "If an icon is repeated under different names, you can consolidate and just use one of the icons and delete the others to make your application smaller. However, duplicated icons usually are not intentional and can sometimes point to icons that were accidentally overwritten or accidentally not updated.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private Map<String, Element> iconMap = new HashMap<>();

    @Override
    public Issue[] getIssues() {
        return new Issue[]{DUPLICATE_ICONS};
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        iconMap.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String iconName = element.getAttribute("name");
        if (iconName != null && !iconName.isEmpty()) {
            Element existingIcon = iconMap.get(iconName);
            if (existingIcon != null) {
                context.report(DUPLICATE_ICONS, element, context.getLocation(element),
                        "Duplicate icon found: " + iconName);
            } else {
                iconMap.put(iconName, element);
            }
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        // No-op
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }
}