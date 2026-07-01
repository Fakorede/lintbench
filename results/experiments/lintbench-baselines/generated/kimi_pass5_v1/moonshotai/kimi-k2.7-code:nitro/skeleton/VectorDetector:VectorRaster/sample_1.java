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
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";

    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_TRIM_PATH_START = "trimPathStart";
    private static final String ATTR_TRIM_PATH_END = "trimPathEnd";
    private static final String ATTR_TRIM_PATH_OFFSET = "trimPathOffset";
    private static final String ATTR_STROKE_LINE_CAP = "strokeLineCap";
    private static final String ATTR_STROKE_LINE_JOIN = "strokeLineJoin";
    private static final String ATTR_STROKE_MITER_LIMIT = "strokeMiterLimit";

    private static final String KEY_MIN_SDK = "minSdk";

    private static final Implementation IMPLEMENTATION =
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, but when "
                            + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 "
                            + "or higher is used, a vector drawable placed in the `drawable` folder "
                            + "is automatically moved to `drawable-anydpi-v21` or "
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
        checkElement(context, root);
    }

    private static void checkElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if (TAG_CLIP_PATH.equals(tag)) {
            report(
                    context,
                    element,
                    "Vector clip paths are not supported when a vector drawable is rasterized for "
                            + "older devices. Please verify the generated bitmaps manually.",
                    21);
        } else if (TAG_GRADIENT.equals(tag)) {
            report(
                    context,
                    element,
                    "Vector gradients are not supported when a vector drawable is rasterized for "
                            + "older devices. Please verify the generated bitmaps manually.",
                    24);
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                if (!ANDROID_URI.equals(attr.getNamespaceURI())) {
                    continue;
                }

                String name = attr.getLocalName();
                if (ATTR_FILL_TYPE.equals(name)
                        || ATTR_TRIM_PATH_START.equals(name)
                        || ATTR_TRIM_PATH_END.equals(name)
                        || ATTR_TRIM_PATH_OFFSET.equals(name)
                        || ATTR_STROKE_LINE_CAP.equals(name)
                        || ATTR_STROKE_LINE_JOIN.equals(name)
                        || ATTR_STROKE_MITER_LIMIT.equals(name)) {
                    report(
                            context,
                            attr,
                            "The \""
                                    + name
                                    + "\" attribute is not supported when a vector drawable is "
                                    + "rasterized for older devices. Please verify the generated "
                                    + "bitmaps manually.",
                            24);
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }

    private static void report(
            XmlContext context, Node node, String message, int requiredApi) {
        Location location = context.getLocation(node);
        LintMap map = new LintMap.Builder().put(KEY_MIN_SDK, requiredApi).build();
        context.report(ISSUE, location, message, map);
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        if (!context.getProject().isGradleProject()) {
            return false;
        }

        int requiredApi = map.getInt(KEY_MIN_SDK, 21);
        if (context.getProject().getMinSdk() >= requiredApi) {
            return false;
        }

        File file = incident.getLocation().getFile();
        if (file != null) {
            File parent = file.getParentFile();
            if (parent != null) {
                String folderName = parent.getName();
                int index = folderName.lastIndexOf("-v");
                if (index != -1) {
                    String suffix = folderName.substring(index + 2);
                    int end = 0;
                    while (end < suffix.length() && Character.isDigit(suffix.charAt(end))) {
                        end++;
                    }
                    if (end > 0) {
                        try {
                            int folderVersion = Integer.parseInt(suffix.substring(0, end));
                            if (folderVersion >= requiredApi) {
                                return false;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        return true;
    }
}