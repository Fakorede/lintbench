package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue MISSING_LEANBACK_SUPPORT = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface required by Android TV. "
                    + "Add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to the manifest.\n\n"
                    + "Reference: https://developer.android.com/training/tv/start/start.html#leanback-req",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static class FileData {
        final XmlContext context;
        boolean hasLeanbackLauncher;
        boolean hasLeanbackFeature;

        FileData(XmlContext context) {
            this.context = context;
        }
    }

    private final Map<File, FileData> mFileData = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getTagName();
        FileData data = mFileData.get(context.getFile());
        if (data == null) {
            data = new FileData(context);
            mFileData.put(context.getFile(), data);
        }

        if (SdkConstants.TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (LEANBACK_FEATURE.equals(name)) {
                data.hasLeanbackFeature = true;
            }
        } else if (SdkConstants.TAG_CATEGORY.equals(tag)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                data.hasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        for (FileData data : mFileData.values()) {
            if (data.hasLeanbackLauncher && !data.hasLeanbackFeature) {
                Element root = data.context.document.getDocumentElement();
                data.context.report(
                        MISSING_LEANBACK_SUPPORT,
                        data.context.getLocation(root),
                        "You should add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to the manifest for Android TV support."
                );
            }
        }
    }
}