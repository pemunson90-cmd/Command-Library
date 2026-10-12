# Cancellable HTTP engine candidate

Checked2026-10-12. The reviewed URLConnection draft is rejected for production binding: hard500ms connection/header cap cuts healthy slowmobile/TLS functionality, and timeout/read assumptions do not establish correct cancellation. Its original tested branch remains preserved.

Candidate OkHttp5.5.0 with Okio3.18.1, exact published Maven metadata/POM/module/source artifacts inspected over configuredTLSproxy. com.squareup.okhttp3:okhttp advertises Android andJVM variants; actual Android compatibility still requires hostedcompile/nativeproof. Existing Gradle/AGP/Kotlin tuple stays unchanged. okhttp-jvm POM specifies Apache2.0 and kotlinstdlib2.1.21/okio-jvm3.18.1; current Kotlin2.2.10 binding is candidate untilcompile.

SourceURLs: https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml ; https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/5.5.0/okhttp-5.5.0.module ; https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-jvm/5.5.0/okhttp-jvm-5.5.0.pom ; https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-jvm/5.5.0/okhttp-jvm-5.5.0-sources.jar .

okhttp-jvm JAR961167bytes SHA256234194a04aac54858df0a750d243af7b2e39df5e4eb86e9912043ad33a9f9a52; POM1866bytes SHAe72ea7261789a91b5a60920c8fd2ee860d0584765aadde249548c2ed1dba4631. Maven .jar.sha256 absent(404); computedidentity is not a publishedsignature. Public overview/Dokka URLs also returned404; APIknowledge here comes from actualversioned sourcejar, not assumedwebsiteavailability.

ActualCall source exposes enqueue/cancel; RealCall.cancel closes established/in-progress transport socket. Builder.callTimeout spans DNS/connect/write/server/read; connect/read/write timeoutvalues are configurable, not hard500ms. Disable followRedirects/followSslRedirects/retryOnConnectionFailure/cache/cookies/authenticator; explicit Accept-Encoding identity preventstransparentgzip. Adapterowns manualsafe retry/redirect/originpolicy and closesbodies/cancelscall on structuredcoroutine/token/clientclose. Do not discardvalid Retry-After >24h: keep cooldownorreturnwithoutretry, neverearlyretry.

Required verification: synthetic delayedheaders>500ms succeed with configuredlongerbudget, blockedheader/body cancel, DNS/connect/TLS/write boundaries wherefixturepermits, chargedPOSTnoautomaticretry, longRetry-After, rawgzip/hash/Range, redirects/credentials isolation. Hosted Android/TLS tests required beforeacceptance; JVMproof alone is scoped.
