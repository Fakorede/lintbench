package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue CLASHING_PNG_FILES = Issue.create(
            "ClashingPngFiles",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, the image file and the nine patch file will both map to the same drawable resource, `@drawable/file`, which is probably not what was intended.",
            "This issue reports when a PNG file and a 9-PNG file with the same base name are found in the same resource folder.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    Collections.emptyList()));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceType.DRAWABLE.equals(folderType.getType());
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        List<Element> elements = getAllChildren(document.getDocumentElement());
        for (Element element : elements) {
            String name = element.getAttribute("name");
            if (name != null && !name.isEmpty()) {
                checkForClashingFiles(context, name);
            }
        }
    }

    private void checkForClashingFiles(XmlContext context, String baseName) {
        boolean hasPng = context.getResources(ResourceType.DRAWABLE).stream()
                .anyMatch(resource -> resource.getName().equals(baseName + ".png"));
        boolean hasNinePatch = context.getResources(ResourceType.DRAWABLE).stream()
                .anyMatch(resource -> resource.getName().equals(baseName + ".9.png"));

        if (hasPng && hasNinePatch) {
            context.report(CLASHING_PNG_FILES, null, "Clashing PNG and 9-PNG files found: " + baseName);
        }
    }

    private List<Element> getAllChildren(Element parent) {
        NodeList children = parent.getChildNodes();
        List<Element> result = new java.util.ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child instanceof Element) {
                result.add((Element) child);
            }
        }
        return result;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(XmlContext context) {
        return null;
    }
}