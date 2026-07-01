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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_EXPORTED = "android:exported";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE));

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER =
            Issue.create(
                            "MissingMediaBrowserServiceIntentFilter",
                            "Missing MediaBrowserService intent-filter",
                            "An Automotive Media App requires an exported service that extends "
                                    + "`android.service.media.MediaBrowserService` with an "
                                    + "`intent-filter` for the action "
                                    + "`android.media.browse.MediaBrowserService` to be able to browse "
                                    + "and play media.\n\n"
                                    + "To do this, add\n"
                                    + "```xml\n"
                                    + "<intent-filter>\n"
                                    + "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n"
                                    + "</intent-filter>\n"
                                    + "```\n"
                                    + "to the service that extends "
                                    + "`android.service.media.MediaBrowserService`",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#config_manifest");

    // Set of service class names (qualified) found in Java/Kotlin source that extend
    // MediaBrowserService.
    private final java.util.Set<String> mMediaBrowserServiceClasses =
            new java.util.HashSet<>();

    // Set of service class names found in the manifest that have the required intent-filter.
    private final java.util.Set<String> mServicesWithIntentFilter =
            new java.util.HashSet<>();

    // Map from service class name to the XmlContext+Element for reporting issues later.
    private final java.util.Map<String, XmlContext> mServiceContexts =
            new java.util.HashMap<>();
    private final java.util.Map<String, Element> mServiceElements =
            new java.util.HashMap<>();

    // Track classes that were visited (qualified names) with their UClass for reporting.
    private final java.util.Map<String, UClass> mVisitedClasses =
            new java.util.HashMap<>();
    private final java.util.Map<String, JavaContext> mVisitedClassContexts =
            new java.util.HashMap<>();

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return false;
    }

    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.XmlContext context) {
        return true;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String serviceName = element.getAttribute(ATTR_NAME);
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        // Resolve the full qualified name if it starts with a dot or has no package.
        String packageName = context.getProject().getPackage();
        if (packageName != null && serviceName.startsWith(".")) {
            serviceName = packageName + serviceName;
        } else if (packageName != null && !serviceName.contains(".")) {
            serviceName = packageName + "." + serviceName;
        }

        // Check if this service has the required intent-filter.
        boolean hasMediaBrowserIntentFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName(TAG_ACTION);
                for (int j = 0; j < actions.getLength(); j++) {
                    Node actionNode = actions.item(j);
                    if (actionNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element action = (Element) actionNode;
                        String actionName = action.getAttribute(ATTR_NAME);
                        if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                            hasMediaBrowserIntentFilter = true;
                            break;
                        }
                    }
                }
            }
            if (hasMediaBrowserIntentFilter) {
                break;
            }
        }

        if (hasMediaBrowserIntentFilter) {
            mServicesWithIntentFilter.add(serviceName);
        } else {
            mServiceContexts.put(serviceName, context);
            mServiceElements.put(serviceName, element);
        }
    }

    // ---- SourceCodeScanner ----

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null || MEDIA_BROWSER_SERVICE.equals(qualifiedName)) {
            return;
        }
        mMediaBrowserServiceClasses.add(qualifiedName);
        mVisitedClasses.put(qualifiedName, declaration);
        mVisitedClassContexts.put(qualifiedName, context);
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull org.jetbrains.uast.UCallExpression node,
            @NonNull PsiMethod method) {
        // Not used for this check.
    }

    // ---- Lifecycle ----

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServiceClasses.clear();
        mServicesWithIntentFilter.clear();
        mServiceContexts.clear();
        mServiceElements.clear();
        mVisitedClasses.clear();
        mVisitedClassContexts.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // For each class that extends MediaBrowserService, check if it appears in the manifest
        // with the required intent-filter.
        for (String className : mMediaBrowserServiceClasses) {
            if (!mServicesWithIntentFilter.contains(className)) {
                // Report the issue. Prefer to report on the manifest element if available,
                // otherwise report on the class declaration.
                if (mServiceElements.containsKey(className)) {
                    XmlContext xmlContext = mServiceContexts.get(className);
                    Element element = mServiceElements.get(className);
                    if (xmlContext != null && element != null) {
                        xmlContext.report(
                                MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER,
                                element,
                                xmlContext.getNameLocation(element),
                                "This service does not have an `intent-filter` for action "
                                        + "`android.media.browse.MediaBrowserService`");
                    }
                } else {
                    // The service might not be declared in the manifest at all,
                    // or we couldn't match it. Report on the class if we have it.
                    JavaContext javaContext = mVisitedClassContexts.get(className);
                    UClass uClass = mVisitedClasses.get(className);
                    if (javaContext != null && uClass != null) {
                        javaContext.report(
                                MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER,
                                uClass,
                                javaContext.getNameLocation(uClass),
                                "This `MediaBrowserService` subclass is not registered in the "
                                        + "manifest with an `intent-filter` for action "
                                        + "`android.media.browse.MediaBrowserService`");
                    }
                }
            }
        }
    }
}