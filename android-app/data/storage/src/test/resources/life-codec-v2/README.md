# Actual CLIF version2 golden fixtures

Synthetic codec metadata only; not authentic game/card/source fixtures. Generated2026-10-14 by actual unchanged production LifePayloadCodec (CODEC_VERSION=2), source base2e93046, before model/encoder version3 edits. Temporary capture test passed122s, one test, no failures/errors/skips; log /tmp/life-codec-v2-capture.log. No header rewriting or future encoder used. Four roots plus five event/archive pairs cover all six roots and all five old request families. Existing version1 snapshot fixture remains separate.

The permanent LifePayloadCodecTest.actualVersionTwoGoldenBytesStillDecode checks each stored header and decoded exact literal model.

| File | Bytes | SHA-256 |
|---|---:|---|
| archive-coin.bin | 6535 | faba65242c4a2a76cb7d7600e10dd9843553e0249b0dca1e318be2b5c15828a6 |
| archive-continue.bin | 6586 | 8e644923c56f9dae690cd084dabca53a9e3c0eb67c3a0e112a410cb8f976262b |
| archive-dice.bin | 6594 | b48e37c402d4bfed83b5fd8159a3271afae48ba09d883df35f8dce915ab657b4 |
| archive-order.bin | 6546 | a626d81d0c5e056765382afa3e3840baf43b6105780199fc48c05dc98000e257 |
| archive-pick.bin | 6565 | c02c036fd55a9afd99069d4d40dc37b7194cc804de4334e6fb2a7dfd4caa2348 |
| checkpoint.bin | 3178 | 31b462a760083db1609efff06de4b58522e45a422c901bec5b86ccfd145a3fda |
| creation.bin | 2295 | 146f0d3af34a0cc7041bbd1871f991782574b6f555a37ac208c42525e547455f |
| event-coin.bin | 199 | d207ac750d15c012e3c7342faa4286ad8b1e525b9c303bf6639bfd562162703c |
| event-continue.bin | 250 | 93e216f92ad2be1c16e7c11c9f1bac1baf3b327a95d7d71bf814366809b8f05a |
| event-dice.bin | 258 | 18d76a56ff3e79c7e651caafb9f6428b7f4de782ca441f1caa735c67f1c8b40c |
| event-order.bin | 210 | a403bef115a51156f96b8ddcd7a45a7cff1ead64dcf346aa6ac1749bf571a14e |
| event-pick.bin | 229 | 1f80fdf38465548230168070c7820c9556fcc0a35024098a66694d281f2c638b |
| profile.bin | 535 | 94b094e7f8767065f94afd6eb7396ad70f47b6104deddc0f5849905ada6cc7c8 |
| snapshot.bin | 3137 | 187cc3658e90ac2c9d720546faa07789e24c6551bf302967679ed091730f9d59 |

Permanent pinned-fixture decode test passed148s under actual unchanged version2 codec before upgrade, one test0failures/errors/skips; log /tmp/life-codec-v2-pinned.log. All14 files total43117bytes.
