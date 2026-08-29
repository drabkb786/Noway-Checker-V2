# NOWAY CDN Finder R5

Native Android CDN identification utility built for fast single and bulk checks.

## Features
- Single or bulk domain/URL scanning (up to 1,500 unique targets per run)
- CNAME chain lookup through DNS-over-HTTPS with Google and Cloudflare fallback
- A/AAAA IP resolution
- HTTPS probe with HTTP fallback
- CDN fingerprint scoring from CNAMEs, redirects, provider-specific response headers, header values and server tokens
- Built-in fingerprints for Cloudflare, CloudFront, Akamai, Fastly, Bunny CDN, Azure Front Door/CDN, Google Cloud CDN, Imperva, Sucuri, Gcore, CDN77, KeyCDN, CacheFly, StackPath, Vercel, Netlify, Alibaba, Tencent, Huawei, QUIC.cloud, section.io and Edgecast/Edgio
- Parallel worker control: 3 / 6 / 10
- Stop scan, live progress and session statistics
- Filter by Detected / Unknown / Error plus provider/domain search
- Tap result for CNAME, IP, HTTP and detection evidence
- Import targets from TXT
- Copy visible results
- Export TXT in four modes: Full results, Detected only, Detected hosts only, Group by CDN
- No analytics or tracking SDK

Detection is evidence-based but no CDN fingerprinting system can guarantee 100% identification because providers and configurations change. Use on systems/domains you are authorized to test.
