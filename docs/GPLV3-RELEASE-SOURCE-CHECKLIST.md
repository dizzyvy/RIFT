# GPLv3 release-source checklist

Complete this checklist separately for every distributed release. This is a
review aid, not a certification of compliance. Its existence does not prove
that a particular binary has corresponding source available or that every
applicable GPLv3 obligation has been satisfied.

## Release identification

- [ ] Record the release identifier and distribution date.
- [ ] Identify the exact APK or other binary being distributed.
- [ ] Record a cryptographic hash of each distributed binary.
- [ ] Record the exact source revision corresponding to each binary.

## Source and licensing review

- [ ] Identify RIFT-authored source included in the release and verify the
      provenance and licensing basis for the applicable files.
- [ ] Identify third-party source components, modifications, and retained
      copyright, license, and attribution notices.
- [ ] Review Seal-derived downloader provenance and applicable GPLv3 terms;
      do not assume a module boundary determines whether the app is an
      aggregate or a combined work.
- [ ] Confirm the applicable corresponding source for the distributed
      binary is available in the required form.
- [ ] Include or make available relevant build scripts, configuration, and
      other materials that form part of the applicable corresponding source.
- [ ] Include required license texts, attribution, and third-party notices.
- [ ] Record the source-conveyance method and the precise instructions or
      location recipients can use to obtain the corresponding source.

## Distribution-specific GPLv3 review

- [ ] Review the relevant GPLv3 requirements, including Section 6, against
      the actual binary and source distribution method.
- [ ] Assess whether installation information is required for this specific
      distribution model and document the basis for the assessment.
- [ ] Resolve or explicitly document open questions about dependencies,
      native artifacts, bundled payloads, provenance, source correspondence,
      build materials, and distribution obligations.
- [ ] Confirm that the notices and source offer/access details correspond
      to this exact release and remain available as required.

## Approval

- [ ] Record the reviewer(s), review date, and release-specific decision.
- [ ] Obtain final approval before distributing the release.
