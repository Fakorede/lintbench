public void testArraySizesIncremental() throws Exception {
        String expected = ""
                + "res/values-es/arrays.xml:3: Warning: Array size_test has an inconsistent number of items (3 in values-es, but 4 in values) [InconsistentArrays]\n"
                + "    <string-array name=\"size_test\">\n"
                + "    ^\n"
                + "0 errors, 1 warnings\n";

        //noinspection all
        assertEquals(expected, lintProjectIncrementally(
                "res/values-es/arrays.xml",
                "res/values/arrays.xml" => "...",
                "res/values-es/arrays.xml" => "..."
        ));
    }