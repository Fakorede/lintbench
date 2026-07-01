package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

public class VectorDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, " +
                    "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or " +
                    "higher is used, a vector drawable placed in the `drawable` folder is automatically " +
                    "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are " +
                    "generated for different screen resolutions for backwards compatibility.\n\n" +
                    "However, there are some limitations to this raster image generation, and this " +
                    "lint check flags elements and attributes that are not fully supported. " +
                    "You should manually check whether the generated output is acceptable for those " +
                    "older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"vector".equals(root.getTagName())) {
            return;
        }

        NodeList paths = root.getElementsByTagName("path");
        for (int i = 0; i < paths.getLength(); i++) {
            Element path = (Element) paths.item(i);
            String pathData = path.getAttribute("android:pathData");
            if (pathData != null && (pathData.indexOf('A') != -1 || pathData.indexOf('a') != -1)) {
                context.report(ISSUE, context.getLocation(path),
                        "Arcs (`A`/`a` commands) in `pathData` are not supported when rasterizing vector drawables");
            }
            if (path.hasAttribute("android:fillType")) {
                context.report(ISSUE, context.getLocation(path),
                        "`fillType` is not supported when rasterizing vector drawables");
            }
            if (path.hasAttribute("android:trimPathStart") ||
                path.hasAttribute("android:trimPathEnd") ||
                path.hasAttribute("android:trimPathOffset")) {
                context.report(ISSUE, context.getLocation(path),
                        "`trimPath*` attributes are not supported when rasterizing vector drawables");
            }
        }

        NodeList gradients = root.getElementsByTagName("gradient");
        for (int i = 0; i < gradients.getLength(); i++) {
            Element gradient = (Element) gradients.item(i);
            context.report(ISSUE, context.getLocation(gradient),
                    "`<gradient>` is not supported when rasterizing vector drawables");
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        File parent = context.file.getParentFile();
        if (parent != null) {
            String folderName = parent.getName();
            if (folderName.startsWith("drawable-v")) {
                try {
                    int version = Integer.parseInt(folderName.substring("drawable-v".length()));
                    if (version >= 21) {
                        return false;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed folder names
                }
            }
        }
        return true;
    }
}