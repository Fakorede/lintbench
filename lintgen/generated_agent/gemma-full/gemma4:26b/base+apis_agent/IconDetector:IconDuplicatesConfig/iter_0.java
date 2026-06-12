package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.XmlContext.XmlContext;

import android.graphics.drawable.Drawable;
import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements XmlScanner {

    private static final Issue ISSUE = Issue.create(
            "IdenticalIconAcrossConfigurations",
            "The same icon name is provided in multiple resource configurations (e.g., hdpi and xhdpi). " +
                    "This usually indicates a copy-paste error where the icon was not resized for the specific density.",
            Issue.Level.WARNING,
            new Implementation(IconDetector.class, null)
    );

    private static final Pattern RESOURCE_REF_PATTERN = Pattern.compile("@(?:drawable|mipmap)/([^\\s@]+)");
    
    // Tracks resource name -> Set of folder types where it has been encountered.
    private final Map<String, Set<ResourceFolderType>> seenResources = new HashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        // We want to inspect all attributes for potential resource references.
        return null;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        String uri = context.getXmlContext().getDocument().getBaseURI();
        if (uri == null) return;

        // Extract the filename from the URI (e.g., .../drawable-hdpi/ic_launcher.xml -> ic_launcher)
        String fileName = uri.substring(uri.lastIndexOf('/') + 1);
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);

        ResourceFolderType folderType = context.getFolderType();
        if (isDrawableOrMipmapFolder(folderType)) {
            checkAndRecord(context, resourceName, folderType);
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null) return;

        Matcher matcher = RESOURCE_REF_PATTERN.matcher(value);
        if (matcher.find()) {
            String resourceName = matcher.group(1);
            ResourceFolderType folderType = context.getFolderType();
            
            // If we are currently scanning a drawable/mipmap folder, record this reference.
            if (isDrawableOrMipmapFolder(folderType)) {
                checkAndRecord(context, resourceName, folderType);
            } else {
                // Even if we are in a layout folder, check if the referenced resource 
                // has already been seen in a different configuration folder.
                checkReferenceOnly(context, attribute, resourceName);
            }
        }
    }

    private void checkAndRecord(XmlContext context, String name, ResourceFolderType currentFolder) {
        Set<ResourceFolderType> folders = seenResources.get(name);
        if (folders == null) {
            folders = new HashSet<>();
            seenResources.put(name, folders);
        }

        for (ResourceFolderType existingFolder : folders) {
            if (!existingFolder.equals(currentFolder)) {
                context.report(
                        ISSUE,
                        context.getXmlContext().getDocument().getBaseURI(), // Report on the file being visited
                        null, // No specific element needed if reporting at document level
                        "Resource '" + name + "' found in both " + 
                                existingFolder.getName() + " and " + currentFolder.getName() + ". " +
                                "Ensure they are actually different assets.",
                        null
                );
                break;
            }
        }
        folders.add(currentFolder);
    }

    private void checkReferenceOnly(XmlContext context, Attr attribute, String name) {
        Set<ResourceFolderType> folders = seenResources.get(name);
        if (folders != null && !folders.isEmpty()) {
            for (ResourceFolderType existingFolder : folders) {
                // We don't know the current folder of the reference, but if we see a 
                // reference to an icon that was already registered in another config, 
                // it might be a duplicate usage.
                // However, to avoid false positives in layouts, we only warn if the 
                // resource name is known to exist in multiple configs.
                if (folders.size() > 1) {
                    context.report(
                            ISSUE,
                            attribute.getOwnerDocument().getBaseURI(),
                            null,
                            "Resource '" + name + "' is used here, but it was also detected in multiple " +
                                    "different configurations. Verify if this is intentional.",
                            null
                    );
                }
            }
        }
    }

    private boolean isDrawableOrMipmapFolder(ResourceFolderType type) {
        if (type == null) return false;
        String name = type.getName().toLowerCase();
        return name.contains("drawable") || name.contains("mipmap");
    }

    // Note: In a real implementation, the IssueRegistry would be in a separate class.
    // This is included here to make the detector functional as a standalone snippet.
    public static class Implementation implements com.android.tools.lint.detector.api.Implementation {
        private final Class<?> detectorClass;
        private final Class<?> implementationClass;

        public Implementation(Class<?> detectorClass, Class<?> implementationClass) {
            this.detectorClass = detectorable(detectorClass);
            this.implementationClass = implementationClass;
        }

        private static Class<?> detectorable(Class<?> clazz) {
            return clazz;
        }

        @Override
        public Class<?> getDetector() {
            return detectorClass;
        }

        @Override
        public Implementation getImplementation() {
            return this;
        }
    }
}