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
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";
    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_SERVICE = "service";
    private static final String ATTR_NAME = "android:name";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE));

    public static final Issue MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH =
            Issue.create(
                            "MissingIntentFilterForMediaSearch",
                            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                            "To support voice searches on Android Auto, you should also register an "
                                    + "`intent-filter` for the action "
                                    + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n"
                                    + "\n"
                                    + "To do this, add\n"
                                    + "```xml\n"
                                    + "`<intent-filter>`\n"
                                    + "    `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />`\n"
                                    + "`</intent-filter>`\n"
                                    + "```\n"
                                    + "to your `<activity>` or `<service>`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#support_voice");

    /** Whether the manifest has the MEDIA_PLAY_FROM_SEARCH intent filter. */
    private boolean mHasMediaPlayFromSearchIntentFilter = false;

    /** Whether we found a media browser service class. */
    private boolean mHasMediaBrowserServiceClass = false;

    /** The UClass for reporting, if needed. */
    private UClass mMediaBrowserServiceClass = null;

    /** The JavaContext for reporting, if needed. */
    private JavaContext mJavaContext = null;

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return false;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.Context context,
            @NonNull java.io.File file) {
        return true;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasMediaPlayFromSearchIntentFilter = false;
        mHasMediaBrowserServiceClass = false;
        mMediaBrowserServiceClass = null;
        mJavaContext = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We're visiting <action> elements; check if it has the right android:name
        String name = element.getAttribute(ATTR_NAME);
        if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
            mHasMediaPlayFromSearchIntentFilter = true;
        }
    }

    // ---- SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_BROWSER_SERVICE_COMPAT, MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        // Don't flag the base classes themselves
        if (MEDIA_BROWSER_SERVICE_COMPAT.equals(qualifiedName)
                || MEDIA_BROWSER_SERVICE.equals(qualifiedName)) {
            return;
        }
        mHasMediaBrowserServiceClass = true;
        mMediaBrowserServiceClass = declaration;
        mJavaContext = context;

        checkAndReport();
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod resolvedMethod) {
        // Not used in this detector
    }

    private void checkAndReport() {
        if (mHasMediaBrowserServiceClass && !mHasMediaPlayFromSearchIntentFilter) {
            if (mJavaContext != null && mMediaBrowserServiceClass != null) {
                mJavaContext.report(
                        MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
                        mMediaBrowserServiceClass,
                        mJavaContext.getNameLocation(mMediaBrowserServiceClass),
                        "To support voice searches on Android Auto, register an "
                                + "`intent-filter` for the action "
                                + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        checkAndReport();
    }
}