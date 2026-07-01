package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Manifest Tag Typo",
                    "This tag does not match any known Android manifest tag, but is very similar"
                            + " to one. This is likely a typo and will cause the tag to be"
                            + " ignored.",
                    Category.CORRECTNESS,
                    10,
                    Severity.FATAL,
                    new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> VALID_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "manifest",
                            "application",
                            "activity",
                            "activity-alias",
                            "service",
                            "receiver",
                            "provider",
                            "intent-filter",
                            "action",
                            "category",
                            "data",
                            "meta-data",
                            "uses-permission",
                            "uses-permission-sdk-23",
                            "permission",
                            "permission-tree",
                            "permission-group",
                            "uses-sdk",
                            "uses-feature",
                            "uses-library",
                            "supports-screens",
                            "compatible-screens",
                            "supports-gl-texture",
                            "instrumentation",
                            "grant-uri-permission",
                            "path-permission",
                            "profileable",
                            "queries",
                            "package",
                            "intent"));

    private static final int MAX_DISTANCE_SHORT = 1;
    private static final int MAX_DISTANCE_LONG = 2;
    private static final int LONG_TAG_THRESHOLD = 5;

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (tagName == null) {
            return;
        }

        String localName = tagName;
        int colonIndex = localName.indexOf(':');
        if (colonIndex != -1) {
            localName = localName.substring(colonIndex + 1);
        }

        if (localName.isEmpty() || VALID_TAGS.contains(localName)) {
            return;
        }

        String closest = null;
        int minDistance = Integer.MAX_VALUE;
        for (String valid : VALID_TAGS) {
            int distance = editDistance(localName, valid);
            if (distance < minDistance) {
                minDistance = distance;
                closest = valid;
            }
        }

        int threshold =
                localName.length() >= LONG_TAG_THRESHOLD
                        ? MAX_DISTANCE_LONG
                        : MAX_DISTANCE_SHORT;
        if (minDistance <= threshold && closest != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Possible typo in manifest tag: '<"
                            + localName
                            + ">' is not a recognized tag. Did you mean '<"
                            + closest
                            + ">'?");
        }
    }

    private static int editDistance(String a, String b) {
        int m = a.length();
        int n = b.length();
        if (m == 0) {
            return n;
        }
        if (n == 0) {
            return m;
        }

        int[] prev = new int[n + 1];
        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            int[] curr = new int[n + 1];
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] =
                        Math.min(
                                Math.min(curr[j - 1] + 1, prev[j] + 1),
                                prev[j - 1] + cost);
            }
            prev = curr;
        }

        return prev[n];
    }
}