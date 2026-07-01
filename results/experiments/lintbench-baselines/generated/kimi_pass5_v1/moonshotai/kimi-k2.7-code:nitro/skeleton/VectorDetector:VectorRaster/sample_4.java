package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String VECTOR_TAG = "vector";
    private static final String GRADIENT_TAG = "gradient";
    private static final String FILL_TYPE_ATTR = "fillType";
    private static final String FILL_TYPE_EVEN_ODD = "evenOdd";
    private static final String KEY_MIN_API = "min-api";
    private static final int API_LEVEL = 24;

    private static final Implementation IMPLEMENTATION =
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, "
                            + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                            + "higher is used, a vector drawable placed in the `drawable` folder is automatically "
                            + "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are "
                            + "generated for different screen resolutions for backwards compatibility.\n"
                            + "\n"
                            + "However, there are some limitations to this raster image generation, and this "
                            + "lint check flags elements and attributes that are not fully supported. You should "
                            + "manually check whether the generated output is acceptable for those older devices.",
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
        if (root == null || !VECTOR_TAG.equals(root.getTagName())) {
            return;
        }
        visitElement(context, root);
    }

    private void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (GRADIENT_TAG.equals(tag)) {
            reportIssue(
                    context,
                    element,
                    "Vector drawables with gradient fills are not supported in the raster images generated for older devices");
        }

        String fillType = element.getAttributeNS(ANDROID_URI, FILL_TYPE_ATTR);
        if (FILL_TYPE_EVEN_ODD.equals(fillType)) {
            reportIssue(
                    context,
                    element,
                    "Vector drawables with fillType=\"evenOdd\" are not supported in the raster images generated for older devices");
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                visitElement(context, (Element) child);
            }
        }
    }

    private void reportIssue(
            @NonNull XmlContext context, @NonNull Element element, @NonNull String message) {
        LintMap map = LintMap.create();
        map.put(KEY_MIN_API, API_LEVEL);
        Location location = context.getLocation(element);
        context.report(ISSUE, location, message, map);
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int requiredApi = map.getInt(KEY_MIN_API, API_LEVEL);
        return context.getProject().getMinSdk() < requiredApi;
    }
}