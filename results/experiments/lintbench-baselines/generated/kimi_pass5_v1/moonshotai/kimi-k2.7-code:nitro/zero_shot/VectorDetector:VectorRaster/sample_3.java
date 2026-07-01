package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class VectorDetector extends ResourceXmlDetector {

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_CLIP_PATH = "clip-path";

    private static final Implementation IMPLEMENTATION = new Implementation(
            VectorDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector image generation supports only a subset of features",
            "Vector drawables require API 21 (or API 24 for some features). When "
                    + "minSdkVersion is lower and the Android Gradle plugin is 1.4 or higher, a "
                    + "vector placed in drawable/ is moved to drawable-anydpi-v21 (or "
                    + "drawable-anydpi-v24) and PNG bitmaps are generated for backwards "
                    + "compatibility. The raster image generator does not support every "
                    + "VectorDrawable element and attribute, so you should manually verify that "
                    + "the generated output is acceptable on older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    private static final Set<String> UNSUPPORTED_TAGS = new HashSet<>(Arrays.asList(
            TAG_CLIP_PATH
    ));

    private static final Set<String> API_24_TAGS = new HashSet<>(Arrays.asList(
            "gradient", "linearGradient", "radialGradient", "sweepGradient"
    ));

    private static final Set<String> API_24_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "fillType"
    ));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_VECTOR);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!context.getProject().isGradleProject()) {
            return;
        }

        String folderName = context.file.getParentFile().getName();
        if (folderName.contains("-v21") || folderName.contains("-v24")) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 24) {
            return;
        }

        boolean willBeRasterized = minSdk < 21 || containsApi24Feature(element);
        if (!willBeRasterized) {
            return;
        }

        checkElement(context, element);
    }

    private void checkElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if (API_24_TAGS.contains(tag)) {
            reportTag(context, element, tag, true);
        } else if (UNSUPPORTED_TAGS.contains(tag)) {
            reportTag(context, element, tag, false);
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node node = attributes.item(i);
                if (!(node instanceof Attr)) {
                    continue;
                }
                Attr attr = (Attr) node;
                String name = attr.getLocalName();
                if (name == null) {
                    name = attr.getName();
                    int colon = name.indexOf(':');
                    if (colon != -1) {
                        name = name.substring(colon + 1);
                    }
                }

                if (API_24_ATTRIBUTES.contains(name)) {
                    String message = String.format(
                            "`%s` requires API 24 and is not supported when a vector drawable is "
                                    + "rasterized for older devices; verify the generated output.",
                            name);
                    context.report(ISSUE, attr, context.getLocation(attr), message);
                }
            }
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
            child = child.getNextSibling();
        }
    }

    private boolean containsApi24Feature(Element element) {
        if (API_24_TAGS.contains(element.getTagName())) {
            return true;
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node node = attributes.item(i);
                if (!(node instanceof Attr)) {
                    continue;
                }
                Attr attr = (Attr) node;
                String name = attr.getLocalName();
                if (name == null) {
                    name = attr.getName();
                    int colon = name.indexOf(':');
                    if (colon != -1) {
                        name = name.substring(colon + 1);
                    }
                }
                if (API_24_ATTRIBUTES.contains(name)) {
                    return true;
                }
            }
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && containsApi24Feature((Element) child)) {
                return true;
            }
            child = child.getNextSibling();
        }

        return false;
    }

    private void reportTag(XmlContext context, Element element, String tag, boolean api24) {
        String message;
        if (api24) {
            message = String.format(
                    "`<%s>` requires API 24 and is not supported when a vector drawable is "
                            + "rasterized for older devices; verify the generated output.",
                    tag);
        } else {
            message = String.format(
                    "`<%s>` is not supported when a vector drawable is rasterized into PNG "
                            + "images for older devices; verify the generated output.",
                    tag);
        }
        context.report(ISSUE, element, context.getLocation(element), message);
    }
}