package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_ROUND_ICON;
import static com.android.SdkConstants.EXT_XML;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import com.android.utils.XmlUtils;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ManifestResourceDetector extends Detector
        implements Detector.XmlScanner, Detector.ResourceFolderScanner {

    private static final String ISSUE_ID = "ManifestResource";
    private static final String ISSUE_SUMMARY =
            "Manifest resource references cannot vary across configurations";
    private static final String ISSUE_EXPLANATION =
            "Resources referenced from AndroidManifest.xml cannot vary across "
                    + "configurations (such as locale, density, orientation, etc.). "
                    + "The only allowed configuration qualifier is version, and the "
                    + "application title/icon attributes are exempt.";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            ISSUE_SUMMARY,
            ISSUE_EXPLANATION,
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    ManifestResourceDetector.class,
                    Scope.MANIFEST_SCOPE,
                    Scope.RESOURCE_FOLDER_SCOPE
            )
    );

    private final Map<ResourceType, Map<String, Set<Variant>>> mResourceConfigurations =
            new HashMap<>();

    private static final class Variant {
        final FolderConfiguration config;
        final String folderName;

        Variant(FolderConfiguration config, String folderName) {
            this.config = config;
            this.folderName = folderName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Variant other = (Variant) o;
            return config.equals(other.config);
        }

        @Override
        public int hashCode() {
            return config.hashCode();
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mResourceConfigurations.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attr) {
        if (!ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        String value = attr.getValue();
        if (value.isEmpty() || !value.startsWith(PREFIX_RESOURCE_REF)) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isCreate()) {
            return;
        }

        ResourceType type = url.type;
        if (type == null) {
            return;
        }

        if (isApplicationTitleOrIcon(attr)) {
            return;
        }

        if (url.packageName != null
                && !url.packageName.equals(context.getMainProject().getPackage())) {
            return;
        }

        Set<Variant> variants = getVariants(type, url.name);
        if (variants == null || variants.isEmpty()) {
            return;
        }

        if (hasOnlyVersionVariation(variants)) {
            return;
        }

        String message = String.format(
                "The %1$s attribute must not reference a resource that varies across "
                        + "configurations (except by version); `%2$s` is defined in: %3$s",
                attr.getName(),
                value,
                describe(variants)
        );

        Location location = context.getValueLocation(attr);
        context.report(ISSUE, attr, location, message);
    }

    private static boolean isApplicationTitleOrIcon(@NonNull Attr attr) {
        String element = attr.getOwnerElement().getTagName();
        if (!"application".equals(element)) {
            return false;
        }

        String localName = attr.getLocalName();
        return ATTR_LABEL.equals(localName)
                || ATTR_ICON.equals(localName)
                || ATTR_ROUND_ICON.equals(localName);
    }

    private Set<Variant> getVariants(@NonNull ResourceType type, @NonNull String name) {
        Map<String, Set<Variant>> map = mResourceConfigurations.get(type);
        return map == null ? null : map.get(name);
    }

    private static boolean hasOnlyVersionVariation(@NonNull Set<Variant> variants) {
        if (variants.size() <= 1) {
            return true;
        }

        for (Variant variant : variants) {
            FolderConfiguration config = variant.config;
            if (config == null) {
                continue;
            }
            for (ResourceQualifier qualifier : config.getQualifiers()) {
                if (qualifier != null && !(qualifier instanceof VersionQualifier)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static String describe(@NonNull Set<Variant> variants) {
        StringBuilder sb = new StringBuilder();
        for (Variant variant : variants) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(variant.folderName);
        }
        return sb.toString();
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        File folder = context.file;
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        FolderConfiguration config = context.getFolderConfiguration();
        if (config == null) {
            config = new FolderConfiguration();
        }

        String folderName = folder.getName();

        if (folderType == ResourceFolderType.VALUES) {
            for (File file : files) {
                if (file.isDirectory() || !file.getName().endsWith(EXT_XML)) {
                    continue;
                }
                parseValueResourceFile(file, folderName, config);
            }
        } else {
            ResourceType type = folderType.getResourceType();
            if (type == null) {
                try {
                    type = ResourceType.valueOf(folderType.name());
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (type != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        continue;
                    }

                    String name = file.getName();
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) {
                        name = name.substring(0, dot);
                    }

                    record(type, name, config, folderName);
                }
            }
        }
    }

    private void parseValueResourceFile(
            @NonNull File file,
            @NonNull String folderName,
            @NonNull FolderConfiguration config) {
        Document document = XmlUtils.parseDocumentSilently(file, true);
        if (document == null) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null || !TAG_RESOURCES.equals(root.getTagName())) {
            return;
        }

        NodeList children = root.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) node;
            ResourceType type = getValueResourceType(element);
            if (type == null) {
                continue;
            }

            String name = element.getAttribute(ATTR_NAME);
            if (name.isEmpty()) {
                continue;
            }

            record(type, name, config, folderName);
        }
    }

    private static ResourceType getValueResourceType(@NonNull Element element) {
        String tag = element.getTagName();
        if ("item".equals(tag)) {
            String typeAttr = element.getAttribute("type");
            if (!typeAttr.isEmpty()) {
                try {
                    return ResourceType.valueOf(typeAttr.toUpperCase(Locale.US));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            return null;
        }

        switch (tag) {
            case "string":
                return ResourceType.STRING;
            case "bool":
                return ResourceType.BOOL;
            case "integer":
                return ResourceType.INTEGER;
            case "color":
                return ResourceType.COLOR;
            case "dimen":
                return ResourceType.DIMEN;
            case "drawable":
                return ResourceType.DRAWABLE;
            case "style":
                return ResourceType.STYLE;
            case "array":
            case "string-array":
            case "integer-array":
                return ResourceType.ARRAY;
            case "plurals":
                return ResourceType.PLURALS;
            case "attr":
                return ResourceType.ATTR;
            case "id":
                return ResourceType.ID;
            case "layout":
                return ResourceType.LAYOUT;
            default:
                try {
                    return ResourceType.valueOf(tag.toUpperCase(Locale.US));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
        }
    }

    private void record(
            @NonNull ResourceType type,
            @NonNull String name,
            @NonNull FolderConfiguration config,
            @NonNull String folderName) {
        Map<String, Set<Variant>> map = mResourceConfigurations.get(type);
        if (map == null) {
            map = new HashMap<>();
            mResourceConfigurations.put(type, map);
        }

        Set<Variant> set = map.get(name);
        if (set == null) {
            set = new HashSet<>();
            map.put(name, set);
        }

        set.add(new Variant(config, folderName));
    }
}