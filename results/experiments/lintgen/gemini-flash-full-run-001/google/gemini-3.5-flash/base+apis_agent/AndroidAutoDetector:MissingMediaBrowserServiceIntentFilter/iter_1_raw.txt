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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner {

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
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE)
    );

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

                if (!isMediaBrowserService(node, context)) {
                    return;
                }

                String className = node.getQualifiedName();
                if (className == null) {
                    return;
                }

                Document mergedManifest = context.getProject().getMergedManifest();
                if (mergedManifest == null) {
                    mergedManifest = context.getMainProject().getMergedManifest();
                }
                if (mergedManifest == null) {
                    return;
                }

                NodeList services = mergedManifest.getElementsByTagName("service");
                for (int i = 0; i < services.getLength(); i++) {
                    Element service = (Element) services.item(i);
                    String name = service.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                    if (name.isEmpty()) {
                        name = service.getAttribute("android:name");
                    }
                    if (name.isEmpty()) {
                        continue;
                    }

                    if (namesMatch(className, name, context)) {
                        boolean hasIntentFilter = false;
                        NodeList children = service.getChildNodes();
                        for (int j = 0; j < children.getLength(); j++) {
                            Node child = children.item(j);
                            if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                                Element intentFilter = (Element) child;
                                NodeList actions = intentFilter.getElementsByTagName("action");
                                for (int k = 0; k < actions.getLength(); k++) {
                                    Element action = (Element) actions.item(k);
                                    String actionName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                                    if (actionName.isEmpty()) {
                                        actionName = action.getAttribute("android:name");
                                    }
                                    if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                                        hasIntentFilter = true;
                                        break;
                                    }
                                }
                            }
                            if (hasIntentFilter) {
                                break;
                            }
                        }

                        if (!hasIntentFilter) {
                            context.report(
                                    ISSUE,
                                    node,
                                    context.getNameLocation(node),
                                    "Missing android.media.browse.MediaBrowserService intent-filter"
                            );
                        }
                        return;
                    }
                }
            }
        };
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

    private boolean namesMatch(@NonNull String classFqName, @NonNull String manifestName, @NonNull JavaContext context) {
        if (manifestName.isEmpty()) {
            return false;
        }
        if (manifestName.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null && !pkg.isEmpty()) {
                return classFqName.equals(pkg + manifestName);
            }
            return classFqName.endsWith(manifestName);
        }
        if (!manifestName.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null && !pkg.isEmpty()) {
                return classFqName.equals(pkg + "." + manifestName);
            }
            return classFqName.endsWith("." + manifestName);
        }
        return classFqName.equals(manifestName) || classFqName.replace('$', '.').equals(manifestName.replace('$', '.'));
    }
}