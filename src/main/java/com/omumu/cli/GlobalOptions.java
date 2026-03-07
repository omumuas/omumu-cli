package com.omumu.cli;

import picocli.CommandLine.Option;

public class GlobalOptions {

    @Option(names = "--json", description = "Output as JSON (for scripts and AI agents)", scope = picocli.CommandLine.ScopeType.INHERIT)
    boolean json;

    @Option(names = "--verbose", description = "Verbose output", scope = picocli.CommandLine.ScopeType.INHERIT)
    boolean verbose;

    @Option(names = "--site-url", description = "Override site URL", defaultValue = "${OMUMU_URL}", scope = picocli.CommandLine.ScopeType.INHERIT)
    String siteUrl;

    @Option(names = "--api-key", description = "Override API key", defaultValue = "${OMUMU_API_KEY}", scope = picocli.CommandLine.ScopeType.INHERIT)
    String apiKey;
}
