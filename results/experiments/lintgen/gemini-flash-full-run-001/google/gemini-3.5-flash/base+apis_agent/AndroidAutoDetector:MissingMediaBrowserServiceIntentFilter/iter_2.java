package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends "
                    + "`android.service.media.MediaBrowserService` with an "
                    + "`intent-filter` for the action `android.media.browse.MediaBrowserService` "
                    + "to be able to browse and play media.\n"
                    + "\n"
                    + "To do this, add\n"
                    + "`<intent-filter>`\n"
                    + "    `<action android:name=\"android.media.browse.MediaBrowserService\" />`\n"
                    + "`</intent-filter>`\n"
                    + "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_AND_JAVA_FILES)
    );

    private final List<ServiceDeclaration> mServices = new ArrayList<>();
    private final Set<String> mServicesWithIntentFilter = new HashSet<>();

    private static class ServiceDeclaration {
        final String fqcn;
        final Location location;

        ServiceDeclaration(String fqcn, Location location) {
            this.fqcn = fqcn;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mServices.clear();
        mServicesWithIntentFilter.clear();
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (context.getEvaluator().isAbstract(node)) {
                    return;
                }

                if (isMediaBrowserService(node, context)) {
                    String fqcn = node.getQualifiedName();
                    if (fqcn != null) {
                        mServices.add(new ServiceDeclaration(fqcn, context.getNameLocation(node)));
                    }
                }
            }
        };
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        if (name.isEmpty()) {
            return;
        }

        String fqcn = resolveFqcn(name, context);

        if (hasMediaBrowserServiceIntentFilter(element)) {
            mServicesWithIntentFilter.add(fqcn);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (ServiceDeclaration service : mServices) {
            String normalized = normalize(service.fqcn);
            boolean found = false;
            for (String filterService : mServicesWithIntentFilter) {
                if (normalize(filterService).equals(normalized)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                context.report(
                        ISSUE,
                        service.location,
                        "Missing android.media.browse.MediaBrowserService intent-filter"
                );
            }
        }
    }

    private boolean isMediaBrowserService(@NonNull UClass node, @NonNull JavaContext context) {
        if (context.getEvaluator().inheritsFrom(node, "android.service.media.MediaBrowserService", false)
                || context.getEvaluator().inheritsFrom(node, "androidx.media.MediaBrowserServiceCompat", false)) {
            return true;
        }
        for (PsiClassType type : node.getSuperTypes()) {
            String canonicalText = type.getCanonicalText();
            if (canonicalText != null) {
                if (canonicalText.equals("android.service.media.MediaBrowserService")
                        || canonicalText.equals("androidx.media.MediaBrowserServiceCompat")
                        || canonicalText.equals("MediaBrowserService")
                        || canonicalText.equals("MediaBrowserServiceCompat")) {
                    return true;
                }
            }
        }
        PsiClass superClass = node.getSuperClass();
        if (superClass != null) {
            String name = superClass.getQualifiedName();
            if (name != null) {
                if (name.equals("android.service.media.MediaBrowserService")
                        || name.equals("androidx.media.MediaBrowserServiceCompat")) {
                    return true;
                }
            }
            String shortName = superClass.getName();
            if (shortName != null) {
                if (shortName.equals("MediaBrowserService")
                        || shortName.equals("MediaBrowserServiceCompat")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveFqcn(String name, XmlContext context) {
        if (name.startsWith(".") || !name.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg == null || pkg.isEmpty()) {
                Element root = context.getDocument().getDocumentElement();
                if (root != null && root.hasAttribute("package")) {
                    pkg = root.getAttribute("package");
                }
            }
            if (pkg != null && !pkg.isEmpty()) {
                if (name.startsWith(".")) {
                    return pkg + name;
                } else {
                    return pkg + "." + name;
                }
            }
        }
        return name;
    }

    private boolean hasMediaBrowserServiceIntentFilter(Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    String actionName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                    if (actionName.isEmpty()) {
                        actionName = action.getAttribute("android:name");
                    }
                    if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String normalize(String fqcn) {
        return fqcn.replace('$', '.');
    }
}