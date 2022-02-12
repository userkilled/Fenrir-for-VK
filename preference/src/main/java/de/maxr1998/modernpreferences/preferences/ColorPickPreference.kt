package de.maxr1998.modernpreferences.preferences

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import de.maxr1998.modernpreferences.ExtraPref
import de.maxr1998.modernpreferences.PreferencesAdapter
import de.maxr1998.modernpreferences.R
import de.maxr1998.modernpreferences.helpers.DEFAULT_RES_ID
import de.maxr1998.modernpreferences.preferences.colorpicker.ColorCircleDrawable
import de.maxr1998.modernpreferences.preferences.colorpicker.ColorPickerView.WHEEL_TYPE
import de.maxr1998.modernpreferences.preferences.colorpicker.builder.ColorPickerDialogBuilder

class ColorPickPreference(key: String, fragmentManager: FragmentManager) :
    DialogPreference(key, fragmentManager) {
    override fun getWidgetLayoutResource() = R.layout.color_widget

    var alphaSlider = false
    var lightSlider = false
    var border = false

    @ColorInt
    var selectedColor = 0
        private set

    var wheelType: WHEEL_TYPE = WHEEL_TYPE.FLOWER
    var density = 0

    @ColorInt
    var defaultValue: Int = Color.WHITE
    var colorChangeListener: OnColorChangeListener? = null

    var colorIndicator: ImageView? = null

    fun copyColorPick(other: ColorPickPreference): ColorPickPreference {
        alphaSlider = other.alphaSlider
        lightSlider = other.lightSlider
        border = other.border
        wheelType = other.wheelType
        density = other.density
        return this
    }

    override fun onAttach() {
        super.onAttach()
        selectedColor = getInt(defaultValue)
    }

    override fun bindViews(holder: PreferencesAdapter.ViewHolder) {
        super.bindViews(holder)
        colorIndicator = (holder.widget as ImageView)
        val tmpColor =
            if (enabled) selectedColor else darken(selectedColor, .5f)

        val colorChoiceDrawable = ColorCircleDrawable(tmpColor)
        colorIndicator?.setImageDrawable(colorChoiceDrawable)
    }

    private fun darken(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.argb(
            a,
            (r * factor).toInt().coerceAtLeast(0),
            (g * factor).toInt().coerceAtLeast(0),
            (b * factor).toInt().coerceAtLeast(0)
        )
    }

    fun persist(@ColorInt input: Int) {
        if (colorChangeListener?.onColorChange(this, input) != false) {
            selectedColor = input
            commitInt(input)
            requestRebind()
        }
    }

    override fun createAndShowDialogFragment() {
        ColorPickDialog.newInstance(
            title,
            titleRes,
            alphaSlider,
            lightSlider,
            border,
            selectedColor,
            WHEEL_TYPE.toInt(wheelType),
            density,
            key,
            parent?.key
        ).show(fragmentManager, "ColorPickDialog")
    }

    class ColorPickDialog : DialogFragment() {
        companion object {
            fun newInstance(
                title: CharSequence?,
                @StringRes titleRes: Int,
                alphaSlider: Boolean,
                lightSlider: Boolean,
                border: Boolean,
                @ColorInt selectedColor: Int,
                wheelType: Int,
                density: Int,
                key: String,
                screenKey: String?
            ): ColorPickDialog {
                val args = Bundle()
                args.putInt(ExtraPref.TITLE_RES, titleRes)
                args.putCharSequence(ExtraPref.TITLE, title)

                args.putBoolean(ExtraPref.COLOR_ALPHA_SLIDER, alphaSlider)
                args.putBoolean(ExtraPref.COLOR_LIGHT_SLIDER, lightSlider)
                args.putBoolean(ExtraPref.COLOR_BORDER, border)
                args.putInt(ExtraPref.COLOR_WHEEL_TYPE, wheelType)
                args.putInt(ExtraPref.COLOR_DENSITY, density)
                args.putInt(ExtraPref.DEFAULT_VALUE, selectedColor)

                args.putString(ExtraPref.PREFERENCE_KEY, key)
                args.putString(ExtraPref.PREFERENCE_SCREEN_KEY, screenKey)
                val dialog = ColorPickDialog()
                dialog.arguments = args
                return dialog
            }
        }

        private var title: CharSequence? = null

        @StringRes
        private var titleRes: Int = DEFAULT_RES_ID

        private var alphaSlider = false
        private var lightSlider = false
        private var border = false

        @ColorInt
        private var selectedColor = 0

        private var wheelType: WHEEL_TYPE = WHEEL_TYPE.FLOWER
        private var density = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            titleRes = requireArguments().getInt(ExtraPref.TITLE_RES)
            title = requireArguments().getCharSequence(ExtraPref.TITLE)
            alphaSlider = requireArguments().getBoolean(ExtraPref.COLOR_ALPHA_SLIDER)
            lightSlider = requireArguments().getBoolean(ExtraPref.COLOR_LIGHT_SLIDER)
            border = requireArguments().getBoolean(ExtraPref.COLOR_BORDER)
            wheelType = WHEEL_TYPE.indexOf(requireArguments().getInt(ExtraPref.COLOR_WHEEL_TYPE))
            density = requireArguments().getInt(ExtraPref.COLOR_DENSITY)
            selectedColor = requireArguments().getInt(ExtraPref.DEFAULT_VALUE)
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val builder = ColorPickerDialogBuilder
                .with(context)
                .initialColor(selectedColor)
                .showBorder(border)
                .wheelType(wheelType)
                .density(density)
                .showColorEdit(true)
                .setPositiveButton(
                    android.R.string.ok
                ) { _: DialogInterface?, selectedColorFromPicker: Int, _: Array<Int?>? ->
                    val intent = Bundle()
                    intent.putInt(ExtraPref.RESULT_VALUE, selectedColorFromPicker)
                    intent.putString(
                        ExtraPref.PREFERENCE_KEY,
                        requireArguments().getString(ExtraPref.PREFERENCE_KEY)
                    )
                    intent.putString(
                        ExtraPref.PREFERENCE_SCREEN_KEY,
                        requireArguments().getString(ExtraPref.PREFERENCE_SCREEN_KEY)
                    )
                    parentFragmentManager.setFragmentResult(
                        ExtraPref.COLOR_DIALOG_REQUEST,
                        intent
                    )
                    dismiss()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    dismiss()
                }
            if (titleRes != DEFAULT_RES_ID) builder.setTitle(titleRes) else builder.setTitle(title?.toString())

            if (!alphaSlider && !lightSlider) builder.noSliders() else if (!alphaSlider) builder.lightnessSliderOnly() else if (!lightSlider) builder.alphaSliderOnly()

            return builder.build()
        }
    }

    fun interface OnColorChangeListener {
        fun onColorChange(preference: ColorPickPreference, @ColorInt color: Int): Boolean
    }
}