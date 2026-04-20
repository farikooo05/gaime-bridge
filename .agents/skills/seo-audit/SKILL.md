# SEO Audit (Orchestrator)

Use this skill to perform a comprehensive SEO evaluation of a page or an entire site. This skill orchestrates sub-skills like `seo-technical`, `seo-schema`, and `seo-content`.

## Goal

Provide a prioritized list of SEO improvements to increase organic visibility, click-through rates, and user experience.

## Steps

1. **Discovery**:
   - Access the target URL using `read_url_content` or `browser_subagent`.
   - If analyzing local code, read relevant HTML/JS/CSS files.
2. **Phase 1: Technical Audit**:
   - Call the `seo-technical` skill.
   - Check performance (Core Web Vitals), indexability, and mobile-friendliness.
3. **Phase 2: On-Page & Schema**:
   - Call the `seo-schema` skill.
   - Verify metadata (Title, Description, Headings) and JSON-LD markup.
4. **Phase 3: Content & Quality**:
   - Call the `seo-content` skill.
   - Evaluate E-E-A-T signals and keyword optimization.
5. **Synthesis**:
   - Create a summary report with "Critical", "Warning", and "Opportunity" sections.

## Hard Stops

- Do not ignore 404 or 500 errors in the crawl.
- Do not recommend "black hat" SEO techniques (keyword stuffing, cloaking).
- Do not make changes to source code without a separate implementation plan.

## Report Template

### 🚀 Performance & Core Web Vitals
- [ ] LCP:
- [ ] INP:
- [ ] CLS:

### 🛠️ Technical SEO
- [ ] Robots.txt / Sitemap:
- [ ] Meta Tags:
- [ ] Heading Hierarchy:

### 💎 Schema & JSON-LD
- [ ] Detected Types:
- [ ] Validation Issues:

### 📝 Content Quality (E-E-A-T)
- [ ] Experience/Expertise signals:
- [ ] Trustworthiness/Security:
