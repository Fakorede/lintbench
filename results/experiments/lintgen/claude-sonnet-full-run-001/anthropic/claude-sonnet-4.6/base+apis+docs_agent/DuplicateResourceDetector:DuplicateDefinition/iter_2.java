package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_DECLARE_STYLEABLE;
import static com.android.SdkConstants.TAG_ATTR;

/**
 * Checks for duplicate resource definitions within the same resource folder.
 */
public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate resource definition",
            "You can define a resource multiple times in different resource folders; that's how " +
            "string translations are done, for example. However, defining the same resource " +
            "more than once in the same resource folder is likely an error, for example " +
            "attempting to add a new resource without realizing that the name is already used, " +
            "and so on.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.ALL_RESOURCES_SCOPE
            )
    );

    /**
     * Map from resource folder path to a map of resource keys to their locations.
     * Resource key format: "type/name"
     */
    private final Map<String, Map<String, Location>> mFolderToResourceMap = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFolderToResourceMap.clear();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !TAG_RESOURCES.equals(root.getTagName())) {
            return;
        }

        // Use the absolute path of the parent folder as the key so files in the same
        // folder (e.g. res/values/strings.xml and res/values/dimens.xml) share the map.
        File resourceFolder = context.file.getParentFile();
        String folderKey = resourceFolder != null ? resourceFolder.getAbsolutePath() : "values";

        Map<String, Location> resourceMap = mFolderToResourceMap.get(folderKey);
        if (resourceMap == null) {
            resourceMap = new HashMap<>();
            mFolderToResourceMap.put(folderKey, resourceMap);
        }

        // Iterate over child elements of <resources>
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element resourceElement = (Element) child;
            String tag = resourceElement.getTagName();

            String resourceType = getResourceType(tag, resourceElement);
            String resourceName = resourceElement.getAttribute(ATTR_NAME);

            if (resourceType == null || resourceName == null || resourceName.isEmpty()) {
                continue;
            }

            // Normalize the resource name (replace dots and hyphens with underscores)
            String normalizedName = resourceName.replace('.', '_').replace('-', '_');

            String key = resourceType + "/" + normalizedName;

            Location location = context.getLocation(resourceElement);

            if (resourceMap.containsKey(key)) {
                // Duplicate found - report it
                Location previousLocation = resourceMap.get(key);
                context.report(
                        ISSUE,
                        resourceElement,
                        location,
                        String.format("`%1$s` has already been defined in this folder", key)
                );
            } else {
                resourceMap.put(key, location);
            }

            // For declare-styleable, also check child attr elements
            if (TAG_DECLARE_STYLEABLE.equals(tag)) {
                checkDeclareStyleableAttrs(context, resourceElement, normalizedName, resourceMap);
            }
        }
    }

    private void checkDeclareStyleableAttrs(
            @NonNull XmlContext context,
            @NonNull Element declareStyleable,
            @NonNull String styleableName,
            @NonNull Map<String, Location> resourceMap) {

        Map<String, Location> attrMap = new HashMap<>();
        NodeList children = declareStyleable.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element attrElement = (Element) child;
            if (!TAG_ATTR.equals(attrElement.getTagName())) {
                continue;
            }
            String attrName = attrElement.getAttribute(ATTR_NAME);
            if (attrName == null || attrName.isEmpty()) {
                continue;
            }
            String normalizedAttrName = attrName.replace('.', '_').replace('-', '_');
            String key = styleableName + "_" + normalizedAttrName;

            Location location = context.getLocation(attrElement);
            if (attrMap.containsKey(key)) {
                context.report(
                        ISSUE,
                        attrElement,
                        location,
                        String.format("Duplicate attribute `%1$s` in `%2$s`", attrName, styleableName)
                );
            } else {
                attrMap.put(key, location);
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null; // We use visitDocument instead
    }

    /**
     * Returns the resource type string for a given XML tag and element.
     */
    @Nullable
    private static String getResourceType(@NonNull String tag, @NonNull Element element) {
        if (TAG_ITEM.equals(tag)) {
            String type = element.getAttribute(ATTR_TYPE);
            if (type != null && !type.isEmpty()) {
                return type;
            }
            return null;
        } else if (TAG_RESOURCES.equals(tag)) {
            return null;
        } else {
            switch (tag) {
                case "string":
                    return "string";
                case "string-array":
                    return "array";
                case "integer-array":
                    return "array";
                case "array":
                    return "array";
                case "plurals":
                    return "plurals";
                case "color":
                    return "color";
                case "dimen":
                    return "dimen";
                case "integer":
                    return "integer";
                case "bool":
                    return "bool";
                case "fraction":
                    return "fraction";
                case "style":
                    return "style";
                case "declare-styleable":
                    return "declare-styleable";
                case "attr":
                    return "attr";
                case "drawable":
                    return "drawable";
                case "layout":
                    return "layout";
                case "menu":
                    return "menu";
                case "raw":
                    return "raw";
                case "xml":
                    return "xml";
                case "font":
                    return "font";
                case "navigation":
                    return "navigation";
                case "transition":
                    return "transition";
                case "interpolator":
                    return "interpolator";
                case "animator":
                    return "animator";
                case "anim":
                    return "anim";
                case "mipmap":
                    return "mipmap";
                default:
                    return tag;
            }
        }
    }
}