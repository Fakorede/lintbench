package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlDetector;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ManifestResourceDetector extends XmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Resources referenced from the Android manifest cannot vary across configurations "
                    + "(except by version). Configuration-dependent resources such as those in "
                    + "values-land, drawable-hdpi, etc. should not be referenced from the manifest, "
                    + "with the exception of attributes such as the application label and icon.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_LABEL = "label";
    private static final String ATTR_ICON = "icon";

    private final Map<String, Boolean> mConfigVaryingResources = new HashMap<>();

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mConfigVaryingResources.clear();
        List<File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders != null) {
            computeConfigVaryingResources(resourceFolders);
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node node = attributes.item(i);
            if (node.getNodeType() != Node.ATTRIBUTE_NODE) {
                continue;
            }
            Attr attr = (Attr) node;
            String value = attr.getValue();
            if (value.isEmpty() || !value.startsWith("@")) {
                continue;
            }
            ResourceUrl url = ResourceUrl.parse(value);
            if (url == null || url.type == null || url.framework || url.packageName != null) {
                continue;
            }
            if (isExempt(attr)) {
                continue;
            }

            String key = url.type.getName() + "/" + url.name;
            Boolean varies = mConfigVaryingResources.get(key);
            if (varies == null || !varies) {
                continue;
            }

            String message = String.format(Locale.US,
                    "Resources referenced from the manifest cannot vary across configurations "
                            + "(except by version); `%1$s/%2$s` has configuration-specific "
                            + "variations.", url.type.getName(), url.name);
            context.report(ISSUE, attr, context.getValueLocation(attr), message);
        }
    }

    private static boolean isExempt(@NonNull Attr attr) {
        if (!ANDROID_URI.equals(attr.getNamespaceURI())) {
            return false;
        }
        String localName = attr.getLocalName();
        return ATTR_LABEL.equals(localName) || ATTR_ICON.equals(localName);
    }

    private void computeConfigVaryingResources(@NonNull List<File> resourceFolders) {
        for (File resDir : resourceFolders) {
            if (!resDir.isDirectory()) {
                continue;
            }
            File[] typeDirs = resDir.listFiles();
            if (typeDirs == null) {
                continue;
            }
            for (File typeDir : typeDirs) {
                if (!typeDir.isDirectory()) {
                    continue;
                }
                String dirName = typeDir.getName();
                int dash = dirName.indexOf('-');
                String typeName = dash == -1 ? dirName : dirName.substring(0, dash);
                ResourceType type = ResourceType.getEnum(typeName);
                if (type == null) {
                    continue;
                }
                String qualifiers = dash == -1 ? "" : dirName.substring(dash + 1);
                boolean hasConfig = hasNonVersionQualifier(qualifiers);

                if (type == ResourceType.VALUES) {
                    collectValues(typeDir, hasConfig);
                } else {
                    collectFiles(typeDir, type, hasConfig);
                }
            }
        }
    }

    private void collectValues(@NonNull File valuesDir, boolean hasConfig) {
        File[] files = valuesDir.listFiles();
        if (files == null) {
            return;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".xml")) {
                continue;
            }
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(file);
                Element root = doc.getDocumentElement();
                if (root == null) {
                    continue;
                }
                NodeList children = root.getChildNodes();
                for (int i = 0, n = children.getLength(); i < n; i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element childElement = (Element) child;
                    String name = childElement.getAttribute("name");
                    if (name.isEmpty()) {
                        continue;
                    }
                    ResourceType childType = getValueResourceType(childElement);
                    if (childType == null) {
                        continue;
                    }
                    mark(childType.getName() + "/" + name, hasConfig);
                }
            } catch (Exception ignore) {
                // Malformed XML will be reported by other checks; ignore here.
            }
        }
    }

    private void collectFiles(@NonNull File dir, @NonNull ResourceType type, boolean hasConfig) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String fileName = file.getName();
            int dot = fileName.lastIndexOf('.');
            String name = dot == -1 ? fileName : fileName.substring(0, dot);
            mark(type.getName() + "/" + name, hasConfig);
        }
    }

    private void mark(@NonNull String key, boolean hasConfig) {
        if (hasConfig) {
            mConfigVaryingResources.put(key, Boolean.TRUE);
        } else if (!mConfigVaryingResources.containsKey(key)) {
            mConfigVaryingResources.put(key, Boolean.FALSE);
        }
    }

    @Nullable
    private static ResourceType getValueResourceType(@NonNull Element element) {
        String tag = element.getTagName();
        ResourceType type = ResourceType.getEnum(tag);
        if (type != null) {
            return type;
        }
        if ("string-array".equals(tag) || "integer-array".equals(tag)) {
            return ResourceType.ARRAY;
        }
        if ("item".equals(tag)) {
            String typeAttr = element.getAttribute("type");
            if (!typeAttr.isEmpty()) {
                return ResourceType.getEnum(typeAttr);
            }
        }
        return null;
    }

    private static boolean hasNonVersionQualifier(@Nullable String qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return false;
        }
        for (String qualifier : qualifiers.split("-")) {
            if (qualifier.isEmpty()) {
                continue;
            }
            if (!qualifier.matches("v\\d+")) {
                return true;
            }
        }
        return false;
    }
}