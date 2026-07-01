package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.EnumSet;
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

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_ACTION_FILTER =
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
                    .setAndroidSpecific(true)
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#config_manifest");

    // Set of service class names found in source that extend MediaBrowserService
    private final java.util.Set<String> mMediaBrowserServiceClasses = new java.util.HashSet<>();

    // Set of service names found in manifest with MediaBrowserService intent-filter
    private final java.util.Set<String> mServicesWithMediaBrowserFilter = new java.util.HashSet<>();

    // Manifest service elements that we need to check
    private final java.util.Map<String, Element> mServiceElements = new java.util.HashMap<>();
    private XmlContext mManifestContext;

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return false;
    }

    public boolean appliesTo(@NonNull Scope scope) {
        return scope == Scope.MANIFEST || scope == Scope.JAVA_FILE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServiceClasses.clear();
        mServicesWithMediaBrowserFilter.clear();
        mServiceElements.clear();
        mManifestContext = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We're visiting <service> elements in the manifest
        String serviceName = element.getAttribute(ATTR_NAME);
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        // Normalize class name (remove leading dot if present)
        String packageName = context.getProject().getPackage();
        if (serviceName.startsWith(".")) {
            if (packageName != null) {
                serviceName = packageName + serviceName;
            }
        } else if (!serviceName.contains(".")) {
            if (packageName != null) {
                serviceName = packageName + "." + serviceName;
            }
        }

        boolean hasMediaBrowserFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getChildNodes();
                for (int j = 0; j < actions.getLength(); j++) {
                    Node actionNode = actions.item(j);
                    if (actionNode.getNodeType() == Node.ELEMENT_NODE
                            && TAG_ACTION.equals(actionNode.getNodeName())) {
                        Element action = (Element) actionNode;
                        String actionName = action.getAttribute(ATTR_NAME);
                        if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                            hasMediaBrowserFilter = true;
                            break;
                        }
                    }
                }
            }
            if (hasMediaBrowserFilter) {
                break;
            }
        }

        if (hasMediaBrowserFilter) {
            mServicesWithMediaBrowserFilter.add(serviceName);
        } else {
            mServiceElements.put(serviceName, element);
            if (mManifestContext == null) {
                mManifestContext = context;
            }
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        if (MEDIA_BROWSER_SERVICE.equals(qualifiedName)) {
            return;
        }
        mMediaBrowserServiceClasses.add(qualifiedName);
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull org.jetbrains.uast.UCallExpression node,
            @NonNull PsiMethod method) {
        // Not used in this detector
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Find MediaBrowserService subclasses that are declared in the manifest
        // without the required intent-filter
        for (String serviceClass : mMediaBrowserServiceClasses) {
            if (!mServicesWithMediaBrowserFilter.contains(serviceClass)) {
                // Check if this class is in the manifest service elements
                Element serviceElement = findServiceElement(serviceClass);
                if (serviceElement != null && mManifestContext != null) {
                    mManifestContext.report(
                            MISSING_MEDIA_BROWSER_SERVICE_ACTION_FILTER,
                            serviceElement,
                            mManifestContext.getNameLocation(serviceElement),
                            "This service does not have an `intent-filter` for action "
                                    + "`android.media.browse.MediaBrowserService`");
                }
            }
        }
    }

    @Nullable
    private Element findServiceElement(String qualifiedName) {
        // Try direct match
        if (mServiceElements.containsKey(qualifiedName)) {
            return mServiceElements.get(qualifiedName);
        }
        // Try simple name match
        String simpleName = qualifiedName;
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleName = qualifiedName.substring(lastDot + 1);
        }
        for (java.util.Map.Entry<String, Element> entry : mServiceElements.entrySet()) {
            String key = entry.getKey();
            if (key.equals(simpleName) || key.endsWith("." + simpleName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}