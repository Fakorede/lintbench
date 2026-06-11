package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashSet;
import java.util.Set;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue CLASHING_PNG_FILES = Issue.create(
            "ClashingPngFiles",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, the image file and the nine patch file will both map to the same drawable resource, `@drawable/file`, which is probably not what was intended.",
            "This issue reports when a PNG file and a 9-PNG file have clashing names in the same resource folder.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    private Set<String> pngFiles = new HashSet<>();
    private Set<String> ninePatchFiles = new HashSet<>();

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
        for (Element element : getAllElements(document)) {
            String name = getName(element);
            if (name != null && name.endsWith(".png")) {
                if (name.endsWith(".9.png")) {
                    ninePatchFiles.add(name.substring(0, name.length() - 7));
                } else {
                    pngFiles.add(name.substring(0, name.length() - 4));
                }
            }
        }

        for (String baseName : pngFiles) {
            if (ninePatchFiles.contains(baseName)) {
                context.report(CLASHING_PNG_FILES, document, context.getLocation(document),
                        "Clashing PNG and 9-PNG files: " + baseName);
            }
        }
    }

    private Set<Element> getAllElements(@NotNull Document document) {
        // Implementation to get all elements from the document
        return new HashSet<>();
    }

    private String getName(Element element) {
        // Implementation to get the name of the resource file from the element
        return "";
    }
}