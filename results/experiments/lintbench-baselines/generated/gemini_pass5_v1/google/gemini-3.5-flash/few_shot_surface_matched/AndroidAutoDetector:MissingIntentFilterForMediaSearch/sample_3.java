package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an "
                            + "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`. "
                            + "To do this, add `<intent-filter><action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" /></intent-filter>` "
                            + "to your `<activity>` or `<service>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            java.util.EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)));

    private boolean mHasMediaSearchIntentFilter = false;
    private final java.util.List<Location> mServiceLocations = new java.util.ArrayList<>();

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return false;
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
        mHasMediaSearchIntentFilter = false;
        mServiceLocations.clear();
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        if ("action".equals(element.getTagName())) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
                mHasMediaSearchIntentFilter = true;
            }
        }
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.List<String> applicableSuperClasses() {
        java.util.List<String> supers = new java.util.ArrayList<>();
        supers.add("android.media.browse.MediaBrowserService");
        supers.add("android.support.v4.media.MediaBrowserServiceCompat");
        supers.add("androidx.media.MediaBrowserServiceCompat");
        return supers;
    }

    @Override
    public void visitClass(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UClass declaration) {
        mServiceLocations.add(context.getNameLocation(declaration));
    }

    @Override
    public void visitMethod(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UMethod method) {
        // Required override
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        if (!mHasMediaSearchIntentFilter && !mServiceLocations.isEmpty()) {
            for (Location location : mServiceLocations) {
                context.report(
                        ISSUE,
                        location,
                        "Missing `MEDIA_PLAY_FROM_SEARCH` intent-filter");
            }
        }
    }
}