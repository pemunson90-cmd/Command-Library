package com.architectpsg.commandlibrary.feature.rules

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun rulesSql(statement: String, maxRows: Int = 1, vararg values: Pair<String, Any?>): SqlRequest = SqlRequest(statement,
    SnapshotList.copyOf(values.map { (name, value) -> SqlParameter(name, when (value) { null -> SqlValue.Null; is Long -> SqlValue.Integer(value); is Int -> SqlValue.Integer(value.toLong()); else -> SqlValue.Text(value.toString()) }) }), maxRows)
internal fun RowData.cell(name: String): SqlValue = columns.single { it.name == name }.value
internal fun RowData.text(name: String): String = (cell(name) as SqlValue.Text).value
internal fun RowData.number(name: String): Long = (cell(name) as SqlValue.Integer).value
internal fun RowData.optionalText(name: String): String? = if (cell(name) == SqlValue.Null) null else text(name)
internal fun RowData.date(name: String): UtcTimestamp? = if (cell("${name}_epoch_seconds") == SqlValue.Null) null else UtcTimestamp(number("${name}_epoch_seconds"), number("${name}_nano").toInt())
internal fun <T> Knowledge<T>.known(): T? = (this as? Knowledge.Known)?.value
internal class RulesFailure(val outcome: Outcome<Nothing>) : RuntimeException()
internal fun rulesFailure(code: FailureCode, message: String): Outcome.Failure = Outcome.Failure(DomainFailure(code, message, SnapshotList.empty()))
internal fun <T> Outcome<T>.requireSuccess(): T = when (this) { is Outcome.Success -> value; is Outcome.Failure -> throw RulesFailure(this); is Outcome.Unavailable -> throw RulesFailure(this); is Outcome.Unknown -> throw RulesFailure(this) }
internal suspend fun CancelToken.check(): Unit { currentCoroutineContext().ensureActive(); throwIfCancelled() }
internal suspend fun DbTransaction.write(statement: String, cancel: CancelToken, vararg values: Pair<String, Any?>): Unit {
    when (val result = execute(rulesSql(statement, 1, *values), cancel)) {
        is Outcome.Success -> Unit; is Outcome.Failure -> abort(result.error); else -> abort(DomainFailure(FailureCode.PERSISTENCE, "Rules write unavailable", SnapshotList.empty()))
    }
}
