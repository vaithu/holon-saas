# Upstream bug report: `holon-core`'s `BeanDatastoreHelper` silently drops rows with empty `@ElementCollection` fields

**Status:** Not yet filed upstream — this document is the ready-to-submit report. File it against
the `holon-core` project (the module owning `com.holonplatform.core.datastore.beans.BeanDatastoreHelper`).

**Affected version:** `holon-core` 12.0.0 (the version this repository currently depends on —
see `holon-core.version` in the root `pom.xml`). Not verified against other versions.

## Summary

`BeanDatastoreHelper` builds a single JPQL/HQL query that references every mapped bean
property — including `@ElementCollection`-mapped `Map`/`Set`/`List` fields — directly in the
projection. Because JPA providers (verified against Hibernate, the provider used throughout this
project) translate a collection-valued property reference in the `SELECT` list into a SQL
**inner join** against the collection's join table, any entity whose `@ElementCollection` field
happens to be **empty** is **silently excluded from the result set entirely** — not an
exception, not a null field, just a missing row, indistinguishable from "this entity doesn't
exist".

This is a correctness bug, not a performance quirk: a freshly-created entity (the single most
common case for any collection-valued field — nothing has been added to it yet) is the exact
shape that triggers data loss.

## Reproduction

1. Define any `@Entity` with an `@ElementCollection`-mapped field, e.g.:
   ```java
   @Entity
   public class MyEntity {
       @Id
       private String id;

       @ElementCollection
       private Map<String, String> attributes = new HashMap<>();
       // ... other fields, getters/setters
   }
   ```
2. Persist an instance with the default (empty) `attributes` map, via a
   `BeanDatastoreHelper`-backed save.
3. Attempt to read it back via the same `BeanDatastoreHelper` (a `findById`-style query that
   projects the whole bean, including `attributes`).
4. **Observed:** the query returns no result, as if the entity were never saved.
   **Expected:** the entity is returned, with `attributes` correctly empty.

## Root cause

`BeanDatastoreHelper`'s bulk-projection query includes the collection-valued property in the
same `SELECT`, which the underlying JPA provider implements as an inner join to the
`@ElementCollection`'s join table. Standard inner-join semantics: a parent row with **zero**
matching child rows (an empty collection) produces **zero** result rows for that parent —
i.e., the parent itself disappears from the result set.

## Where this was found and how it was worked around

This bug was discovered independently, three separate times, while building `holon-saas` (the
project this report lives in) on top of `holon-core` 12.0.0:

- `TenantUser#attributes` (`tenant-users` module)
- `UserInvitation#attributes` (`tenant-invitations` module)
- `WhatsAppMessage#templateParams` (`tenant-whatsapp` module)
- `TenantDetails#features`/`#attributes` (`tenant-core` module) — hit a fourth time, then
  **reverted** to a plain `JpaRepository` (bypassing `BeanDatastoreHelper` entirely for this one
  entity) specifically to keep the more natural `@ElementCollection` mapping; see
  `TenantDetailsElementCollectionRoundTripTest` for the regression test proving a plain
  `JpaRepository`/`EntityManager` does **not** exhibit this bug (Hibernate's default
  `FetchMode.SELECT` for element collections issues a separate, FK-keyed query instead of an
  inline inner join, so it cannot drop the owning row).

The workaround adopted for the first three entities: store the `Map`/`Set` as a single
JSON-serialized `TEXT`/`VARCHAR` column via a JPA `AttributeConverter`
(`StringMapJsonConverter`/`StringSetJsonConverter` in `tenant-core`) instead of a separate
`@ElementCollection` join table — sidesteps the bug entirely since there's no longer a
collection-valued property for `BeanDatastoreHelper` to inner-join on, at the cost of losing the
ability to query inside the collection at the SQL level.

`holon-saas` also ships a build-time validator
(`com.holonplatform.multitenant.testing.HolonEntityMappingValidator`, in `tenant-core`) that
flags any `@ElementCollection` field on an entity intended for `BeanDatastoreHelper`, precisely
because this bug has no compile-time signal and silently returns wrong data rather than
throwing.

## Suggested fix (for `holon-core` maintainers)

Options, roughly in order of preference:

1. **Fetch element-collection properties via a secondary query**, mirroring standard JPA/Hibernate
   default behavior (`FetchMode.SELECT`) instead of inlining them into the primary projection —
   this is the most correct fix and matches what a plain `JpaRepository`/`EntityManager` already
   does safely.
2. **Use a `LEFT JOIN` instead of an (implicit) inner join** when a projected property is
   collection-valued, then reassemble multiple result rows per entity (or de-duplicate) in
   `BeanDatastoreHelper` itself.
3. **At minimum, document the limitation loudly** in `BeanDatastoreHelper`'s Javadoc, so this
   isn't rediscovered independently by every consumer with a collection-valued entity property —
   this repository alone hit it four times before recognizing the pattern.

## How to file this

This document is intentionally self-contained and ready to paste into a new issue on the
`holon-core` repository (or an internal ticket, if `holon-core` is maintained privately). Include
a link back to `tenant-core`'s `StringMapJsonConverter`/`StringSetJsonConverter` and
`TenantDetailsElementCollectionRoundTripTest` in this repository as a working reference for both
the workaround and the regression test proving the root-cause analysis above.
