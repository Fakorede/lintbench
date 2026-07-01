package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.jetbrains.annotations.NonNull;

public class VectorDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
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
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<Project, Boolean> mUseSupportLibraryMap = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("vector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (useSupportLibrary(context)) {
            return;
        }

        int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        if (minSdk >= 24) {
            return;
        }

        checkElement(context, element, minSdk);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element, int minSdk) {
        String tagName = element.getTagName();

        if (minSdk < 21) {
            if ("clip-path".equals(tagName)) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The Android Gradle plugin's vector image generator does not support `<clip-path>`"
                );
            }
        }

        if (minSdk < 24) {
            if ("gradient".equals(tagName) || "radialGradient".equals(tagName) || "sweepGradient".equals(tagName)) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Gradients are only supported in vector drawables on API 24 and higher"
                );
            }
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attrNode = attributes.item(i);
            if (attrNode instanceof Attr) {
                Attr attr = (Attr) attrNode;
                String value = attr.getValue();
                String name = attr.getLocalName();
                String namespace = attr.getNamespaceURI();

                if (minSdk < 21) {
                    if (value.startsWith("?")) {
                        context.report(
                                ISSUE,
                                attr,
                                context.getLocation(attr),
                                "Theme references are not supported by the Android Gradle plugin's vector image generator"
                        );
                    }

                    if ("autoMirrored".equals(name) && SdkConstants.ANDROID_URI.equals(namespace) && "true".equals(value)) {
                        context.report(
                                ISSUE,
                                attr,
                                context.getLocation(attr),
                                "Auto-mirrored is only supported in vector drawables on API 21 and higher"
                        );
                    }
                }

                if (minSdk < 24) {
                    if ("fillType".equals(name) && SdkConstants.ANDROID_URI.equals(namespace) && "evenOdd".equalsIgnoreCase(value)) {
                        context.report(
                                ISSUE,
                                attr,
                                context.getLocation(attr),
                                "The `evenOdd` fillType is only supported in vector drawables on API 24 and higher"
                        );
                    }
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child, minSdk);
            }
        }
    }

    private boolean useSupportLibrary(@NonNull XmlContext context) {
        Project project = context.getProject();
        Boolean cached = mUseSupportLibraryMap.get(project);
        if (cached != null) {
            return cached;
        }
        boolean useSupportLib = false;
        File dir = project.getDir();
        File buildGradle = new File(dir, "build.gradle");
        if (!buildGradle.exists()) {
            buildGradle = new File(dir, "build.gradle.kts");
        }
        if (buildGradle.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(buildGradle.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                if (content.contains("useSupportLibrary") &&
                        (content.contains("true") || content.contains("useSupportLibrary = true") || content.contains("useSupportLibrary(true)"))) {
                    useSupportLib = true;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        mUseSupportLibraryMap.put(project, useSupportLib);
        return useSupportLib;
    }
}