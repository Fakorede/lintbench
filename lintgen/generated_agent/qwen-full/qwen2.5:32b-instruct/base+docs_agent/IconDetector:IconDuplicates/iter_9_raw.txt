package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector implements ResourceXmlScanner {

    private Map<String, Collection<IconInfo>> iconMap = new HashMap<>();

    @Override
    public Collection<String> getApplicableResourceTypes() {
        return ResourceType.Mipmap.getSupportedNames();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String iconName = element.getAttribute("name");
        if (iconName != null && !iconName.isEmpty()) {
            IconInfo value = new IconInfo(context.getLocation(element), iconName);
            iconMap.computeIfAbsent(iconName + ".png", k -> new ArrayList<>()).add(value);
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (String key : iconMap.keySet()) {
            Collection<IconInfo> values = iconMap.get(key);
            if (values.size() > 1) {
                Issue issue = ISSUE;
                for (IconInfo value : values) {
                    context.report(issue, value.location, "Icon is duplicated under different names: " + key);
                }
            }
        }
    }

    public static final Issue ISSUE = Issue.create(
            "DuplicateIcons",
            "Duplicated icons under different names can be consolidated to make your application smaller.",
            "If an icon is repeated under different names, you can consolidate and just use one of the icons and delete the others to make your application smaller. However, duplicated icons usually are not intentional and can sometimes point to icons that were accidentally overwritten or accidentally not updated.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static class IconInfo {
        final com.android.tools.lint.detector.api.Location location;
        final String name;

        public IconInfo(com.android.tools.lint.detector.api.Location location, String name) {
            this.location = location;
            this.name = name;
        }
    }
}