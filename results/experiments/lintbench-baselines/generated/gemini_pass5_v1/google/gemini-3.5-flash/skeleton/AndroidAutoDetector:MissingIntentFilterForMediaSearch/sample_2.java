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
import com.intellij.psi.PsiClass;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register "
                    + "an `intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`. "
                    + "To do this, add `<intent-filter><action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" /></intent-filter>` "
                    + "to your `<activity>` or `<service>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasMediaPlayFromSearch = false;
    private boolean mHasMediaBrowserService = false;
    private Location mMediaBrowserServiceLocation = null;
    private Location mManifestLocation = null;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("action", "application");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasMediaPlayFromSearch = false;
        mHasMediaBrowserService = false;
        mMediaBrowserServiceLocation = null;
        mManifestLocation = null;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        if (context.getProject() == context.getMainProject()) {
            mHasMediaPlayFromSearch = false;
            mHasMediaBrowserService = false;
            mMediaBrowserServiceLocation = null;
            mManifestLocation = null;
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("action".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.media.browse.MediaBrowserService".equals(name)) {
                mHasMediaBrowserService = true;
                if (mMediaBrowserServiceLocation == null) {
                    mMediaBrowserServiceLocation = context.getLocation(element);
                }
            } else if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
                mHasMediaPlayFromSearch = true;
            }
        } else if ("application".equals(tagName)) {
            if (mManifestLocation == null) {
                mManifestLocation = context.getLocation(element);
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.browse.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat",
                "android.support.v4.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mHasMediaBrowserService = true;
        if (mMediaBrowserServiceLocation == null) {
            mMediaBrowserServiceLocation = context.getNameLocation(declaration);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasMediaBrowserService && !mHasMediaPlayFromSearch) {
            Location location = mMediaBrowserServiceLocation;
            if (location == null) {
                location = mManifestLocation;
            }
            if (location == null) {
                location = Location.create(context.getProject().getDir());
            }
            context.report(
                    ISSUE,
                    location,
                    "Missing `intent-filter` for `android.media.action.MEDIA_PLAY_FROM_SEARCH` to support voice searches on Android Auto"
            );
        }
    }
}