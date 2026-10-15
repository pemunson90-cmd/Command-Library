# Independent CLDK1 minimal fixture

Protocol design r1; manually framed using Python struct, no production encoder exists at creation. Synthetic app-owned values, no publisher data.

DeckSnapshot: id(local,golden-deck), name Golden, format Absent(none), description empty, folder Absent(none), tags empty; createdAt/modifiedAt UtcTimestamp(1,0); revision0; entries empty; budget Absent(none); profileId null; catalog/packageInstances empty; primer empty; categoryOrder null (legacy, not explicit empty). Hex includes CLDK + unsigned16version1 and exactly those fields in declaration order. Decoder must produce this exact value and encoder these exact bytes. Explicit-empty category registry differs by final presence byte1 + unsigned32zero count.

`minimal-v1.hex` is a fixed independently specified golden. Do not replace it with actual encoder output to make a test green; investigate any protocol disagreement and return it to root. This is not an authentic vendor interchange fixture or persistence acceptance.
