package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableSet;
import com.intellij.psi.PsiDirectory;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Set;

public class IconDetector extends Detector implements ResourceXmlScanner {
    private static final Set<ResourceFolderType> FOLDERS = ImmutableSet.of(ResourceFolderType.DRAWABLE);

    @NonNull
    @Override
    public Set<ResourceFolderType> getApplicableResourceFolders() {
        return FOLDERS;
    }

    @Override
    public boolean isApplicable(@NonNull ResourceType type) {
        return type == ResourceType.DRAWABLE;
    }

    private static final String ISSUE_NAME = "IconSpecifiedBothAsXmlAndBitmap";
    private static final String ISSUE_ID = "IconSpecifiedBothAsXmlAndBitmap";
    private static final Category CATEGORY = Category.CORRECTNESS;
    private static final int PRIORITY = 6;
    private static final Severity SEVERITY = Severity.WARNING;

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            "Icon is specified both as .xml file and as a bitmap",
            "If a drawable resource appears as an `.xml` file in the `drawable/` folder, it's usually not intentional for it to also appear as a bitmap using the same name; generally you expect the drawable XML file to define states and each state has a corresponding drawable bitmap.",
            CATEGORY,
            PRIORITY,
            SEVERITY,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @NonNull
    @Override
    public Set<Issue> getApplicableIssues() {
        return ImmutableSet.of(ISSUE);
    }

    private boolean isXmlFile(@NonNull PsiDirectory directory, @NonNull String name) throws IOException, SAXException {
        Document doc = XmlUtils.parse(directory.findFile(name + ".xml").getVirtualFile().contentsToByteArray());
        return doc != null;
    }

    private boolean hasBitmapResource(@NonNull ResourceRepository repository, @NonNull String name) {
        for (Density density : Density.values()) {
            if (repository.getResource(ResourceType.DRAWABLE, Pair.of(density, name)) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void visitResource(@NonNull ResourceFile resourceFile, @NonNull Context context) {
        String resourceName = resourceFile.getNameWithoutExtension();
        PsiDirectory directory = resourceFile.getDirectory();

        if (directory == null) {
            return;
        }

        try {
            boolean isXmlDefined = isXmlFile(directory, resourceName);
            boolean hasBitmap = hasBitmapResource(resourceFile.getProject().getRepository(), resourceName);

            if (isXmlDefined && hasBitmap) {
                context.report(ISSUE, resourceFile.getXmlElement(),
                        context.getLocation(resourceFile.getXmlElement()),
                        "Icon `" + resourceName + "` is specified both as .xml file and as a bitmap");
            }
        } catch (IOException | SAXException e) {
            // Handle exceptions appropriately
            e.printStackTrace();
        }
    }
}