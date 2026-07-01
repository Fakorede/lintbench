package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when "
            + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or higher "
            + "is used, a vector drawable placed in the `drawable` folder is automatically "
            + "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are "
            + "generated for different screen resolutions for backwards compatibility.\n\n"
            + "However, there are some limitations to this raster image generation, and this "
            + "lint check flags elements and attributes that are not fully supported. You "
            + "should manually check whether the generated output is acceptable for those "
            + "older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"vector".equals(root.getTagName())) {
            return;
        }

        NodeList gradients = document.getElementsByTagName("gradient");
        for (int i = 0; i < gradients.getLength(); i++) {
            Node node = gradients.item(i);
            context.report(ISSUE, context.getLocation(node),
                    "Gradient elements in vector drawables may not rasterize correctly for pre-API 24 devices.");
        }

        NodeList clipPaths = document.getElementsByTagName("clip-path");
        for (int i = 0; i < clipPaths.getLength(); i++) {
            Node node = clipPaths.item(i);
            context.report(ISSUE, context.getLocation(node),
                    "Clip-path elements in vector drawables may not rasterize correctly for older devices.");
        }

        NodeList paths = document.getElementsByTagName("path");
        for (int i = 0; i < paths.getLength(); i++) {
            Element path = (Element) paths.item(i);
            if (path.hasAttribute("android:fillType")) {
                context.report(ISSUE, context.getLocation(path.getAttributeNode("android:fillType")),
                        "The fillType attribute may not be fully supported during raster image generation.");
            }
        }
    }

    @Override
    public boolean filterIncident(@NotNull Context context, @NotNull Incident incident) {
        return true;
    }
}