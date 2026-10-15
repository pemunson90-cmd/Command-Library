package com.architectpsg.commandlibrary.feature.search

import com.architectpsg.commandlibrary.core.common.DecimalComparisonKeys
import com.architectpsg.commandlibrary.core.model.*

/** All aliases except the reader's c are local to scalar subqueries. No AST value enters SQL. */
internal class CatalogQuerySql(private val cancel: CancelToken) {
    val parameters: MutableList<SqlParameter> = ArrayList()
    private val bindings = LinkedHashMap<SqlValue, String>()

    private fun bind(value: SqlValue): String = bindings.getOrPut(value) {
        cancel.throwIfCancelled()
        require(parameters.size < 512) { "Query exceeds parameter budget" }
        val name = "qp${parameters.size}"
        parameters.add(SqlParameter(name, value)); ":$name"
    }
    private fun text(value: String): String = bind(SqlValue.Text(value))
    private fun integer(value: Int): String = bind(SqlValue.Integer(value.toLong()))
    private fun sameCard(alias: String): String =
        "$alias.generation_id=c.generation_id AND $alias.projection_version=c.projection_version AND " +
            "$alias.provider=c.provider AND $alias.oracle_id=c.oracle_id AND $alias.language=c.language"

    /** OR over original direct/member relations deduplicates by the original printing PK. */
    private fun printingDomain(fact: String): String =
        "printing pr LEFT JOIN catalog_search_printing_fact $fact ON " +
            "$fact.generation_id=pr.generation_id AND $fact.projection_version=c.projection_version AND " +
            "$fact.provider=pr.provider AND $fact.printing_id=pr.printing_id WHERE pr.generation_id=c.generation_id AND " +
            "((pr.oracle_provider=c.provider AND pr.oracle_id=c.oracle_id AND pr.oracle_language=c.language) OR " +
            "EXISTS (SELECT 1 FROM printing_oracle_membership pm WHERE pm.generation_id=pr.generation_id AND " +
            "pm.printing_provider=pr.provider AND pm.printing_id=pr.printing_id AND " +
            "pm.oracle_provider=c.provider AND pm.oracle_id=c.oracle_id AND pm.oracle_language=c.language))"

    /** TRUE wins; absent TRUE, any UNKNOWN wins; otherwise FALSE. Empty domain is UNKNOWN. */
    private fun reduce(expression: String, domain: String): String =
        "(SELECT CASE MAX(CASE ($expression) WHEN 1 THEN 2 WHEN 0 THEN 0 ELSE 1 END) " +
            "WHEN 2 THEN 1 WHEN 0 THEN 0 ELSE NULL END FROM $domain)"

    fun compile(query: QueryAst, policy: FaceMatchPolicy): String {
        if (!hasPrinting(query)) return faceExpression(query, policy, null)
        // With no printings, evaluate against missing evidence: a true card-only OR still wins.
        val present = faceExpression(query, policy, "p")
        val absent = faceExpression(query, policy, null)
        return "(CASE WHEN EXISTS (SELECT 1 FROM ${printingDomain("pe")}) THEN " +
            "${reduce(present, printingDomain("p"))} ELSE $absent END)"
    }

    private fun hasPrinting(query: QueryAst): Boolean = when (query) {
        is QueryAst.PrintingText -> true
        is QueryAst.And -> query.terms.any(::hasPrinting)
        is QueryAst.Or -> query.terms.any(::hasPrinting)
        is QueryAst.Not -> hasPrinting(query.term)
        else -> false
    }

    private fun faceExpression(query: QueryAst, policy: FaceMatchPolicy, printing: String?): String =
        if (policy == FaceMatchPolicy.SAME_FACE) {
            val perFace = expression(query, policy, "f", printing)
            val canonical = expression(query, FaceMatchPolicy.CANONICAL, null, printing)
            "(CASE WHEN c.face_count>0 THEN ${reduce(perFace, "catalog_search_face_fact f WHERE ${sameCard("f")}")} ELSE $canonical END)"
        } else expression(query, policy, null, printing)

