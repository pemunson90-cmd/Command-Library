package com.architectpsg.commandlibrary.core.model

/** Complete local-only capability; no online/suggestion placeholders or implicit preparation. */
public interface LocalCardQuery {
    public suspend fun local(query: QueryAst, context: SearchContext, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>>
}

/** Trusted application compiler output, never source/user-provided SQL or a serialized input format.
 * Predicate references only outer schema9 card-fact alias `c`; all other scopes are self-contained.
 * Parameters use qp0..qp511. The reader owns generation/source/READY/cursor selection and the
 * original-card decoder. Only TRUE matches; UNKNOWN must survive inside boolean expressions.
 */
public data class CompiledCatalogQuery(
    public val predicateSql: String,
    public val parameters: SnapshotList<SqlParameter>,
    public val fingerprint: ContentHash,
    public val facePolicy: FaceMatchPolicy,
    public val compilerVersion: Int = 1,
    public val projectionVersion: Int = 1,
)

/** Pure bounded compilation; catalog consumes this injected port without depending on search.
 * Validates the entire AST even when the parser was bypassed. Owned/InDeck/Price anywhere return
 * Unavailable(UNSUPPORTED) until real revision-pinned contextual adapters exist. No IO or IDs.
 * Native cancellation escapes; invalid/budget input is Failure(INVALID_INPUT), never a partial plan.
 */
public interface CatalogQueryCompiler {
    public fun compile(query: QueryAst, facePolicy: FaceMatchPolicy, cancel: CancelToken): Outcome<CompiledCatalogQuery>
}
