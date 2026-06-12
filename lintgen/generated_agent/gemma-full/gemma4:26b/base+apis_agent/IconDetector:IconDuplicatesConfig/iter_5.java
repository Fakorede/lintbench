package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IdenticalIconAcrossConfigurations",
            "Identical icon across configurations",
            "The same resource name is provided in multiple configuration folders (e.g., drawable-hdpi and drawable-xhdpi). This usually indicates a copy-paste error where the icon was not resized for the specific density.",
            com.android.tools.lint.detector.api.Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, null)
    );

    private final Map<String, Set<String>> seenResources = new ConcurrentHashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        String name = folderType.getName().toLowerCase();
        return name.contains("drawable") || name.contains("mipmap");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) return;

        File file = null;
        try {
            // Cast to Context to access getFiles() which is defined in the base Context class
            Map<org.w3c.dom.Node, File> files = ((com.android.tools.lint.detector.api.Context) context).getFiles();
            file = files.get(root);
        } catch (Exception e) {
            return;
        }

        if (file == null || file.getParentFile() == null) return;

        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);

        String folderName = file.getParentFile().getName();
        
        // We only care about folders that are part of drawable or mipmap configurations
        if (!folderName.contains("drawable") && !folderName.contains("mipmap")) {
            return;
        }

        String key = resourceName;
        Set<String> seenFolders = seenResources.computeIfAbsent(key, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

        for (String existingFolder : seenFolders) {
            // If the resource name is the same but the folder configuration is different
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
}