    private fun expression(query: QueryAst, policy: FaceMatchPolicy, face: String?, printing: String?): String {
        cancel.throwIfCancelled()
        return when (query) {
            is QueryAst.And -> query.terms.joinToString(" AND ", "(", ")") { expression(it, policy, face, printing) }
            is QueryAst.Or -> query.terms.joinToString(" OR ", "(", ")") { expression(it, policy, face, printing) }
            is QueryAst.Not -> "(NOT (${expression(query.term, policy, face, printing)}))"
            is QueryAst.Numeric -> {
                val key = text(DecimalComparisonKeys.queryKey(query.value))
                val column = query.field.name.lowercase(java.util.Locale.ROOT) + "_key"
                val op = when (query.comparison) {
                    Comparison.EQ -> "="; Comparison.NE -> "<>"; Comparison.LT -> "<"
                    Comparison.LE -> "<="; Comparison.GT -> ">"; Comparison.GE -> ">="
                }
                if (query.field == SearchNumericField.MANA_VALUE) "(c.$column COLLATE BINARY $op $key)"
                else faceLeaf(policy, face) { alias -> "($alias.$column COLLATE BINARY $op $key)" }
            }
            is QueryAst.Colors -> {
                val mask = integer(query.values.fold(0) { sum, color -> sum or (1 shl color.ordinal) })
                if (query.identity) colors("c.identity_mask", mask, query.comparison)
                else faceLeaf(policy, face) { alias -> colors("$alias.colors_mask", mask, query.comparison) }
            }
            is QueryAst.Text -> when (query.field) {
                SearchTextField.LAYOUT -> literal("lower(c.layout)", text(query.value), query.exact)
                SearchTextField.KEYWORD -> "(CASE WHEN c.keywords_known=1 THEN " +
                    "EXISTS (SELECT 1 FROM catalog_search_text_fact t WHERE ${sameCard("t")} AND " +
                    "t.scope_kind='KEYWORD' AND t.field='KEYWORD' AND " +
                    "${literal("t.normalized_value", text(query.value), query.exact)}) ELSE NULL END)"
                else -> faceLeaf(policy, face) { alias ->
                    val canonical = textLeaf(query, null)
                    if (alias == "c") canonical
                    else if (query.field == SearchTextField.NAME) "($canonical OR ${textLeaf(query, alias)})"
                    else textLeaf(query, alias)
                }
            }
            is QueryAst.PrintingText -> if (printing == null) "NULL" else when (query.field) {
                PrintingSearchField.SET -> literal("lower($printing.set_code)", text(query.value), true)
                PrintingSearchField.LANGUAGE -> literal("lower($printing.language)", text(query.value), true)
                PrintingSearchField.RARITY -> literal("lower($printing.rarity)", text(query.value), true)
                PrintingSearchField.FINISH -> {
                    val bit = integer(when (query.value) { "nonfoil" -> 1; "foil" -> 2; "etched" -> 4; else -> error("Unvalidated finish") })
                    "(($printing.finish_mask & $bit)<>0)"
                }
            }
            is QueryAst.Flag -> if (query.field == SearchFlag.RESERVED) "(c.reserved=${integer(if (query.value) 1 else 0)})"
                else "(${consensus("pc.gamechanger")}=${integer(if (query.value) 1 else 0)})"
            is QueryAst.LegalIn -> {
                val source = text(query.format.source.value)
                val raw = text(query.format.raw)
                val status = text(query.status.name)
                val evidence = "(SELECT CASE WHEN l.status IN ('LEGAL','BANNED','RESTRICTED','NOT_LEGAL') " +
                    "THEN l.status ELSE NULL END FROM catalog_search_legality_fact l WHERE " +
                    "l.generation_id=pc.generation_id AND l.projection_version=pc.projection_version AND " +
                    "l.provider=pc.provider AND l.provider=$source AND l.printing_id=pc.printing_id AND l.format=$raw)"
                "(CASE WHEN c.provider=$source THEN ${consensus(evidence)}=$status ELSE NULL END)"
            }
            is QueryAst.Owned, is QueryAst.Price, QueryAst.InDeck -> error("Contextual query passed validation")
        }
    }

    private fun consensus(value: String): String =
        "(SELECT CASE WHEN COUNT(*)>0 AND COUNT(v)=COUNT(*) AND MIN(v)=MAX(v) THEN MIN(v) ELSE NULL END " +
            "FROM (SELECT $value AS v FROM ${printingDomain("pc")}))"

    private fun faceLeaf(policy: FaceMatchPolicy, face: String?, leaf: (String) -> String): String = when (policy) {
        FaceMatchPolicy.CANONICAL -> leaf("c")
        FaceMatchPolicy.SAME_FACE -> leaf(requireNotNull(face))
        FaceMatchPolicy.ANY_FACE -> "(CASE WHEN c.face_count>0 THEN " +
            "${reduce(leaf("fl"), "catalog_search_face_fact fl WHERE ${sameCard("fl")}")} ELSE ${leaf("c")} END)"
    }

    private fun literal(column: String, parameter: String, exact: Boolean): String =
        if (exact) "($column COLLATE BINARY=$parameter)" else "(instr($column,$parameter)>0)"

    private fun textLeaf(query: QueryAst.Text, face: String?): String {
        val scope = if (face == null) "t.scope_kind='CANONICAL' AND t.scope_index=0"
            else "t.scope_kind='FACE' AND t.scope_index=$face.face_index"
        // A known empty string is a real row; a missing row returns NULL even under NOT.
        return "(SELECT ${literal("t.normalized_value", text(query.value), query.exact)} " +
            "FROM catalog_search_text_fact t WHERE ${sameCard("t")} AND $scope AND t.field='${query.field.name}')"
    }

    private fun colors(column: String, mask: String, op: Comparison): String = when (op) {
        Comparison.EQ -> "($column=$mask)"
        Comparison.NE -> "($column<>$mask)"
        Comparison.LT -> "(($column & $mask)=$column AND $column<>$mask)"
        Comparison.LE -> "(($column & $mask)=$column)"
        Comparison.GT -> "(($column & $mask)=$mask AND $column<>$mask)"
        Comparison.GE -> "(($column & $mask)=$mask)"
    }
}
