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
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an "
                            + "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH` "
                            + "in your `<activity>` or `<service>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasMediaService = false;
    private boolean mHasSearchIntentFilter = false;
    private Location mMediaServiceLocation = null;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("action", "service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasMediaService = false;
        mHasSearchIntentFilter = false;
        mMediaServiceLocation = null;
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mHasMediaService = false;
        mHasSearchIntentFilter = false;
        mMediaServiceLocation = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("action".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                name = element.getAttribute("android:name");
            }
            if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
                mHasSearchIntentFilter = true;
            } else if ("android.media.browse.MediaBrowserService".equals(name)) {
                mHasMediaService = true;
                if (mMediaServiceLocation == null) {
                    mMediaServiceLocation = context.getLocation(element);
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.service.media.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat",
                "android.support.v4.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mHasMediaService = true;
        if (mMediaServiceLocation == null) {
            mMediaServiceLocation = context.getNameLocation(declaration);
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mHasMediaService && !mHasSearchIntentFilter) {
            Location location = mMediaServiceLocation;
            if (location == null) {
                location = Location.create(context.getProject().getDir());
            }
            context.report(
                    ISSUE,
                    location,
                    "Missing `android.media.action.MEDIA_PLAY_FROM_SEARCH` intent-filter to support voice searches on Android Auto."
            );
        }
    }

    public void visitMethod() {
        // Not used
    }
}