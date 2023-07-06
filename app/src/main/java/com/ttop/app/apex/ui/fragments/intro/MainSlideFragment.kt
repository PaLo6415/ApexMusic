package com.ttop.app.apex.ui.fragments.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.parseAsHtml
import androidx.fragment.app.Fragment
import com.ttop.app.apex.R
class MainSlideFragment : Fragment() {

    private var desc: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_main_intro, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appName =
            getString(
                R.string.message_welcome,
                "Apex Music"
            ).parseAsHtml()

        desc = view.findViewById(R.id.description) as TextView

        desc?.text = appName

        view.setBackgroundColor(ContextCompat.getColor(requireActivity(), com.ttop.app.appthemehelper.R.color.md_red_400))
    }
    companion object {
        fun newInstance(): MainSlideFragment {
            return MainSlideFragment()
        }
    }
}