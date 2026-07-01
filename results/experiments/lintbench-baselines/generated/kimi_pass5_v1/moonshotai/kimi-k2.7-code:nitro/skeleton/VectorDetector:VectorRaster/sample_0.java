package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_PATH = "path";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_AUTO_MIRRORED = "autoMirrored";
    private static final String ATTR_FILL_TYPE = "fillType";

    private static final String KEY_MIN_API = "minApi";
    private static final int API_21 = 21;
    private static final int API_24 = 24;

    private static final Implementation IMPLEMENTATION =
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 (or API 24 when using certain features), but when "
                            + "`minSdkVersion` is lower and the Android Gradle Plugin generates "
                            + "fallback PNG bitmaps for backwards compatibility, some vector "
                            + "features cannot be rendered correctly in those generated bitmaps. "
                            + "This check flags the elements and attributes that are not fully "
                            + "supported. You should manually verify the generated output on older "
                            + "devices.",
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
        if (root == null) {
            return;
        }

        if (!TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        // Only vectors placed directly in res/drawable are auto-rasterized.
        File parent = context.file.getParentFile();
        if (parent == null || !"drawable".equals(parent.getName())) {
            return;
        }

        checkElement(context, root);
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int minApi = map.getInt(KEY_MIN_API, API_21);
        return context.getMainProject().getMinSdk() < minApi;
    }

    private void checkElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if (TAG_CLIP_PATH.equals(tag)) {
            report(context, element,
                    "`<clip-path>` elements are not supported when generating raster images for "
                            + "older devices; check the generated bitmaps.",
                    API_21);
        } else if (TAG_GRADIENT.equals(tag)) {
            report(context, element,
                    "`<gradient>` elements are not supported when generating raster images for "
                            + "older devices; check the generated bitmaps.",
                    API_24);
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            int length = attributes.getLength();
            for (int i = 0; i < length; i++) {
                Attr attr = (Attr) attributes.item(i);
                if (attr == null || !ANDROID_URI.equals(attr.getNamespaceURI())) {
                    continue;
                }
                String name = attr.getLocalName();
                if (name == null) {
                    continue;
                }

                if (TAG_VECTOR.equals(tag) && ATTR_AUTO_MIRRORED.equals(name)) {
                    report(context, element,
                            "`android:autoMirrored` is not supported when generating raster images "
                                    + "for older devices; check the generated bitmaps.",
                            API_21);
                } else if (TAG_PATH.equals(tag) && ATTR_FILL_TYPE.equals(name)) {
                    report(context, element,
                            "`android:fillType` is not supported when generating raster images for "
                                    + "older devices; check the generated bitmaps.",
                            API_24);
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

    private void report(XmlContext context, Node node, String message, int minApi) {
        Incident incident = new Incident(ISSUE, context.getLocation(node), message);
        incident.setMap(LintMap.of(KEY_MIN_API, minApi));
        context.report(incident);
    }
}