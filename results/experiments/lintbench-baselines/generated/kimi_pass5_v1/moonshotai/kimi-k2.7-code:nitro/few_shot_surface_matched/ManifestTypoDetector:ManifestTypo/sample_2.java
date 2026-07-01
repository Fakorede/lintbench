package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typo in Manifest Tag",
                    "This check looks through the manifest, and if it finds any tags that look "
                            + "like likely misspellings of valid Android manifest tags, they are "
                            + "flagged. Misspelled tags are ignored by the framework and can lead "
                            + "to subtle bugs such as missing permissions, unexported components, "
                            + "or components with the wrong configuration.",
                    Category.CORRECTNESS,
                    10,
                    Severity.FATAL,
                    new Implementation(
                            ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final List<String> KNOWN_TAGS = Arrays.asList(
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
            "uses-permission",
            "permission",
            "permission-group",
            "permission-tree",
            "uses-sdk",
            "uses-feature",
            "uses-library",
            "supports-screens",
            "compatible-screens",
            "supports-gl-texture",
            "instrumentation",
            "meta-data",
            "layout",
            "grant-uri-permission",
            "path-permission",
            "profileable",
            "queries",
            "package"
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (KNOWN_TAGS.contains(tag)) {
            return;
        }

        String closest = findClosestKnownTag(tag);
        if (closest != null) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Manifest tag `<" + tag + ">` looks like a typo; did you mean `<"
                            + closest + ">`?");
        }
    }

    private static String findClosestKnownTag(String tag) {
        String closest = null;
        int minDistance = Integer.MAX_VALUE;
        for (String known : KNOWN_TAGS) {
            int distance = levenshteinDistance(tag, known);
            if (distance < minDistance) {
                minDistance = distance;
                closest = known;
            }
        }

        int threshold = Math.max(1, Math.min(3, tag.length() / 3));
        return minDistance <= threshold ? closest : null;
    }

    private static int levenshteinDistance(String s, String t) {
        int m = s.length();
        int n = t.length();
        if (m == 0) {
            return n;
        }
        if (n == 0) {
            return m;
        }

        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char sc = s.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = sc == t.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[n];
    }
}