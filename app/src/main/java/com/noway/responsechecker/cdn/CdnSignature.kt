package com.noway.responsechecker.cdn

data class CdnSignature(
    val provider: String,
    val cnameTokens: List<String> = emptyList(),
    val headerNames: List<String> = emptyList(),
    val headerValueTokens: List<String> = emptyList(),
    val serverTokens: List<String> = emptyList()
)

object CdnSignatures {
    val all = listOf(
        CdnSignature(
            "Cloudflare",
            cnameTokens = listOf("cdn.cloudflare.net", "cloudflare.net"),
            headerNames = listOf("cf-ray", "cf-cache-status", "cf-request-id"),
            headerValueTokens = listOf("cloudflare"),
            serverTokens = listOf("cloudflare")
        ),
        CdnSignature(
            "Amazon CloudFront",
            cnameTokens = listOf("cloudfront.net"),
            headerNames = listOf("x-amz-cf-id", "x-amz-cf-pop"),
            headerValueTokens = listOf("cloudfront")
        ),
        CdnSignature(
            "Akamai",
            cnameTokens = listOf("akamaiedge.net", "akamai.net", "edgesuite.net", "edgekey.net", "akamaihd.net"),
            headerNames = listOf("x-akamai-transformed", "akamai-grn"),
            headerValueTokens = listOf("akamai")
        ),
        CdnSignature(
            "Fastly",
            cnameTokens = listOf("fastly.net", "fastlylb.net"),
            headerNames = listOf("fastly-debug-digest", "x-served-by", "x-cache-hits"),
            headerValueTokens = listOf("fastly"),
            serverTokens = listOf("fastly")
        ),
        CdnSignature(
            "Bunny CDN",
            cnameTokens = listOf("b-cdn.net", "bunnycdn.com", "bunny.net"),
            headerNames = listOf("cdn-pullzone", "cdn-uid", "cdn-requestid"),
            headerValueTokens = listOf("bunnycdn", "bunny")
        ),
        CdnSignature(
            "Azure Front Door / CDN",
            cnameTokens = listOf("azurefd.net", "azureedge.net", "trafficmanager.net"),
            headerNames = listOf("x-azure-ref", "x-azure-ref-originshield", "x-msedge-ref"),
            headerValueTokens = listOf("azurefrontdoor", "azure")
        ),
        CdnSignature(
            "Google Cloud CDN",
            cnameTokens = listOf("googlehosted.com", "googleusercontent.com"),
            headerNames = listOf("x-goog-generation", "x-goog-stored-content-length"),
            headerValueTokens = listOf("google frontend", "google")
        ),
        CdnSignature(
            "Imperva",
            cnameTokens = listOf("incapdns.net", "impervadns.net"),
            headerNames = listOf("x-iinfo"),
            headerValueTokens = listOf("imperva", "incapsula")
        ),
        CdnSignature(
            "Sucuri",
            cnameTokens = listOf("sucuri.net", "sucuridns.com"),
            headerNames = listOf("x-sucuri-id", "x-sucuri-cache"),
            headerValueTokens = listOf("sucuri")
        ),
        CdnSignature(
            "Gcore",
            cnameTokens = listOf("gcdn.co", "gcorelabs.com", "gcore.com"),
            headerNames = listOf("x-gcdn-cachestatus", "x-gcdn-node"),
            headerValueTokens = listOf("gcore", "gcdn")
        ),
        CdnSignature(
            "CDN77",
            cnameTokens = listOf("cdn77.org", "rsc.cdn77.org"),
            headerNames = listOf("x-77-nzt", "x-77-age"),
            headerValueTokens = listOf("cdn77")
        ),
        CdnSignature(
            "KeyCDN",
            cnameTokens = listOf("kxcdn.com", "keycdn.com"),
            headerNames = listOf("x-edge-location"),
            headerValueTokens = listOf("keycdn")
        ),
        CdnSignature(
            "CacheFly",
            cnameTokens = listOf("cachefly.net"),
            headerValueTokens = listOf("cachefly")
        ),
        CdnSignature(
            "StackPath",
            cnameTokens = listOf("stackpathcdn.com", "stackpathdns.com"),
            headerNames = listOf("x-sp-url", "x-sp-cache"),
            headerValueTokens = listOf("stackpath")
        ),
        CdnSignature(
            "Vercel Edge",
            cnameTokens = listOf("vercel-dns.com", "cname.vercel-dns.com"),
            headerNames = listOf("x-vercel-id", "x-vercel-cache"),
            headerValueTokens = listOf("vercel")
        ),
        CdnSignature(
            "Netlify Edge",
            cnameTokens = listOf("netlify.app", "netlifyglobalcdn.com"),
            headerNames = listOf("x-nf-request-id"),
            headerValueTokens = listOf("netlify")
        ),
        CdnSignature(
            "Alibaba Cloud CDN",
            cnameTokens = listOf("kunlunaq.com", "kunlun.com", "alicdn.com"),
            headerNames = listOf("eagleid", "x-swift-cachetime", "x-swift-savetime"),
            headerValueTokens = listOf("alibaba", "alicdn")
        ),
        CdnSignature(
            "Tencent Cloud CDN",
            cnameTokens = listOf("dnsv1.com", "cdn.dnsv1.com"),
            headerNames = listOf("x-nws-log-uuid"),
            headerValueTokens = listOf("tencent")
        ),
        CdnSignature(
            "Huawei Cloud CDN",
            cnameTokens = listOf("cdnhwc1.com", "cdnhwc2.com", "cdnhwc3.com"),
            headerNames = listOf("x-hcs-proxy-type"),
            headerValueTokens = listOf("huawei")
        ),
        CdnSignature(
            "QUIC.cloud",
            cnameTokens = listOf("quic.cloud", "quic.cloud.cdn"),
            headerNames = listOf("x-qc-cache", "x-qc-pop"),
            headerValueTokens = listOf("quic.cloud")
        ),
        CdnSignature(
            "section.io",
            cnameTokens = listOf("section.io"),
            headerNames = listOf("section-io-id"),
            headerValueTokens = listOf("section.io")
        ),
        CdnSignature(
            "Edgecast / Edgio",
            cnameTokens = listOf("edgecastcdn.net", "systemcdn.net", "edgio.net"),
            headerNames = listOf("x-ec-custom-error", "x-hw"),
            headerValueTokens = listOf("edgecast", "edgio")
        )
    )
}
