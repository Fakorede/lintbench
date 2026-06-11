package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.utils.Pair;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

public class IconDetector extends BaseResourceXmlScanner {

    private Multimap<String, Pair<File, String>> iconMap = HashMultimap.create();

    @Override
    public Collection<String> getApplicableResourceTypes() {
        return ResourceType.valuesAsStrings();
    }

    @Nullable
    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String iconName = element.getAttribute("name");
        if (iconName != null && !iconName.isEmpty()) {
            File iconFile = new File(context.getFilePath());
            Pair<File, String> value = new Pair<>(iconFile, iconName);
            iconMap.put(iconName + ".png", value);
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (String key : iconMap.keySet()) {
            Collection<Pair<File, String>> values = iconMap.get(key);
            if (values.size() > 1) {
                Issue issue = ISSUE;
                for (Pair<File, String> value : values) {
                    context.report(issue, value.first, context.getLocation(value.second),
                            "Icon is duplicated under different names: " + key);
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
}