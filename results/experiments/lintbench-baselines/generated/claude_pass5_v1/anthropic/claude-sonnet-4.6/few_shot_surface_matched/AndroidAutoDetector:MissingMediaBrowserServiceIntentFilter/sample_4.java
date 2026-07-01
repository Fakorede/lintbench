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
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
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
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#config_manifest");

    // Tracks whether we found a service in the manifest with the required intent-filter
    private boolean mFoundMediaBrowserServiceAction = false;

    // Tracks whether we found a class extending MediaBrowserService in Java sources
    private boolean mFoundMediaBrowserServiceClass = false;

    // Location of the MediaBrowserService subclass for reporting
    private Location mMediaBrowserServiceClassLocation = null;

    // Context for reporting
    private JavaContext mJavaContext = null;

    // UClass of the MediaBrowserService subclass
    private UClass mMediaBrowserServiceClass = null;

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return false;
    }

    public boolean appliesTo(@NonNull Context context, @NonNull java.io.File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFoundMediaBrowserServiceAction = false;
        mFoundMediaBrowserServiceClass = false;
        mMediaBrowserServiceClassLocation = null;
        mJavaContext = null;
        mMediaBrowserServiceClass = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        // Check if this service has the MediaBrowserService intent-filter action
        if (hasMediaBrowserServiceIntentFilter(element)) {
            mFoundMediaBrowserServiceAction = true;
        }
    }

    private boolean hasMediaBrowserServiceIntentFilter(@NonNull Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
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
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (MEDIA_BROWSER_SERVICE_CLASS.equals(qualifiedName)) {
            return;
        }
        mFoundMediaBrowserServiceClass = true;
        mMediaBrowserServiceClass = declaration;
        mJavaContext = context;
        mMediaBrowserServiceClassLocation = context.getNameLocation(declaration);
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod resolvedMethod) {
        // Not used for this detector, but required by the interface specification
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mFoundMediaBrowserServiceClass && !mFoundMediaBrowserServiceAction) {
            if (mJavaContext != null && mMediaBrowserServiceClassLocation != null) {
                mJavaContext.report(
                        MISSING_MEDIA_BROWSER_SERVICE_ACTION_FILTER,
                        mMediaBrowserServiceClass,
                        mMediaBrowserServiceClassLocation,
                        "This class extends `MediaBrowserService` but is not exported with an "
                                + "`intent-filter` for the action "
                                + "`android.media.browse.MediaBrowserService` in the manifest. "
                                + "Add the missing `<intent-filter>` to the service in your "
                                + "AndroidManifest.xml.");
            }
        }
    }
}