package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE);

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
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@") || value.startsWith("@null")) {
            return;
        }

        if (value.startsWith("@android:") || value.startsWith("@+android:")) {
            return;
        }

        int slash = value.indexOf('/');
        if (slash == -1) {
            return;
        }
        int typeStart = value.startsWith("@+") ? 2 : 1;
        int colon = value.indexOf(':');
        if (colon != -1 && colon < slash) {
            typeStart = colon + 1;
        }
        String type = value.substring(typeStart, slash);
        String name = value.substring(slash + 1);

        String attrName = attribute.getLocalName();
        String namespace = attribute.getNamespaceURI();
        if ("http://schemas.android.com/apk/res/android".equals(namespace)) {
            if ("label".equals(attrName)
                    || "icon".equals(attrName)
                    || "roundIcon".equals(attrName)
                    || "logo".equals(attrName)
                    || "banner".equals(attrName)
                    || "description".equals(attrName)
                    || "sharedUserLabel".equals(attrName)) {
                return;
            }
        }

        if (doesResourceVary(context, type, name)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "The resource `" + value + "` value cannot vary by configuration");
        }
    }

    private boolean doesResourceVary(XmlContext context, String type, String name) {
        java.util.List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        for (java.io.File resFolder : resourceFolders) {
            java.io.File[] subfolders = resFolder.listFiles();
            if (subfolders == null) {
                continue;
            }
            for (java.io.File subfolder : subfolders) {
                if (!subfolder.isDirectory()) {
                    continue;
                }
                String folderName = subfolder.getName();
                if (!hasConfigQualifiers(folderName, type)) {
                    continue;
                }
                if (folderContainsResource(subfolder, type, name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasConfigQualifiers(String folderName, String type) {
        String prefix;
        if (folderName.startsWith("values")) {
            prefix = "values";
        } else if (folderName.startsWith(type)) {
            prefix = type;
        } else {
            return false;
        }

        if (folderName.length() == prefix.length()) {
            return false;
        }

        if (folderName.charAt(prefix.length()) != '-') {
            return false;
        }

        String qualifiers = folderName.substring(prefix.length() + 1);
        String[] parts = qualifiers.split("-");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (part.startsWith("v") && part.length() > 1) {
                boolean allDigits = true;
                for (int i = 1; i < part.length(); i++) {
                    if (!Character.isDigit(part.charAt(i))) {
                        allDigits = false;
                        break;
                    }
                }
                if (allDigits) {
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    private boolean folderContainsResource(java.io.File subfolder, String type, String name) {
        if (subfolder.getName().startsWith("values")) {
            java.io.File[] files = subfolder.listFiles();
            if (files == null) {
                return false;
            }
            for (java.io.File file : files) {
                if (file.isFile() && file.getName().endsWith(".xml")) {
                    if (valueFileContainsResource(file, type, name)) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            java.io.File[] files = subfolder.listFiles();
            if (files == null) {
                return false;
            }
            for (java.io.File file : files) {
                if (file.isFile()) {
                    String fileName = file.getName();
                    int dot = fileName.lastIndexOf('.');
                    String baseName = dot == -1 ? fileName : fileName.substring(0, dot);
                    if (baseName.equals(name)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private boolean valueFileContainsResource(java.io.File file, String type, String name) {
        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            if (!content.contains(name)) {
                return false;
            }

            String regex = "<\\s*([a-zA-Z0-9_\\-]+)\\s+[^>]*name\\s*=\\s*[\"']" + java.util.regex.Pattern.quote(name) + "[\"']";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String tagName = matcher.group(1);
                if (tagName.equals(type)) {
                    return true;
                }
                if (tagName.equals("item")) {
                    String fullTag = matcher.group(0);
                    if (fullTag.matches(".*\\btype\\s*=\\s*[\"']" + java.util.regex.Pattern.quote(type) + "[\"'].*")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }
}