package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue DUPLICATE_DEFINITION =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate definitions of resources",
                    "You can define a resource multiple times in different resource folders; that's how "
                            + "string translations are done, for example. However, defining the same "
                            + "resource more than once in the same resource folder is likely an error, "
                            + "for example attempting to add a new resource without realizing that the "
                            + "name is already used.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Map<String, List<Location>>> mDefinitions = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent == null
                || parent.getNodeType() != Node.ELEMENT_NODE
                || !TAG_RESOURCES.equals(parent.getLocalName())) {
            return;
        }

        String type = getResourceType(element);
        if (type == null || type.isEmpty()) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        File folder = context.file.getParentFile();
        String folderPath = folder != null ? folder.getPath() : context.file.getPath();

        Map<String, List<Location>> folderMap =
                mDefinitions.computeIfAbsent(folderPath, k -> new HashMap<>());
        String key = type + "/" + name;
        folderMap.computeIfAbsent(key, k -> new ArrayList<>()).add(context.getNameLocation(element));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, List<Location>>> folderEntry : mDefinitions.entrySet()) {
            for (Map.Entry<String, List<Location>> entry : folderEntry.getValue().entrySet()) {
                List<Location> locations = entry.getValue();
                if (locations.size() < 2) {
                    continue;
                }

                int separator = entry.getKey().indexOf('/');
                String type = entry.getKey().substring(0, separator);
                String name = entry.getKey().substring(separator + 1);

                Location first = locations.get(0);
                for (int i = 1; i < locations.size(); i++) {
                    Location location = locations.get(i);
                    Location related =
                            Location.create(first.getFile(), first.getStart(), first.getEnd());
                    location.setSecondary(related);

                    String message =
                            String.format(
                                    "`R.%s.%s` has already been defined in this resource folder",
                                    type, name);
                    context.report(new Incident(DUPLICATE_DEFINITION, location, message));
                }
            }
        }
    }

    @Nullable
    private static String getResourceType(@NonNull Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            return null;
        }

        switch (tag) {
            case TAG_ITEM:
                return element.getAttribute(ATTR_TYPE);
            case "string-array":
            case "integer-array":
                return "array";
            case "skip":
            case "eat-comment":
            case "public":
            case "java-symbol":
            case "add-resource":
            case "overlay":
                return null;
            default:
                return tag;
        }
    }
}