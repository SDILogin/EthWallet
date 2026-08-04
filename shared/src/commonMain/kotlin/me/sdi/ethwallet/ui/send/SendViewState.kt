package me.sdi.ethwallet.ui.send

import kotlinx.serialization.Serializable
import me.sdi.ethwallet.core.EthAddress
import me.sdi.ethwallet.core.EthContractAddress
import me.sdi.ethwallet.core.transfer.Wei
import me.sdi.ethwallet.resources.Res
import me.sdi.ethwallet.resources.send_fee_custom
import me.sdi.ethwallet.resources.send_fee_default
import me.sdi.ethwallet.resources.send_fee_high
import me.sdi.ethwallet.resources.send_fee_low
import org.jetbrains.compose.resources.StringResource
import java.math.BigInteger

data class SendViewState(
    val selectedTokenContractAddress: EthAddress = EthContractAddress,
    val receiverAddress: TextInputState = TextInputState(),
    val transferAmountInputState: TextInputState = TextInputState(),
    val transferAmountUsd: String? = null,
    val gas: GasViewState = GasViewState(),
    val transferring: Boolean = false,
) {
    val isTransferEnabled: Boolean =
        !transferring &&
            receiverAddress.isValid &&
            transferAmountInputState.isValid &&
            gas.maxFee != null
}

/**
 * A selectable gas tier. [feeUsd] is the worst-case cost estimate, null while the fee
 * suggestion or the ETH/USD rate is unavailable.
 */
data class GasChip(
    val option: GasOption,
    val labelRes: StringResource,
    val feeUsd: String?,
    val selected: Boolean,
    val enabled: Boolean,
)

/**
 * Gas section state. [maxFee], [priorityFeeWei] and [gasUnitsValue] are resolved for the
 * selected option (preset math or custom inputs) and consumed directly when building the
 * transfer; they are null when the selected option cannot yet produce a valid fee.
 */
data class GasViewState(
    val chips: List<GasChip> = DEFAULT_GAS_CHIPS,
    val baseFee: TextInputState = TextInputState(),
    val priorityFee: TextInputState = TextInputState(),
    val gasUnits: TextInputState = TextInputState(),
    val gasUnitsEditable: Boolean = false,
    val customFeeUsd: String? = null,
    val maxFee: Wei? = null,
    val priorityFeeWei: Wei? = null,
    val gasUnitsValue: BigInteger? = null,
)

@Serializable
data class UserInput(
    val token: String = EthContractAddress.toString(),
    val amount: String = "",
    val receiverAddress: String = "",
    val selectedGasOption: GasOption = GasOption.Default,
    val customFees: CustomFeesInput = CustomFeesInput(),
)

@Serializable
enum class GasOption { LowFees, Default, FastTransfer, CustomFees }

@Serializable
data class CustomFeesInput(
    val baseFeeGwei: String = "",
    val priorityFeeGwei: String = "",
    val gasUnits: String = "",
)

data class TextInputState(
    val value: String = "",
    val error: String? = null,
) {
    val isValid: Boolean = error == null && value.isNotBlank()
}

internal fun GasOption.labelRes(): StringResource = when (this) {
    GasOption.LowFees -> Res.string.send_fee_low
    GasOption.Default -> Res.string.send_fee_default
    GasOption.FastTransfer -> Res.string.send_fee_high
    GasOption.CustomFees -> Res.string.send_fee_custom
}

private val DEFAULT_GAS_CHIPS: List<GasChip> = GasOption.entries.map { option ->
    GasChip(
        option = option,
        labelRes = option.labelRes(),
        feeUsd = null,
        selected = option == GasOption.Default,
        enabled = option == GasOption.CustomFees,
    )
}
