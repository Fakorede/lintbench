package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IdenticalIconAcrossConfigurations",
            "Identical icon across configurations",
            "The same resource name is provided in multiple configuration folders (e.g., drawable-hdpi and drawable-xhdpi). This usually indicates a copy-paste error where the icon was not resized for the specific density.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, null)
    );

    private final Map<String, Set<String>> seenResources = new HashMap<>();
    private final Set<String> processedFiles = new HashSet<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        String name = folderType.getName().toLowerCase();
        return name.contains("drawable") || name.contains("mipmap");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) return;

        File file = context.getFiles().get(root);
        if (file == null || file.getParentFile() == null) return;

        String filePath = file.getAbsolutePath();
        if (processedFiles.contains(filePath)) return;
        processedFiles.add(filePath);

        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);

        String folderName = file.getParentFile().getName();
        String baseType = getBaseType(folderName);
        if (baseType == null) return;

        String key = baseType + ":" + resourceName;
        Set<String> seenFolders = seenResources.computeIfAbsent(key, k -> new HashSet<>());

        for (String existingFolder : seenFolders) {
            if (!existingFolder.equals(folderName)) {
                context.report(
                        ISSUE,
                        root,
                        context.getLocation(root),
                        String.format("Resource '%s' found in both %s and %s. This usually indicates a copy-paste error.",
                                resourceName, existingFolder, folderName),
                        null
                );
            }
        }
        seenFolders.add(folderName);
    }

    private String getBaseType(String folderName) {
        if (folderName.startsWith("drawable")) return "drawable";
        if (folderName.startsWith("mipmap")) return "mipmap";
        return null;
    }
}