package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import java.util.Collection;

public class ManifestResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, and "
                            + "except for a few specific package attributes such as the application "
                            + "title and icon).",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    new Implementation(
                            ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    private java.util.Set<String> varyingResources = null;

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value.startsWith("@") && !value.startsWith("@android:")) {
            int slash = value.indexOf('/');
            if (slash >= 0) {
                String type = value.substring(1, slash);
                String name = value.substring(slash + 1);
                int colon = type.indexOf(':');
                if (colon >= 0) {
                    String pkg = type.substring(0, colon);
                    if ("android".equals(pkg)) {
                        return;
                    }
                    type = type.substring(colon + 1);
                }

                if (isExpectedToVary(attribute.getLocalName())) {
                    return;
                }

                ensureVaryingResources(context);
                if (varyingResources.contains(type + "/" + name)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "Resources referenced from the manifest cannot vary by configuration "
                                    + "(except by version qualifier)");
                }
            }
        }
    }

    private static boolean isExpectedToVary(String name) {
        return "label".equals(name)
                || "icon".equals(name)
                || "roundIcon".equals(name)
                || "logo".equals(name)
                || "banner".equals(name)
                || "description".equals(name)
                || "sharedUserLabel".equals(name)
                || "theme".equals(name);
    }

    private void ensureVaryingResources(XmlContext context) {
        if (varyingResources != null) {
            return;
        }
        varyingResources = new java.util.HashSet<>();
        java.util.List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        for (java.io.File resDir : resourceFolders) {
            java.io.File[] subDirs = resDir.listFiles();
            if (subDirs == null) {
                continue;
            }
            for (java.io.File subDir : subDirs) {
                if (!subDir.isDirectory()) {
                    continue;
                }
                String folderName = subDir.getName();
                String type = getResourceTypeFromFolder(folderName);
                if (type == null) {
                    continue;
                }
                if (isConfigVaryingFolder(folderName, type)) {
                    if (type.equals("values")) {
                        java.io.File[] xmlFiles = subDir.listFiles();
                        if (xmlFiles != null) {
                            for (java.io.File xmlFile : xmlFiles) {
                                if (xmlFile.isFile() && xmlFile.getName().endsWith(".xml")) {
                                    parseValuesXml(xmlFile);
                                }
                            }
                        }
                    } else {
                        java.io.File[] files = subDir.listFiles();
                        if (files != null) {
                            for (java.io.File file : files) {
                                if (file.isFile()) {
                                    String name = file.getName();
                                    int dot = name.indexOf('.');
                                    if (dot > 0) {
                                        name = name.substring(0, dot);
                                    }
                                    varyingResources.add(type + "/" + name);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static String getResourceTypeFromFolder(String folderName) {
        int dash = folderName.indexOf('-');
        if (dash >= 0) {
            return folderName.substring(0, dash);
        }
        return folderName;
    }

    private static boolean isConfigVaryingFolder(String folderName, String type) {
        if (folderName.equals(type)) {
            return false;
        }
        if (!folderName.startsWith(type + "-")) {
            return false;
        }
        String[] qualifiers = folderName.substring(type.length() + 1).split("-");
        for (String q : qualifiers) {
            if (!q.matches("v\\d+")) {
                return true;
            }
        }
        return false;
    }

    private void parseValuesXml(java.io.File xmlFile) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(xmlFile);
            org.w3c.dom.NodeList children = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    org.w3c.dom.Element element = (org.w3c.dom.Element) child;
                    String tagName = element.getTagName();
                    String name = element.getAttribute("name");
                    if (name != null && !name.isEmpty()) {
                        String type = tagName;
                        if (tagName.equals("item")) {
                            String typeAttr = element.getAttribute("type");
                            if (typeAttr != null && !typeAttr.isEmpty()) {
                                type = typeAttr;
                            }
                        }
                        varyingResources.add(type + "/" + name);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
    }
}