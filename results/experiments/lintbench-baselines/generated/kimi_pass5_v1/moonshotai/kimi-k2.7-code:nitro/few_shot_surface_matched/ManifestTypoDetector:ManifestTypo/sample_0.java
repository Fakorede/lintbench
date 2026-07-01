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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Manifest Typo",
                    "A typo in an AndroidManifest.xml tag can cause the element to be ignored, "
                            + "leading to subtle runtime bugs. Ensure manifest tags are spelled "
                            + "exactly as documented.",
                    Category.CORRECTNESS,
                    8,
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
                            "uses-sdk",
                            "uses-permission",
                            "uses-permission-sdk-23",
                            "permission",
                            "permission-group",
                            "permission-tree",
                            "uses-feature",
                            "uses-library",
                            "uses-configuration",
                            "supports-screens",
                            "compatible-screens",
                            "supports-gl-texture",
                            "meta-data",
                            "instrumentation",
                            "queries",
                            "package",
                            "provider",
                            "intent"));

    private static final Map<String, String> KNOWN_TYPOS = new HashMap<>();

    static {
        KNOWN_TYPOS.put("reciever", "receiver");
        KNOWN_TYPOS.put("reciver", "receiver");
        KNOWN_TYPOS.put("activitiy", "activity");
        KNOWN_TYPOS.put("activty", "activity");
        KNOWN_TYPOS.put("servcie", "service");
        KNOWN_TYPOS.put("servce", "service");
        KNOWN_TYPOS.put("aplication", "application");
        KNOWN_TYPOS.put("aplicaton", "application");
        KNOWN_TYPOS.put("permision", "permission");
        KNOWN_TYPOS.put("use-permission", "uses-permission");
        KNOWN_TYPOS.put("uses-permision", "uses-permission");
        KNOWN_TYPOS.put("uses-permission-sdk-23", "uses-permission-sdk-23");
        KNOWN_TYPOS.put("uses-permision-sdk-23", "uses-permission-sdk-23");
        KNOWN_TYPOS.put("mete-data", "meta-data");
        KNOWN_TYPOS.put("metadata", "meta-data");
        KNOWN_TYPOS.put("intentfilter", "intent-filter");
        KNOWN_TYPOS.put("intent-fiter", "intent-filter");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String suggestion = KNOWN_TYPOS.get(tag);
        if (suggestion == null) {
            suggestion = findClosestValidTag(tag);
        }

        if (suggestion != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Possible typo: \""
                            + tag
                            + "\" looks like the manifest tag \""
                            + suggestion
                            + "\"");
        }
    }

    private static String findClosestValidTag(String tag) {
        if (tag.length() < 4) {
            return null;
        }

        int bestDistance = Integer.MAX_VALUE;
        String bestMatch = null;
        for (String valid : VALID_TAGS) {
            int distance = levenshteinDistance(tag, valid);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = valid;
            }
        }

        int threshold = tag.length() <= 6 ? 1 : 2;
        return bestDistance <= threshold ? bestMatch : null;
    }

    private static int levenshteinDistance(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            current[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                int deletion = previous[j] + 1;
                int insertion = current[j - 1] + 1;
                int substitution = previous[j - 1] + cost;
                current[j] = Math.min(Math.min(deletion, insertion), substitution);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[m];
    }
}