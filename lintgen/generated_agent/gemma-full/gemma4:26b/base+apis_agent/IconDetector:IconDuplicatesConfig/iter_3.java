package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
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
            "The same resource name is provided in multiple configuration folders (e.g., drawable-hdpi and drawable-xhdpi). " +
                    "This usually indicates a copy-paste error where the icon was not resized for the specific density.",
            Severity.WARNING,
            new Implementation(IconDetector.class, null)
    );

    private final Map<String, Set<String>> seenResources = new HashMap<>();

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
        String uri = context.getDocument().getBaseURI();
        if (uri == null) return;

        // The URI looks like: file:///path/to/project/res/drawable-hdpi/icon.xml
        int resIndex = uri.lastIndexOf("/res/");
        if (resIndex == -1) return;

        // Skip the "/res/" part (5 characters)
        String pathAfterRes = uri.substring(resIndex + 5);
        int lastSlash = pathAfterRes.lastIndexOf('/');
        if (lastSlash == -1) return;

        String folder = pathAfterRes.substring(0, lastSlash);
        String fileNameWithExt = pathAfterRes.substring(lastSlash + 1);
        
        int dotIndex = fileNameWithExt.lastIndexOf('.');
        String resourceName = (dotIndex == -1) ? fileNameWithExt : fileNameWithExt.substring(0, dotIndex);

        Set<String> folders = seenResources.get(resourceName);
        Element root = document.getDocumentElement();
        if (root == null) return;

        if (folders != null) {
            for (String existingFolder : folders) {
                // If the resource name is found in a different configuration folder, report it.
                if (!existingFolder.equals(folder)) {
                    context.report(
                            ISSUE,
                            root,
                            context.getLocation(root),
                            "Resource '" + resourceName + "' found in both " + existingFolder + " and " + folder + ".",
                            null
                    );
                }
            }
        } else {
            folders = new HashSet<>();
            seenResources.put(resourceName, folders);
        }
        folders.add(folder);
    }
}