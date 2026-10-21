# Enhancement ROM verification

Catalog checked on 2026-10-21 against the primary
[pret/pokeheartgold README](https://github.com/pret/pokeheartgold/blob/master/README.md).
Only checksum metadata was consulted. No ROM was downloaded or opened.

| Image | Header / revision | Whole-file SHA-1 | Recognition | New gameplay modifiers |
| --- | --- | --- | --- | --- |
| HeartGold US | IPKE / 0 | `4fcded0e2713dc03929845de631d0932ea2b5a37` | Known identity | None approved |
| SoulSilver US | IPGE / 0 | `f8dc38ea20c17541a43b58c5e6d18c1732c7e582` | Known identity | None approved |
| Modified/trimmed/translated copies with those headers | Any | Other hash | Unknown revision | Disabled |
| Other games, including Platinum and Gen 5 | Any | Any | Unsupported by this enhancement catalog | Disabled |
| Read failure or incomplete stream | Any | None | Unverified | Disabled |

The whole selected ROM must match; a familiar title, game code, revision byte,
filename or user-selected companion base game cannot bypass this requirement.
SHA-256 keys settings and must separately pin every future production patch plan.
Recognition is separate from ordinary emulation and existing read-only companion
coverage. This matrix makes no new companion, native-hook or real-device claim.

Before adding a production adapter: independently establish its loaded-ROM identity,
document its exact signatures and safe point, verify code/memory ownership and
rollback, test reset/resume/load and interactions with existing options, then record
legal-asset and hardware evidence. Missing progression metadata must disable only
the dependent feature, never fabricate badge/story state. Do not fill the registry
with placeholder addresses or approve a patch from header identity alone.
