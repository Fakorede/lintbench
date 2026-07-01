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
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_VECTOR = "vector";
    private static final String TAG_GRADIENT = "gradient";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String KEY_REQUIRED_API = "requiredApi";

    private static final Implementation IMPLEMENTATION =
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, but when "
                            + "`minSdkVersion` is less than 21 or 24 and the Android Gradle plugin "
                            + "1.4 or higher is used, a vector drawable placed in the `drawable` "
                            + "folder is automatically moved to `drawable-anydpi-v21` or "
                            + "`drawable-anydpi-v24` and bitmap images are generated for different "
                            + "screen resolutions for backwards compatibility.\n\n"
                            + "However, there are some limitations to this raster image generation, "
                            + "and this lint check flags elements and attributes that are not fully "
                            + "supported. You should manually check whether the generated output "
                            + "is acceptable for those older devices.",
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
        if (root == null || !TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0, n = elements.getLength(); i < n; i++) {
            Node node = elements.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String tag = element.getTagName();

            if (TAG_GRADIENT.equals(tag)) {
                reportIssue(context, element, "`<gradient>`", 24);
            } else if (TAG_CLIP_PATH.equals(tag)) {
                reportIssue(context, element, "`<clip-path>`", 21);
            }

            if (element.hasAttributeNS(ANDROID_URI, ATTR_FILL_TYPE)) {
                reportIssue(context, element, "`android:fillType`", 24);
            }
        }
    }

    private void reportIssue(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String name,
            int requiredApi) {
        String message =
                name
                        + " is not fully supported when the Android Gradle plugin generates bitmap "
                        + "images from this vector drawable for older devices; the generated "
                        + "output may not match the vector.";
        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                message,
                new LintMap.Builder().put(KEY_REQUIRED_API, requiredApi).build());
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int requiredApi = map.getInt(KEY_REQUIRED_API, 21);
        if (context.getProject().getMinSdk() >= requiredApi) {
            return false;
        }

        // The automatic PNG generation is performed by Android Gradle plugin 1.4+.
        if (context.getProject().getBuildModule() == null) {
            return false;
        }
        Object agpVersion = context.getProject().getBuildModule().getAgpVersion();
        return agpVersion != null && isAtLeast(agpVersion.toString(), 1, 4, 0);
    }

    private static boolean isAtLeast(String version, int major, int minor, int micro) {
        if (version == null) {
            return false;
        }
        String[] parts = version.split("\\D+");
        int[] target = {major, minor, micro};
        for (int i = 0; i < target.length; i++) {
            if (i >= parts.length) {
                return true;
            }
            int part;
            try {
                part = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (part > target[i]) {
                return true;
            }
            if (part < target[i]) {
                return false;
            }
        }
        return true;
    }
}