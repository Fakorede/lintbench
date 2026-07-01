package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;

import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DuplicateResourceDetector extends ResourceXmlDetector
        implements Detector.ResourceFolderScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            DuplicateResourceDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; that's how "
                    + "string translations are done, for example. However, defining the same "
                    + "resource more than once in the same resource folder is likely an error, "
                    + "for example attempting to add a new resource without realizing that the "
                    + "name is already used, and so on.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    private Map<File, ListMultimap<String, Location>> mValueResources;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mValueResources = Maps.newHashMap();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        if (!isValueResourceElement(element)) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        String tagName = element.getTagName();
        String type;
        if (TAG_ITEM.equals(tagName)) {
            type = element.getAttribute(ATTR_TYPE);
            if (type.isEmpty()) {
                return;
            }
        } else {
            type = tagName;
        }

        String key = type + "/" + name;
        File folder = context.file.getParentFile();
        ListMultimap<String, Location> map = mValueResources.get(folder);
        if (map == null) {
            map = ArrayListMultimap.create();
            mValueResources.put(folder, map);
        }
        map.put(key, context.getLocation(element));
    }

    private static boolean isValueResourceElement(@NonNull Element element) {
        if (element.getParentNode() == null) {
            return false;
        }
        return TAG_RESOURCES.equals(element.getParentNode().getNodeName());
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mValueResources == null) {
            return;
        }

        for (Map.Entry<File, ListMultimap<String, Location>> entry : mValueResources.entrySet()) {
            ListMultimap<String, Location> map = entry.getValue();
            for (String key : map.keySet()) {
                List<Location> locations = map.get(key);
                if (locations.size() < 2) {
                    continue;
                }

                Location first = locations.get(0);
                Location location = first;
                for (int i = 1; i < locations.size(); i++) {
                    Location next = locations.get(i);
                    location.setSecondary(next);
                    location = next;
                }

                int slash = key.indexOf('/');
                String type = key.substring(0, slash);
                String name = key.substring(slash + 1);
                context.report(
                        ISSUE,
                        first,
                        String.format("Duplicate definition of resource `%1$s/%2$s`", type, name));
            }
        }
    }

    @Override
    public void checkFolder(@NonNull Context context, @NonNull File folder) {
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
        if (folderType == null) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, List<File>> baseNames = Maps.newHashMap();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String baseName = getResourceName(file, folderType);
            List<File> list = baseNames.get(baseName);
            if (list == null) {
                list = new ArrayList<>();
                baseNames.put(baseName, list);
            }
            list.add(file);
        }

        for (Map.Entry<String, List<File>> entry : baseNames.entrySet()) {
            List<File> list = entry.getValue();
            if (list.size() < 2) {
                continue;
            }

            Location first = Location.create(list.get(0));
            Location location = first;
            for (int i = 1; i < list.size(); i++) {
                Location next = Location.create(list.get(i));
                location.setSecondary(next);
                location = next;
            }

            String baseName = entry.getKey();
            context.report(
                    ISSUE,
                    first,
                    String.format(
                            "Duplicate resource `@%1$s/%2$s` in `%3$s`",
                            folderType.getName(),
                            baseName,
                            folder.getName()));
        }
    }

    private static String getResourceName(@NonNull File file,
            @NonNull ResourceFolderType folderType) {
        String name = file.getName();
        if (folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP) {
            if (name.endsWith(".9.png")) {
                return name.substring(0, name.length() - ".9.png".length());
            }
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}