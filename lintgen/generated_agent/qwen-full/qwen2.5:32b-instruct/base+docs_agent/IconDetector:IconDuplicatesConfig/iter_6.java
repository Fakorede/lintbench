package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class IconDetector extends ResourceXmlDetector {

    private static final Issue ISSUE = Issue.create(
            "IdenticalBitmaps",
            "Identical bitmaps across various configurations",
            "If an icon is provided under different configuration parameters such as `drawable-hdpi` or `-v11`, they should typically be different. This detector catches cases where the same icon is provided in different configuration folders which is usually not intentional.",
            Category.CORRECTNESS, 6, Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Multimap<String, Pair<ResourceFolderType, Element>> iconMap = HashMultimap.create();
    private Set<File> processedFiles = new HashSet<>();

    @Override
    public void visitResource(@Nullable XmlContext context, String resourceType,
                              @Nullable String name, Document document) {
        if (!resourceType.equals(SdkConstants.FD_DRAWABLE)) {
            return;
        }

        for (Folder folder : context.getProject().getResources().getFolders()) {
            if (!folder.getType().equals(ResourceFolderType.DRAWABLE)
                    && !folder.getType().isDensityVariantOf(ResourceFolderType.DRAWABLE)) {
                continue;
            }

            for (String fileName : folder.getFileNames(resourceType, name)) {
                File filePath = new File(folder.getAbsolutePath(fileName));
                if (processedFiles.contains(filePath)) {
                    continue;
                }
                processedFiles.add(filePath);

                Element element = parseBitmapFile(context.getProject(), folder, fileName);
                if (element != null) {
                    String hash = getBitmapHash(element);
                    iconMap.put(hash, Pair.of(folder.getType(), element));
                }
            }
        }

        for (Collection<Pair<ResourceFolderType, Element>> entries : iconMap.asMap().values()) {
            if (entries.size() > 1) {
                reportIssue(context, entries);
            }
        }
    }

    private void reportIssue(@Nullable XmlContext context,
                             Collection<Pair<ResourceFolderType, Element>> entries) {
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

    private Element parseBitmapFile(Project project,
                                    Folder folder,
                                    String fileName) {
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

    private String getBitmapHash(Element element) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(element.getTextContent().getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}