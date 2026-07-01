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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an "
                            + "`<intent-filter>` for the action "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH` and add it to an "
                            + "`<activity>` or `<service>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.MANIFEST_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String NODE_ACTION = "action";
    private static final String NODE_ACTIVITY = "activity";
    private static final String NODE_APPLICATION = "application";
    private static final String NODE_INTENT_FILTER = "intent-filter";
    private static final String NODE_SERVICE = "service";

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT_ANDROIDX =
            "androidx.media.MediaBrowserServiceCompat";

    private boolean mHasMediaBrowserService;
    private boolean mHasMediaSearchIntentFilter;
    private Location mApplicationLocation;
    private final List<String> mMediaBrowserServiceClassNames = new ArrayList<>();
    private final Map<String, Location> mComponentLocations = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        return "AndroidManifest.xml".equals(name)
                || name.endsWith(".java")
                || name.endsWith(".kt");
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION, NODE_SERVICE, NODE_ACTIVITY);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasMediaBrowserService = false;
        mHasMediaSearchIntentFilter = false;
        mApplicationLocation = null;
        mMediaBrowserServiceClassNames.clear();
        mComponentLocations.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (NODE_APPLICATION.equals(tag)) {
            mApplicationLocation = context.getLocation(element);
            return;
        }

        String className = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (className == null || className.isEmpty()) {
            className = element.getAttribute(ATTR_NAME);
        }
        if (className == null || className.isEmpty()) {
            return;
        }

        String fqcn = resolveClassName(context, className);
        mComponentLocations.put(fqcn, context.getLocation(element));

        if (hasMediaPlayFromSearchIntentFilter(element)) {
            mHasMediaSearchIntentFilter = true;
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_BROWSER_SERVICE,
                MEDIA_BROWSER_SERVICE_COMPAT,
                MEDIA_BROWSER_SERVICE_COMPAT_ANDROIDX);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        mHasMediaBrowserService = true;
        mMediaBrowserServiceClassNames.add(qualifiedName);
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // Not needed for this check.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mHasMediaBrowserService || mHasMediaSearchIntentFilter) {
            return;
        }

        Location location = mApplicationLocation;
        for (String className : mMediaBrowserServiceClassNames) {
            Location componentLocation = mComponentLocations.get(className);
            if (componentLocation != null) {
                location = componentLocation;
                break;
            }
        }

        if (location != null) {
            context.report(
                    ISSUE,
                    location,
                    "To support voice searches on Android Auto, add an `<intent-filter>` with "
                            + "`<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />` "
                            + "to an `<activity>` or `<service>`.");
        }
    }

    private static boolean hasMediaPlayFromSearchIntentFilter(@NonNull Element element) {
        NodeList intentFilters = element.getElementsByTagName(NODE_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList actions = intentFilter.getElementsByTagName(NODE_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (actionName == null || actionName.isEmpty()) {
                    actionName = action.getAttribute(ATTR_NAME);
                }
                if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String resolveClassName(@NonNull XmlContext context, @NonNull String className) {
        String packageName = context.getMainProject().getPackage();
        if (packageName == null || packageName.isEmpty()) {
            return className;
        }
        if (className.startsWith(".")) {
            return packageName + className;
        }
        if (!className.contains(".")) {
            return packageName + "." + className;
        }
        return className;
    }
}