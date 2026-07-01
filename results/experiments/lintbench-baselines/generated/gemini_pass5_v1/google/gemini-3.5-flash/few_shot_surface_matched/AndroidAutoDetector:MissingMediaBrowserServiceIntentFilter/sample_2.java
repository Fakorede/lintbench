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
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `intent-filter` "
                            + "for the action `android.media.browse.MediaBrowserService` to be able "
                            + "to browse and play media.\n\n"
                            + "To do this, add\n"
                            + "`<intent-filter>`\n"
                            + "    `<action android:name=\"android.media.browse.MediaBrowserService\" />`\n"
                            + "`</intent-filter>`\n"
                            + "to the service that extends `android.service.media.MediaBrowserService`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)))
                    .setAndroidSpecific(true);

    private final Set<String> servicesWithFilter = new HashSet<>();
    private final Set<String> servicesWithoutFilter = new HashSet<>();
    private final Map<String, Element> serviceElements = new HashMap<>();
    private final List<String> mediaBrowserServices = new ArrayList<>();
    private final Map<String, Location> classLocations = new HashMap<>();
    private final Map<String, Location> elementLocations = new HashMap<>();

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public boolean appliesTo(Scope scope) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        servicesWithFilter.clear();
        servicesWithoutFilter.clear();
        serviceElements.clear();
        mediaBrowserServices.clear();
        classLocations.clear();
        elementLocations.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("service".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name == null || name.isEmpty()) {
                name = element.getAttribute("android:name");
            }
            if (name == null || name.isEmpty()) {
                return;
            }
            String pkg = context.getProject().getPackage();
            String fqName = name;
            if (name.startsWith(".")) {
                fqName = pkg != null ? pkg + name : name;
            } else if (!name.contains(".")) {
                fqName = pkg != null ? pkg + "." + name : name;
            }

            serviceElements.put(fqName, element);
            elementLocations.put(fqName, context.getLocation(element));

            boolean hasFilter = false;
            NodeList intentFilters = element.getElementsByTagName("intent-filter");
            for (int i = 0; i < intentFilters.getLength(); i++) {
                Element filter = (Element) intentFilters.item(i);
                NodeList actions = filter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    String actionName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                    if (actionName == null || actionName.isEmpty()) {
                        actionName = action.getAttribute("android:name");
                    }
                    if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                        hasFilter = true;
                        break;
                    }
                }
                if (hasFilter) {
                    break;
                }
            }

            if (hasFilter) {
                servicesWithFilter.add(fqName);
            } else {
                servicesWithoutFilter.add(fqName);
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.service.media.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        if ("android.service.media.MediaBrowserService".equals(qualifiedName)
                || "androidx.media.MediaBrowserServiceCompat".equals(qualifiedName)) {
            return;
        }
        mediaBrowserServices.add(qualifiedName);
        classLocations.put(qualifiedName, context.getNameLocation(declaration));
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression node, PsiMethod method) {
    }

    public void visitMethod(JavaContext context, UMethod method) {
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (String service : mediaBrowserServices) {
            if (!servicesWithFilter.contains(service)) {
                Location location = elementLocations.get(service);
                if (location == null) {
                    location = classLocations.get(service);
                }
                if (location != null) {
                    context.report(
                            ISSUE,
                            location,
                            "Missing MediaBrowserService intent-filter"
                    );
                }
            }
        }
    }
}