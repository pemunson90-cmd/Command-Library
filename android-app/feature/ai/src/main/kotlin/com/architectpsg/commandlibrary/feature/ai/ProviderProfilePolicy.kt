package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import java.net.URI
import java.util.Locale
import kotlin.time.Duration.Companion.days

/** Initial retained HTTPS subset. Admission says nothing about network or model eligibility. */
public object ProviderProfilePolicy {
    public fun canonicalOrigin(baseUri: ResourceUri): Outcome<Origin> = profileOutcome {
        canonical(baseUri).second
    }

    public fun normalize(profile: ProviderProfile): Outcome<ProviderProfile> = profileOutcome {
        profileText(profile.id.value, 256)
        profileText(profile.name, 1024)
        profileText(profile.selectedModelId, 1024)
        val endpoint = profile.endpoint ?: profileReject("Provider endpoint is required")
        profileText(endpoint.id.value, 256)
        profileRequire(endpoint.id == profile.id, "Endpoint identity must equal profile identity")
        val (base, origin) = canonical(profile.baseUri ?: profileReject("Provider base URI is required"))
        profileRequire(endpoint.origin == origin, "Endpoint origin must equal canonical base origin")
        profileRequire(profile.extraHeaders.isEmpty(), "Retained provider headers are unsupported")
        profileRequire(endpoint.allowedRedirectOrigins.isEmpty(), "Retained provider redirects are unsupported")
        profileRequire(endpoint.requestsPerSecond > 0, "Invalid provider request limit")
        val retry = endpoint.retry
        profileRequire(retry.maxAttempts in 1..10 && retry.initialDelay.isFinite() && retry.maximumDelay.isFinite() &&
            retry.initialDelay >= kotlin.time.Duration.ZERO && retry.maximumDelay >= retry.initialDelay &&
            retry.maximumDelay <= 1.days, "Invalid provider retry settings")
        when (val api = profile.apiVersion) {
            is Knowledge.Known -> profileText(api.value, 1024)
            is Knowledge.Absent -> profileText(api.reason, 1024)
            is Knowledge.Unknown -> {
                profileText(api.reason, 1024)
                profileRequire(api.evidence.isEmpty(), "Retained API evidence is unsupported")
            }
        }
        profileRequire(endpoint.secret == profile.secretRef, "Provider secret references must agree")
        profile.secretRef?.let { ref ->
            profileText(ref.id.value, 256)
            profileRequire(ref.scope == SecretScope(profile.id, origin, "provider-auth-v1"), "Provider secret scope must agree")
        }
        profile.copy(baseUri = base)
    }

    private fun canonical(base: ResourceUri): Pair<ResourceUri, Origin> {
        profileText(base.value, 4096)
        profileRequire('\\' !in base.value && '%' !in base.value, "Invalid provider base URI")
        val uri = try { URI(base.value).parseServerAuthority() } catch (_: Exception) { profileReject("Invalid provider base URI") }
        profileRequire(!uri.isOpaque && uri.scheme.equals("https", true) && uri.host != null &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null, "Invalid provider HTTPS authority")
        val host = uri.host.lowercase(Locale.ROOT)
        // java.net.URI validates IPv6 literals without DNS resolution and retains their brackets,
        // matching the existing network Origin representation. DNS is an explicit ASCII subset.
        if (!host.startsWith("[")) {
            profileRequire(host.length <= 253 && host.split('.').all { label ->
                label.length in 1..63 && label.first().isAsciiAlphaNumeric() && label.last().isAsciiAlphaNumeric() &&
                    label.all { it.isAsciiAlphaNumeric() || it == '-' }
            }, "Unsupported provider host")
        }
        val port = if (uri.port == -1) 443 else uri.port
        profileRequire(port in 1..65535, "Invalid provider port")
        // Reject explicit empty ports and odd authority spellings that URI otherwise accepts.
        val authority = host + if (uri.port == -1) "" else ":${uri.port}"
        profileRequire(uri.rawAuthority.lowercase(Locale.ROOT) == authority, "Invalid provider authority")
        val path = uri.rawPath.orEmpty()
        profileRequire(path.isEmpty() || path.startsWith('/'), "Invalid provider base path")
        profileRequire("//" !in path, "Repeated provider path slash")
        val segments = path.removePrefix("/").removeSuffix("/")
        profileRequire(segments.isEmpty() || segments.split('/').all { segment ->
            segment.isNotEmpty() && segment != "." && segment != ".." &&
                segment.all { it.isAsciiAlphaNumeric() || it in "-._~" }
        }, "Unsupported provider base path")
        val canonicalPath = if (segments.isEmpty()) "/" else "/$segments"
        val canonicalAuthority = host + if (port == 443) "" else ":$port"
        return ResourceUri("https://$canonicalAuthority$canonicalPath") to Origin("https", host, port)
    }

    private fun Char.isAsciiAlphaNumeric(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}
