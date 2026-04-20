# SEO Schema Markup

Use this skill to generate, validate, and optimize JSON-LD structured data for rich results (Rich Snippets).

## Goal

Help search engines understand the nature of the content and increase organic CTR through rich results.

## Steps

1. **Detection**:
   - Locate existing `<script type="application/ld+json">` blocks.
   - Validate against Schema.org standards.
2. **Generation**:
   - Use current templates for `Organization`, `WebSite`, `Product`, and `SoftwareApplication`.
   - Ensure mandatory fields are present (e.g., `name`, `author`, `datePublished`).
3. **Validation**:
   - Check for syntax errors and missing required attributes.
   - Verify compatibility with Google's Rich Results Test logic.

## Recommended Types

- **Organization**: For brand identity.
- **WebSite**: For site search box functionality.
- **SoftwareApplication**: For tool/utility projects (like this one).
- **FAQPage**: For informational sections (where applicable).

## Hard Stops

- Do not use deprecated schema types (e.g., `HowTo` on non-gov sites).
- Do not create "hidden" schema that doesn't match the visible content.
- Do not use multiple `WebSite` definitions per page.
