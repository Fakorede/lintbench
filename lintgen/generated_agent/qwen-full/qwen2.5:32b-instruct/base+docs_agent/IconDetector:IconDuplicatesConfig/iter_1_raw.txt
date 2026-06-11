package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class IconDetector extends ResourceXmlScanner {

    private static final String ISSUE_NAME = "IdenticalBitmaps";
    private static final String ISSUE_ID = "IdenticalBitmaps";
    private static final Category CATEGORY = Category.CORRECTNESS;
    private static final int PRIORITY = 6;
    private static final Severity SEVERITY = Severity.WARNING;

    @NonNull
    public static Issue getIssue() {
        return Issue.create(
                ISSUE_ID,
                "Identical bitmaps across various configurations",
                "If an icon is provided under different configuration parameters such as `drawable-hdpi` or `-v11`, they should typically be different. This detector catches cases where the same icon is provided in different configuration folders which is usually not intentional.",
                CATEGORY, PRIORITY, SEVERITY,
                new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));
    }

    @Override
    public void visitResource(@NonNull ResourceRepository repository,
                              @Nullable ResourceType resourceType,
                              @Nullable String name,
                              @Nullable Density density,
                              @Nullable Document document) {
        if (resourceType == null || !resourceType.equals(ResourceType.DRAWABLE)) {
            return;
        }

        Multimap<String, Pair<ResourceFolderType, Element>> iconMap = HashMultimap.create();
        Set<String> processedFiles = new HashSet<>();

        for (ResourceRepository.Folder folder : repository.getFolders()) {
            if (!folder.getType().equals(ResourceFolderType.DRAWABLE) && !folder.getType().isDensityVariantOf(ResourceFolderType.DRAWABLE)) {
                continue;
            }

            for (String fileName : folder.getFileNames(resourceType, name)) {
                String filePath = folder.getAbsolutePath(fileName);
                if (processedFiles.contains(filePath)) {
                    continue;
                }
                processedFiles.add(filePath);

                Element element = parseBitmapFile(folder, fileName);
                if (element != null) {
                    iconMap.put(getBitmapHash(element), Pair.of(folder.getType(), element));
                }
            }
        }

        for (Collection<Pair<ResourceFolderType, Element>> entries : iconMap.asMap().values()) {
            if (entries.size() > 1) {
                reportIssue(entries);
            }
        }
    }

    private void reportIssue(@NonNull Collection<Pair<ResourceFolderType, Element>> entries) {
        Pair<ResourceFolderType, Element> firstEntry = null;
        for (Pair<ResourceFolderType, Element> entry : entries) {
            if (firstEntry == null) {
                firstEntry = entry;
                continue;
            }
            String message = "Identical bitmap found in different configuration folders: "
                    + firstEntry.first.getName() + " and " + entry.first.getName();
            context.report(ISSUE, entry.second, context.getLocation(entry.second),
                    message);
        }
    }

    @Nullable
    private Element parseBitmapFile(@NonNull ResourceRepository.Folder folder,
                                    @NonNull String fileName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(folder.getFile(fileName));
            return document.getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            // Ignore parsing errors
        }
        return null;
    }

    @NonNull
    private String getBitmapHash(@NonNull Element element) {
        // Simplified hash function for demonstration purposes.
        // In a real implementation, consider using a more robust hashing mechanism.
        return java.util.Objects.hash(element.getTextContent()).toString();
    }
}