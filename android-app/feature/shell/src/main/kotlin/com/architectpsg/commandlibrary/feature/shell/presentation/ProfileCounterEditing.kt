package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*

// Matches the persisted profile definition list bound in LifeCoreWires.profileWire.
internal const val MaxProfileCounterDefinitions = 128
internal val CounterDefinition.editableInProfile: Boolean get() = custom && role == CounterRole.OTHER
internal enum class ProfileCounterLoss { NONE, ABOVE, BELOW }
internal enum class ProfileCounterValidation { LABEL, INITIAL, MINIMUM, MAXIMUM, BOUNDS, THRESHOLD, COLOR }

internal data class ProfileCounterEditor(
    val id: EntityId,
    val original: CounterDefinition? = null,
    val label: String = "",
    val initial: String = "0",
    val minimum: String = "",
    val maximum: String = "",
    val loss: ProfileCounterLoss = ProfileCounterLoss.NONE,
    val threshold: String = "0",
    val useColor: Boolean = false,
    val color: String = DefaultProfileColor,
    val reset: CounterResetPolicy = CounterResetPolicy.MANUAL,
    val validation: ProfileCounterValidation? = null,
)

internal fun CounterDefinition.profileCounterEditor(): ProfileCounterEditor = ProfileCounterEditor(
    id = id, original = this, label = label, initial = initial.toString(),
    minimum = minimum?.toString().orEmpty(), maximum = maximum?.toString().orEmpty(),
    loss = when (lossPolicy) {
        CounterLossPolicy.None -> ProfileCounterLoss.NONE
        is CounterLossPolicy.AtOrAbove -> ProfileCounterLoss.ABOVE
        is CounterLossPolicy.AtOrBelow -> ProfileCounterLoss.BELOW
    },
    threshold = when (val policy = lossPolicy) {
        CounterLossPolicy.None -> "0"
        is CounterLossPolicy.AtOrAbove -> policy.threshold.toString()
        is CounterLossPolicy.AtOrBelow -> policy.threshold.toString()
    },
    useColor = color != null,
    color = color?.profileColorText() ?: DefaultProfileColor,
    reset = resetPolicy,
)

internal sealed interface ProfileCounterEditResult {
    data class Valid(val value: CounterDefinition) : ProfileCounterEditResult
    data class Invalid(val field: ProfileCounterValidation) : ProfileCounterEditResult
}

/** Parses only explicitly edited fields; identity/role/preset/custom metadata stay immutable. */
internal fun ProfileCounterEditor.parse(): ProfileCounterEditResult {
    fun invalid(field: ProfileCounterValidation) = ProfileCounterEditResult.Invalid(field)
    if (label.isBlank() && label != original?.label) return invalid(ProfileCounterValidation.LABEL)
    val start = initial.toIntOrNull() ?: return invalid(ProfileCounterValidation.INITIAL)
    val low = if (minimum.isEmpty()) null else minimum.toIntOrNull() ?: return invalid(ProfileCounterValidation.MINIMUM)
    val high = if (maximum.isEmpty()) null else maximum.toIntOrNull() ?: return invalid(ProfileCounterValidation.MAXIMUM)
    if (low != null && high != null && low > high) return invalid(ProfileCounterValidation.BOUNDS)
    if ((low != null && start < low) || (high != null && start > high)) return invalid(ProfileCounterValidation.INITIAL)
    val policy = when (loss) {
        ProfileCounterLoss.NONE -> CounterLossPolicy.None
        ProfileCounterLoss.ABOVE -> CounterLossPolicy.AtOrAbove(threshold.toIntOrNull() ?: return invalid(ProfileCounterValidation.THRESHOLD))
        ProfileCounterLoss.BELOW -> CounterLossPolicy.AtOrBelow(threshold.toIntOrNull() ?: return invalid(ProfileCounterValidation.THRESHOLD))
    }
    val displayColor = if (!useColor) null else {
        parseProfileColor(color) ?: return invalid(ProfileCounterValidation.COLOR)
    }
    val value = original?.copy(label = label, initial = start, minimum = low, maximum = high,
        lossPolicy = policy, color = displayColor, resetPolicy = reset)
        ?: CounterDefinition(id, label, low, high, start, policy, true, CounterRole.OTHER, displayColor,
            resetPolicy = reset)
    return ProfileCounterEditResult.Valid(value)
}
