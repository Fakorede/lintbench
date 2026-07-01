package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an `intent-filter` " +
            "for the action `android.media.browse.MediaBrowserService` to be able " +
            "to browse and play media.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.service.media.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat",
                "android.support.v4.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        String fqName = declaration.getQualifiedName();
        if (fqName == null) {
            return;
        }

        Document manifest = getManifest(context);
        if (manifest == null) {
            return;
        }

        NodeList services = manifest.getElementsByTagName(SdkConstants.TAG_SERVICE);
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            String name = service.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (name == null || name.isEmpty()) {
                continue;
            }

            String resolvedName = resolveClassName(context, name, manifest);
            if (fqName.equals(resolvedName)) {
                if (!hasMediaBrowserIntentFilter(service)) {
                    context.report(
                            ISSUE,
                            declaration,
                            context.getNameLocation(declaration),
                            "This service extends `MediaBrowserService` but is missing the required " +
                            "`intent-filter` for `android.media.browse.MediaBrowserService` in the manifest."
                    );
                }
                return;
            }
        }
    }

    @Nullable
    private Document getManifest(@NotNull JavaContext context) {
        try {
            Document manifest = context.getProject().getMergedManifest();
            if (manifest != null) {
                return manifest;
            }
        } catch (Throwable ignored) {
        }
        try {
            return context.getClient().getMergedManifest(context.getProject());
        } catch (Throwable ignored) {
        }
        return null;
    }

    @NotNull
    private String resolveClassName(@NotNull JavaContext context, @NotNull String name, @Nullable Document manifest) {
        if (name.startsWith(".")) {
            String pkg = getPackageName(manifest, context);
            if (pkg != null) {
                return pkg + name;
            }
        } else if (!name.contains(".")) {
            String pkg = getPackageName(manifest, context);
            if (pkg != null) {
                return pkg + "." + name;
            }
        }
        return name;
    }

    @Nullable
    private String getPackageName(@Nullable Document manifest, @NotNull JavaContext context) {
        if (manifest != null && manifest.getDocumentElement() != null) {
            String pkg = manifest.getDocumentElement().getAttribute("package");
            if (pkg != null && !pkg.isEmpty()) {
                return pkg;
            }
        }
        try {
            return context.getProject().getPackage();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean hasMediaBrowserIntentFilter(@NotNull Element service) {
        for (Element filter : getChildrenByTagName(service, SdkConstants.TAG_INTENT_FILTER)) {
            for (Element action : getChildrenByTagName(filter, SdkConstants.TAG_ACTION)) {
                String actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @NotNull
    private List<Element> getChildrenByTagName(@NotNull Element parent, @NotNull String name) {
        List<Element> matches = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                matches.add((Element) child);
            }
        }
        return matches;
    }
}