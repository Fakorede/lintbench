package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP not supported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as"
                            + " lossless encoding and transparency, require Android 4.2.1 (API 18;"
                            + " API 17 is 4.2.0). Avoid using WebP images when minSdkVersion is"
                            + " lower than the required level.",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)));

    private static final int WEBP_LOSSLESS_TRANSPARENT_MIN_SDK = 18;
    private static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";

    private int mMinSdk = Integer.MAX_VALUE;
    private final List<PendingReport> mPendingReports = new ArrayList<>();

    private static class PendingReport {
        final Location location;
        final String message;

        PendingReport(Location location, String message) {
            this.location = location;
            this.message = message;
        }
    }

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(Context context) {
        mMinSdk = context.getMainProject().getMinSdk();
        mPendingReports.clear();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        if (mMinSdk >= WEBP_LOSSLESS_TRANSPARENT_MIN_SDK) {
            mPendingReports.clear();
            return;
        }

        for (PendingReport report : mPendingReports) {
            context.report(ISSUE, report.location, report.message);
        }
        mPendingReports.clear();
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        if (incident.getIssue() != ISSUE) {
            return true;
        }
        return context.getMainProject().getMinSdk() < WEBP_LOSSLESS_TRANSPARENT_MIN_SDK;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        String path = file.getPath();
        return path.endsWith(".xml") || path.endsWith(".java") || path.endsWith(".kt");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "application",
                "activity",
                "activity-alias",
                "service",
                "receiver",
                "provider",
                "bitmap",
                "ImageView");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mMinSdk >= WEBP_LOSSLESS_TRANSPARENT_MIN_SDK) {
            return;
        }

        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String value = attr.getValue();
            if (value != null && value.toLowerCase().endsWith(".webp")) {
                Location location = context.getLocation(attr);
                mPendingReports.add(
                        new PendingReport(
                                location,
                                "WebP images are not supported on devices running API "
                                        + mMinSdk
                                        + "; consider using PNG or raising minSdkVersion"));
            }
        }
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler(context) {
            @Override
            public void visitClass(UClass node) {
                checkName(context.getLocation(node), node.getName());
            }

            @Override
            public void visitMethod(UMethod node) {
                checkName(context.getLocation(node), node.getName());
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                checkName(context.getLocation(node), node.getMethodName());
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                checkName(context.getLocation(node), node.getIdentifier());
            }

            private void checkName(Location location, String name) {
                if (mMinSdk >= WEBP_LOSSLESS_TRANSPARENT_MIN_SDK) {
                    return;
                }
                if (name != null && name.toLowerCase().contains("webp")) {
                    mPendingReports.add(
                            new PendingReport(
                                    location,
                                    "WebP references require minSdkVersion 18 or higher"));
                }
            }
        };
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class,
                UMethod.class,
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }
}