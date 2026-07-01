package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Node;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClassType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an `intent-filter` for the " +
            "action `android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n" +
            "To do this, add\n" +
            "```xml\n" +
            "<intent-filter>\n" +
            "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n" +
            "</intent-filter>\n" +
            "```\n" +
            "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (node.isInterface()) {
                    return;
                }
                if (context.getEvaluator().isAbstract(node)) {
                    return;
                }
                if (!isMediaBrowserService(context, node)) {
                    return;
                }

                String className = node.getQualifiedName();
                if (className == null) {
                    className = node.getName();
                }
                if (className == null) {
                    return;
                }

                boolean hasFilter = false;

                // 1. Try merged manifest first
                try {
                    Document mergedManifest = context.getProject().getMergedManifest();
                    if (mergedManifest != null) {
                        NodeList services = mergedManifest.getElementsByTagName("service");
                        for (int i = 0; i < services.getLength(); i++) {
                            Element serviceElement = (Element) services.item(i);
                            String serviceName = getAndroidAttribute(serviceElement, "name");
                            if (matchName(className, serviceName)) {
                                if (hasMediaBrowserServiceIntentFilter(serviceElement)) {
                                    hasFilter = true;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable e) {
                    // ignore
                }

                // 2. Fallback to individual manifest files
                if (!hasFilter) {
                    List<File> manifestFiles = context.getProject().getManifestFiles();
                    for (File manifestFile : manifestFiles) {
                        CharSequence contents = context.getClient().readFile(manifestFile);
                        Document document = context.getClient().getXmlDocument(manifestFile, contents);
                        if (document == null) {
                            continue;
                        }
                        NodeList services = document.getElementsByTagName("service");
                        for (int i = 0; i < services.getLength(); i++) {
                            Element serviceElement = (Element) services.item(i);
                            String serviceName = getAndroidAttribute(serviceElement, "name");
                            if (matchName(className, serviceName)) {
                                if (hasMediaBrowserServiceIntentFilter(serviceElement)) {
                                    hasFilter = true;
                                    break;
                                }
                            }
                        }
                        if (hasFilter) {
                            break;
                        }
                    }
                }

                if (!hasFilter) {
                    context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node),
                            "Service extends `MediaBrowserService` but does not declare the required intent-filter for `android.media.browse.MediaBrowserService` in the manifest."
                    );
                }
            }
        };
    }

    private boolean isMediaBrowserService(JavaContext context, UClass node) {
        if (context.getEvaluator().inheritsFrom(node, "android.service.media.MediaBrowserService", false)
                || context.getEvaluator().inheritsFrom(node, "androidx.media.MediaBrowserServiceCompat", false)
                || context.getEvaluator().inheritsFrom(node, "android.support.v4.media.MediaBrowserServiceCompat", false)) {
            return true;
        }
        for (PsiClassType superType : node.getSuperTypes()) {
            String typeText = superType.getCanonicalText();
            if (typeText.contains("MediaBrowserService") || typeText.contains("MediaBrowserServiceCompat")) {
                return true;
            }
        }
        return false;
    }

    private boolean matchName(String className, String serviceName) {
        if (serviceName == null || className == null) {
            return false;
        }
        if (serviceName.equals(className)) {
            return true;
        }
        if (serviceName.startsWith(".")) {
            return className.endsWith(serviceName);
        }
        if (!serviceName.contains(".")) {
            return className.endsWith("." + serviceName);
        }
        return className.endsWith("." + serviceName);
    }

    private String getAndroidAttribute(Element element, String localName) {
        String value = element.getAttributeNS("http://schemas.android.com/apk/res/android", localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute("android:" + localName);
        }
        return value;
    }

    private boolean hasMediaBrowserServiceIntentFilter(Element serviceElement) {
        NodeList intentFilters = serviceElement.getElementsByTagName("intent-filter");
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList actions = filter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = getAndroidAttribute(action, "name");
                if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }
}