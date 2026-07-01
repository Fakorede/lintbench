package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `intent-filter` for "
                            + "the action `android.media.browse.MediaBrowserService` to be able to "
                            + "browse and play media. To do this, add `<intent-filter>` with "
                            + "`<action android:name=\"android.media.browse.MediaBrowserService\" />` "
                            + "to the service that extends `android.service.media.MediaBrowserService`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.MANIFEST_AND_JAVA_FILES
                    )
            ).setAndroidSpecific(true);

    private final Map<String, ServiceDeclaration> servicesWithoutFilter = new HashMap<>();

    private static class ServiceDeclaration {
        final XmlContext context;
        final Element element;
        final Location location;

        ServiceDeclaration(XmlContext context, Element element, Location location) {
            this.context = context;
            this.element = element;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        servicesWithoutFilter.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String pkg = context.getProject().getPackage();
        String fqcn = name;
        if (name.startsWith(".")) {
            fqcn = pkg + name;
        } else if (!name.contains(".")) {
            fqcn = pkg + "." + name;
        }

        boolean hasFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    String actionName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                    if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                        hasFilter = true;
                        break;
                    }
                }
            }
            if (hasFilter) {
                break;
            }
        }

        if (!hasFilter) {
            servicesWithoutFilter.put(fqcn, new ServiceDeclaration(context, element, context.getLocation(element)));
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.service.media.MediaBrowserService");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String className = declaration.getQualifiedName();
        if (className == null) {
            return;
        }
        if (servicesWithoutFilter.containsKey(className)) {
            ServiceDeclaration decl = servicesWithoutFilter.get(className);
            if (decl != null) {
                decl.context.report(
                        ISSUE,
                        decl.element,
                        decl.location,
                        "Missing MediaBrowserService intent-filter"
                );
                servicesWithoutFilter.remove(className);
            }
        }
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression node, PsiMethod method) {
        // No-op to satisfy specification requirement
    }
}