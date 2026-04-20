# Technical SEO

Use this skill to audit the technical infrastructure of a web application to ensure search engines can crawl, index, and render content efficiency.

## Goal

Identify and fix technical barriers that prevent optimal ranking and user experience.

## Steps

1. **Performance Audit**:
   - Baseline LCP, INP, and CLS using Lighthouse or DevTools.
   - Check compression (Gzip/Brotli) and image optimization (WebP/AVIF).
2. **Indexability**:
   - Check `robots.txt` and `sitemap.xml`.
   - Verify Canonical tags to prevent duplication.
   - Check for `noindex` tags that might be accidentally blocking pages.
3. **Accessibility & UX**:
   - Verify Mobile-first responsive design.
   - Check Tap Target sizes and Font sizes.
   - Verify Contrast Ratios and Alt Text for images.
4. **Security**:
   - Ensure HTTPS is enforced.
   - Check for secure cookie flags and CSP headers.

## Hard Stops

- Do not ignore layout shifts > 0.1 (CLS).
- Do not allow blocking resources in the `<head>`.
- Do not skip alt text for meaningful images.

## Checklist

- [ ] HTTPS Enforced
- [ ] Robots.txt present and valid
- [ ] Sitemap.xml submitted and error-free
- [ ] Mobile-Friendly (no horizontal scroll)
- [ ] 0 Console Errors
- [ ] Fast TTFB (< 200ms)
