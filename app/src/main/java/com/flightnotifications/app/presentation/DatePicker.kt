package com.flightnotifications.app.presentation

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.view.View
import java.time.LocalDate
import java.time.ZoneId

private val colombiaDatePickerZone: ZoneId = ZoneId.of("America/Bogota")

/** Opens the native calendar and accepts the date as soon as the user selects it. */
internal fun openDatePicker(
    context: Context,
    date: LocalDate,
    minimum: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val start = if (date.isBefore(minimum)) minimum else date
    val dialog = DatePickerDialog(
        context,
        null,
        start.year,
        start.monthValue - 1,
        start.dayOfMonth,
    )
    dialog.datePicker.minDate = minimum.atStartOfDay(colombiaDatePickerZone).toInstant().toEpochMilli()
    dialog.setOnShowListener {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.visibility = View.GONE

        var acceptSelection = false
        dialog.datePicker.init(start.year, start.monthValue - 1, start.dayOfMonth) { _, year, month, day ->
            if (acceptSelection) {
                acceptSelection = false
                dialog.dismiss()
                onSelect(LocalDate.of(year, month + 1, day))
            }
        }
        acceptSelection = true
    }
    dialog.show()
}
