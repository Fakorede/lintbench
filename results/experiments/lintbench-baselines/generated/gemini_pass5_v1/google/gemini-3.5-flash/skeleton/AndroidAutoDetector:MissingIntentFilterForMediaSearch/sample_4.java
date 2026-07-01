package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an "
                            + "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`. "
                            + "To do this, add `<intent-filter> <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" /> </intent-filter>` "
                            + "to your `<activity>` or `<service>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasMediaSearchIntentFilter = false;
    private final List<Location> mMediaBrowserServiceLocations = new ArrayList<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasMediaSearchIntentFilter = false;
        mMediaBrowserServiceLocations.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("action".equals(element.getTagName())) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
                mHasMediaSearchIntentFilter = true;
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.browse.MediaBrowserService",
                "android.support.v4.media.MediaBrowserServiceCompat",
                "androidx.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mMediaBrowserServiceLocations.add(context.getNameLocation(declaration));
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        // Not used
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mHasMediaSearchIntentFilter && !mMediaBrowserServiceLocations.isEmpty()) {
            for (Location location : mMediaBrowserServiceLocations) {
                context.report(
                        ISSUE,
                        location,
                        "Missing `intent-filter` for `android.media.action.MEDIA_PLAY_FROM_SEARCH` to support voice searches on Android Auto"
                );
            }
        }
    }
}