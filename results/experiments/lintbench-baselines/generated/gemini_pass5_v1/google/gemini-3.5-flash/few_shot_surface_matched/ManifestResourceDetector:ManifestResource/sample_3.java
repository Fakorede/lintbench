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
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

public class ManifestResourceDetector extends ResourceXmlDetector implements XmlScanner {

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
                    new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!context.getProject().isAndroidProject()) {
            return;
        }
        String fileName = context.file.getName();
        if (!"AndroidManifest.xml".equals(fileName)) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@") || value.startsWith("@android:") || value.startsWith("@null")) {
            return;
        }

        String typeAndName = value.substring(1);
        if (typeAndName.startsWith("+")) {
            typeAndName = typeAndName.substring(1);
        }
        int slash = typeAndName.indexOf('/');
        if (slash == -1) {
            return;
        }
        String type = typeAndName.substring(0, slash);
        String name = typeAndName.substring(slash + 1);

        List<File> subDirs = findResourceDefinitions(context, type, name);
        for (File subDir : subDirs) {
            String dirName = subDir.getName();
            int dash = dirName.indexOf('-');
            if (dash != -1) {
                String qualifiersStr = dirName.substring(dash + 1);
                String[] qualifiers = qualifiersStr.split("-");
                String attrName = attribute.getLocalName();
                if (attrName == null) {
                    attrName = attribute.getName();
                    int colon = attrName.indexOf(':');
                    if (colon != -1) {
                        attrName = attrName.substring(colon + 1);
                    }
                }
                boolean isExempt = isExemptAttribute(attrName);
                for (String qualifier : qualifiers) {
                    if (!isQualifierAllowed(qualifier, isExempt)) {
                        context.report(
                                ISSUE,
                                attribute,
                                context.getLocation(attribute),
                                "Resources referenced in the manifest cannot vary by configuration (except for "
                                        + "version qualifiers, or locale/density for title/icon attributes)");
                        return;
                    }
                }
            }
        }
    }

    private List<File> findResourceDefinitions(XmlContext context, String type, String name) {
        List<File> files = new ArrayList<>();
        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File resDir : resourceFolders) {
            File[] subDirs = resDir.listFiles();
            if (subDirs == null) continue;
            for (File subDir : subDirs) {
                String dirName = subDir.getName();
                if (type.equals("drawable") || type.equals("mipmap") || type.equals("layout") 
                        || type.equals("anim") || type.equals("raw") || type.equals("menu") || type.equals("xml")) {
                    if (dirName.startsWith(type)) {
                        File[] filesInDir = subDir.listFiles();
                        if (filesInDir != null) {
                            for (File f : filesInDir) {
                                String fname = f.getName();
                                int dot = fname.indexOf('.');
                                String baseName = dot == -1 ? fname : fname.substring(0, dot);
                                if (baseName.equals(name)) {
                                    files.add(subDir);
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    if (dirName.startsWith("values")) {
                        File[] xmlFiles = subDir.listFiles();
                        if (xmlFiles != null) {
                            for (File xmlFile : xmlFiles) {
                                if (xmlFile.getName().endsWith(".xml")) {
                                    if (definesValueResource(xmlFile, type, name)) {
                                        files.add(subDir);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return files;
    }

    private boolean definesValueResource(File file, String type, String name) {
        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            String searchStr = "name=\"" + name + "\"";
            int index = content.indexOf(searchStr);
            while (index != -1) {
                int tagStart = content.lastIndexOf('<', index);
                if (tagStart != -1) {
                    String tagContent = content.substring(tagStart + 1, index).trim();
                    if (tagContent.startsWith(type) || tagContent.startsWith("item") || tagContent.startsWith("style")) {
                        return true;
                    }
                }
                index = content.indexOf(searchStr, index + 1);
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private boolean isExemptAttribute(String attrName) {
        return "icon".equals(attrName)
                || "roundIcon".equals(attrName)
                || "label".equals(attrName)
                || "theme".equals(attrName)
                || "description".equals(attrName)
                || "logo".equals(attrName)
                || "banner".equals(attrName);
    }

    private boolean isQualifierAllowed(String qualifier, boolean isExempt) {
        if (qualifier.startsWith("v") && qualifier.substring(1).matches("\\d+")) {
            return true;
        }
        if (isExempt) {
            if (qualifier.equals("ldpi") || qualifier.equals("mdpi") || qualifier.equals("hdpi")
                    || qualifier.equals("xhdpi") || qualifier.equals("xxhdpi") || qualifier.equals("xxxhdpi")
                    || qualifier.equals("nodpi") || qualifier.equals("anydpi") || qualifier.startsWith("tvdpi")) {
                return true;
            }
            if (qualifier.startsWith("b+") || qualifier.length() == 2 || qualifier.length() == 3) {
                return true;
            }
            if (qualifier.startsWith("r") && qualifier.length() == 3 && Character.isUpperCase(qualifier.charAt(1))) {
                return true;
            }
        }
        return false;
    }
}