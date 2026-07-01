package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.ResourceReference;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceUrl;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ManifestResourceDetector extends Detector implements XmlScanner {

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String value = attribute.getValue();
        if (value.isEmpty() || value.charAt(0) != '@') {
            return;
        }

        String namespace = attribute.getNamespaceURI();
        if (TOOLS_URI.equals(namespace)) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isTheme() || url.framework || url.type == null) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (TAG_APPLICATION.equals(owner.getNodeName())
                && ANDROID_URI.equals(namespace)
                && (ATTR_LABEL.equals(attribute.getLocalName())
                        || ATTR_ICON.equals(attribute.getLocalName()))) {
            return;
        }

        if (hasConfigurationVariation(context, url.type, url.name)) {
            Location location = context.getLocation(attribute);
            context.report(
                    ISSUE,
                    attribute,
                    location,
                    "Resources referenced from the manifest cannot vary across configurations "
                            + "(except by version); use a resource with the same value in all "
                            + "configurations.");
        }
    }

    private static boolean hasConfigurationVariation(
            @NotNull XmlContext context, @NotNull ResourceType type, @NotNull String name) {
        List<File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders == null) {
            return false;
        }

        for (File resDir : resourceFolders) {
            if (!resDir.isDirectory()) {
                continue;
            }
            File[] typeDirs = resDir.listFiles(File::isDirectory);
            if (typeDirs == null) {
                continue;
            }
            for (File typeDir : typeDirs) {
                ResourceFolderType folderType = ResourceFolderType.getFolderType(typeDir.getName());
                if (folderType == null || !contains(folderType.getTypes(), type)) {
                    continue;
                }

                String qualifiers = typeDir.getName().substring(folderType.getName().length());
                if (qualifiers.startsWith("-")) {
                    qualifiers = qualifiers.substring(1);
                }
                if (qualifiers.isEmpty() || isVersionOnly(qualifiers)) {
                    continue;
                }

                if (folderType == ResourceFolderType.VALUES) {
                    if (containsValueResource(typeDir, type, name)) {
                        return true;
                    }
                } else if (containsFileResource(typeDir, name)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean containsFileResource(@NotNull File dir, @NotNull String name) {
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String fileName = file.getName();
            int dot = fileName.indexOf('.');
            String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;
            if (baseName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsValueResource(
            @NotNull File valuesDir, @NotNull ResourceType type, @NotNull String name) {
        File[] files = valuesDir.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".xml")) {
                continue;
            }
            try {
                Document document = parseXml(file);
                if (document != null
                        && containsValueResource(document.getDocumentElement(), type, name)) {
                    return true;
                }
            } catch (Exception ignored) {
                // Ignore unreadable resource files.
            }
        }
        return false;
    }

    private static boolean containsValueResource(
            @Nullable Node node, @NotNull ResourceType type, @NotNull String name) {
        if (!(node instanceof Element)) {
            return false;
        }
        Element element = (Element) node;
        if (name.equals(element.getAttribute("name"))
                && isValueResourceTag(element, type)) {
            return true;
        }
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            if (containsValueResource(children.item(i), type, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValueResourceTag(@NotNull Element element, @NotNull ResourceType type) {
        String tag = element.getTagName();
        String typeName = type.getName();

        switch (type) {
            case ARRAY:
                return tag.equals("array")
                        || tag.equals("string-array")
                        || tag.equals("integer-array")
                        || ("item".equals(tag) && typeName.equals(element.getAttribute("type")));
            case PLURALS:
                return tag.equals("plurals")
                        || ("item".equals(tag) && typeName.equals(element.getAttribute("type")));
            case ID:
                return "item".equals(tag) && "id".equals(element.getAttribute("type"));
            default:
                return tag.equals(typeName)
                        || ("item".equals(tag) && typeName.equals(element.getAttribute("type")));
        }
    }

    private static Document parseXml(@NotNull File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file);
    }

    private static boolean contains(@Nullable ResourceType[] types, @NotNull ResourceType type) {
        if (types == null) {
            return false;
        }
        for (ResourceType t : types) {
            if (t == type) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVersionOnly(@NotNull String qualifiers) {
        for (String qualifier : qualifiers.split("-")) {
            if (!qualifier.matches("v\\d+")) {
                return false;
            }
        }
        return true;
    }

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, "
                            + "and except for a few specific package attributes such as the "
                            + "application title and icon).",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            ManifestResourceDetector.class, EnumSet.of(Scope.MANIFEST_SCOPE)));
}