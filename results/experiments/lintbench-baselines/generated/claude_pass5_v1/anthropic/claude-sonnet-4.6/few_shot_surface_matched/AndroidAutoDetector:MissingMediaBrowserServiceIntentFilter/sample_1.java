package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_EXPORTED = "android:exported";

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
                            new Implementation(
                                    AndroidAutoDetector.class,
                                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#config_manifest")
                    .setAndroidSpecific(true);

    // Map from service class name (simple or qualified) to the XML element location
    // for services declared in the manifest
    private final Map<String, Element> mServiceElements = new HashMap<>();

    // Map from service class name to whether it has the media browser intent filter
    private final Map<String, Boolean> mServiceHasIntentFilter = new HashMap<>();

    // Map from qualified class name found in source -> location for reporting
    private final Map<String, Location> mMediaBrowserServiceClasses = new HashMap<>();

    // The current XmlContext for the manifest
    private XmlContext mXmlContext;

    public AndroidAutoDetector() {}

    // ---- Implements Detector ----

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.Context context,
            @NonNull com.android.tools.lint.detector.api.Project project) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mServiceElements.clear();
        mServiceHasIntentFilter.clear();
        mMediaBrowserServiceClasses.clear();
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mXmlContext = context;
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Normalize name: strip leading dot or package prefix issues
        String serviceName = name;

        boolean hasMediaBrowserIntentFilter = false;
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

        mServiceElements.put(serviceName, element);
        mServiceHasIntentFilter.put(serviceName, hasMediaBrowserIntentFilter);

        // Also store with simple class name if fully qualified
        if (serviceName.contains(".")) {
            String simpleName = serviceName.substring(serviceName.lastIndexOf('.') + 1);
            mServiceElements.put(simpleName, element);
            mServiceHasIntentFilter.put(simpleName, hasMediaBrowserIntentFilter);
        }
    }

    // ---- Implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        if (MEDIA_BROWSER_SERVICE_CLASS.equals(qualifiedName)) {
            return;
        }

        // Record this class as a MediaBrowserService subclass
        Location location = context.getNameLocation(declaration);
        mMediaBrowserServiceClasses.put(qualifiedName, location);

        // Check if this class is referenced in the manifest with the proper intent filter
        checkServiceHasIntentFilter(context, qualifiedName, location);
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull org.jetbrains.uast.UCallExpression node,
            @NonNull PsiMethod method) {
        // Not used for primary logic, but required by specification
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    private void checkServiceHasIntentFilter(
            @NonNull JavaContext context,
            @NonNull String qualifiedName,
            @NonNull Location location) {

        // Try to find the service in the manifest by qualified name or simple name
        Boolean hasFilter = mServiceHasIntentFilter.get(qualifiedName);

        if (hasFilter == null) {
            // Try simple name
            String simpleName = qualifiedName.contains(".")
                    ? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
                    : qualifiedName;
            hasFilter = mServiceHasIntentFilter.get(simpleName);
        }

        if (hasFilter == null) {
            // Service not found in manifest at all - could report, but spec focuses on
            // missing intent-filter for declared services
            // We report when the service is declared but missing the filter
            return;
        }

        if (!hasFilter) {
            context.report(
                    MISSING_MEDIA_BROWSER_SERVICE_ACTION_FILTER,
                    location,
                    "This `MediaBrowserService` does not have an `intent-filter` for action "
                            + "`android.media.browse.MediaBrowserService`");
        }
    }
}