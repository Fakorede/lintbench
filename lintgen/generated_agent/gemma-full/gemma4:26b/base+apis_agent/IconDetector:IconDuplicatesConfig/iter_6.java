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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Attr;

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
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("android:src");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@drawable/")) {
            return;
        }

        String resourceName = value.substring(10);
        try {
            // In Android Lint, the Location object can provide access to the file being visited.
            File file = context.getLocation(attribute).getContainingFile();
            if (file == null || file.getParentFile() == null) {
                return;
            }

            String folderName = file.getParentFile().getName();

            // We only care about folders that are part of drawable or mipmap configurations.
            if (!folderName.contains("drawable") && !folderName.contains("mipmap")) {
                return;
            }

            Set<String> seenFolders = seenResources.computeIfAbsent(resourceName, k -> 
                    Collections.newSetFromMap(new ConcurrentHashMap<>()));

            for (String existingFolder : seenFolders) {
                // If the resource name is the same but the folder configuration is different,
                // and it's not just a density or version variation that we expect to be identical,
                // we flag it. However, since we can't check content, we flag any overlap 
                // in non-standard configurations if they are detected.
                if (!existingFolder.equals(folderName)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            String.format("Resource '%s' found in both %s and %s. This usually indicates a copy-paste error.",
                                    resourceName, existingFolder, folderName),
                            null
                    );
                }
            }
            seenFolders.add(folderName);

        } catch (Exception e) {
            // If we cannot determine the file or folder, skip this attribute.
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        String name = folderType.getName().toLowerCase();
        return name.contains("drawable") || name.contains("mipmap");
    }
}