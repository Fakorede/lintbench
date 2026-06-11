package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.*;

public class IconDetector extends Detector implements XmlScanner {
    private static final Issue DUPLICATE_ICONS = Issue.create(
            "DuplicateIcons",
            "Duplicated icons under different names can be consolidated to make the application smaller.",
            "If an icon is repeated under different names, you can consolidate and just use one of the icons and delete the others to make your application smaller. However, duplicated icons usually are not intentional and can sometimes point to icons that were accidentally overwritten or accidentally not updated.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private Map<String, List<Element>> iconMap = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("item");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, Element element) {
        String name = element.getAttribute("name");
        if (name != null && name.startsWith("ic_")) {
            iconMap.computeIfAbsent(name, k -> new ArrayList<>()).add(element);
        }
    }

    @Override
    public void visitDocument(@NotNull XmlContext context, Document document) {
        for (List<Element> elements : iconMap.values()) {
            if (elements.size() > 1) {
                Element firstElement = elements.get(0);
                for (int i = 1; i < elements.size(); i++) {
                    context.report(DUPLICATE_ICONS, elements.get(i), context.getLocation(elements.get(i)),
                            "Duplicate icon found. Consolidate and use one of the icons to make your application smaller.");
                }
            }
        }
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return ResourceFolderType.DRAWABLE.equals(folderType);
    }
}