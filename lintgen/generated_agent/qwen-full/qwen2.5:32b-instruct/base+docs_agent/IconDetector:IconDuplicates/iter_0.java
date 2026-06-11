package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.utils.Pair;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

public class IconDetector extends ResourceXmlScanner {

    private Multimap<String, Pair<String, String>> iconMap = HashMultimap.create();

    @Override
    public Collection<String> getApplicableResourceTypes() {
        return ResourceType.IMAGE.getFolders();
    }

    @Nullable
    @Override
    public Document createDocument(@NonNull String path) throws IOException, SAXException {
        return super.createDocument(path);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String iconPath = getIconPath(element);
        if (iconPath != null) {
            ResourceRepository repository = context.getProject().getRepository();
            Set<String> densities = new HashSet<>();
            for (Density density : Density.values()) {
                String path = SdkConstants.FN_RES + "/" +
                        ResourceType.DRAWABLE.getFolderType() + "-" + density.getName() + "/" +
                        iconPath;
                if (repository.getFile(ResourceFolderType.DRAWABLE, path) != null) {
                    densities.add(density.getName());
                }
            }

            for (String density : densities) {
                String key = iconPath + "_" + density;
                Pair<String, String> value = new Pair<>(context.getFilePath(), element.getAttribute("name"));
                iconMap.put(key, value);
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (String key : iconMap.keySet()) {
            Collection<Pair<String, String>> values = iconMap.get(key);
            if (values.size() > 1) {
                IssueRegistry registry = context.getIssueRegistry();
                Issue issue = registry.getIssue("DuplicateIcons");
                for (Pair<String, String> value : values) {
                    context.report(issue, value.first, context.getLocation(value.second),
                            "Icon is duplicated under different names: " + key);
                }
            }
        }
    }

    @Nullable
    private String getIconPath(@NonNull Element element) {
        String name = element.getAttribute("name");
        if (name != null && !name.isEmpty()) {
            return name + ".png";
        }
        return null;
    }
}