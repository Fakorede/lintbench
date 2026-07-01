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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_SESSION_COMPAT =
            "android.support.v4.media.session.MediaSessionCompat";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.app.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";

    static final String MISSING_MEDIA_PLAY_FROM_SEARCH = "MissingIntentFilterForMediaSearch";

    public static final Issue ISSUE =
            Issue.create(
                            MISSING_MEDIA_PLAY_FROM_SEARCH,
                            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                            "To support voice searches on Android Auto, you should also register an "
                                    + "`intent-filter` for the action "
                                    + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n"
                                    + "\n"
                                    + "To do this, add\n"
                                    + "```xml\n"
                                    + "`<intent-filter>`\n"
                                    + "    `<action android:name=\"android.media.action"
                                    + ".MEDIA_PLAY_FROM_SEARCH\" />`\n"
                                    + "`</intent-filter>`\n"
                                    + "```\n"
                                    + "to your `<activity>` or `<service>`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            new Implementation(
                                    AndroidAutoDetector.class,
                                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html"
                                    + "#support_voice");

    /** Whether the manifest already contains the MEDIA_PLAY_FROM_SEARCH intent-filter */
    private boolean mHasMediaPlayFromSearchIntentFilter = false;

    /** Location of the media session onPlay callback for error reporting */
    private Location mMediaSessionOnPlayLocation = null;

    /** The class that has the onPlay method */
    private String mMediaSessionClassName = null;

    public AndroidAutoDetector() {}

    // ---- XmlScanner ----

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.Context context,
            @NonNull java.io.File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasMediaPlayFromSearchIntentFilter = false;
        mMediaSessionOnPlayLocation = null;
        mMediaSessionClassName = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
            mHasMediaPlayFromSearchIntentFilter = true;
        }
    }

    // ---- SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_SESSION_COMPAT, MEDIA_BROWSER_SERVICE_COMPAT,
                MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // We check for the onPlay method inside this class
        for (PsiMethod method : declaration.getMethods()) {
            if ("onPlay".equals(method.getName())) {
                if (mMediaSessionOnPlayLocation == null) {
                    mMediaSessionOnPlayLocation = context.getNameLocation(declaration);
                    mMediaSessionClassName = declaration.getQualifiedName();
                }
                break;
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("onPlay");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull org.jetbrains.uast.UCallExpression call) {
        // Not used in this detector
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mHasMediaPlayFromSearchIntentFilter && mMediaSessionOnPlayLocation != null) {
            context.report(
                    ISSUE,
                    mMediaSessionOnPlayLocation,
                    "To support voice searches on Android Auto, register an "
                            + "`intent-filter` for the action "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
        }
    }
}