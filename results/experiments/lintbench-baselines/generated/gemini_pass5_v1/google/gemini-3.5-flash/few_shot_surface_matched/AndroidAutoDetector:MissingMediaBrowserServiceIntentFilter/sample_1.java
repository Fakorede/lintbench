package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_AND_MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `intent-filter` "
                            + "for the action `android.media.browse.MediaBrowserService` to be able "
                            + "to browse and play media.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private final Map<String, ServiceInfo> mManifestServices = new HashMap<>();

    public AndroidAutoDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mManifestServices.clear();
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull java.io.File file) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if ("service".equals(tagName)) {
            String pkg = element.getOwnerDocument().getDocumentElement().getAttribute("package");
            String serviceName = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (serviceName != null && !serviceName.isEmpty()) {
                String fqName = getFullyQualifiedName(pkg, serviceName);
                boolean hasIntentFilter = false;
                org.w3c.dom.NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    org.w3c.dom.Node child = children.item(i);
                    if (child instanceof org.w3c.dom.Element && "intent-filter".equals(child.getNodeName())) {
                        org.w3c.dom.NodeList filterChildren = child.getChildNodes();
                        for (int j = 0; j < filterChildren.getLength(); j++) {
                            org.w3c.dom.Node filterChild = filterChildren.item(j);
                            if (filterChild instanceof org.w3c.dom.Element && "action".equals(filterChild.getNodeName())) {
                                org.w3c.dom.Element actionEl = (org.w3c.dom.Element) filterChild;
                                String actionName = actionEl.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                                if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                                    hasIntentFilter = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                mManifestServices.put(fqName, new ServiceInfo(hasIntentFilter, element));
            }
        }
    }

    private String getFullyQualifiedName(String pkg, String className) {
        if (className.startsWith(".")) {
            return pkg + className;
        } else if (!className.contains(".")) {
            return pkg + "." + className;
        }
        return className;
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.service.media.MediaBrowserService");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        ServiceInfo info = mManifestServices.get(qualifiedName);
        if (info == null) {
            info = mManifestServices.get(qualifiedName.replace('$', '.'));
        }

        if (info == null) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Service " + declaration.getName() + " should be declared in the manifest with an intent-filter for android.media.browse.MediaBrowserService"
            );
        } else if (!info.hasIntentFilter) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Missing intent-filter for android.media.browse.MediaBrowserService in service " + declaration.getName()
            );
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        // No-op
    }

    private static class ServiceInfo {
        final boolean hasIntentFilter;
        final org.w3c.dom.Element element;

        ServiceInfo(boolean hasIntentFilter, org.w3c.dom.Element element) {
            this.hasIntentFilter = hasIntentFilter;
            this.element = element;
        }
    }
}